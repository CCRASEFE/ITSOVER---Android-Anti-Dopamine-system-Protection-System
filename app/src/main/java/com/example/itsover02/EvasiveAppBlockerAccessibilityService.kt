package com.example.itsover02

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.example.itsover02.telegram.AccessController
import com.example.itsover02.telegram.PackageSuspender
import com.example.itsover02.telegram.SessionAction
import com.example.itsover02.telegram.TelegramDataStore
import com.example.itsover02.telegram.TelegramPreferences

class EvasiveAppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CentinelaAccessibility"

        // Instaladores y tiendas supervisadas
        private val STORE_PACKAGES = setOf(
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.android.packageinstaller"
        )

        // Paquetes conocidos de suplantación GPS
        private val FAKE_GPS_PACKAGES = setOf(
            "com.lexa.fakegps",
            "com.rosteam.gpsemulator",
            "com.incorporateapps.fakegps.fre",
            "com.fakegps.mock",
            "com.locationchanger",
            "ru.gavrikov.mocklocations"
        )

        // Palabras de intento de evasión por Fake GPS
        private val LOCATION_EVASION_KEYWORDS = listOf(
            "fake gps", "mock location", "ubicación simulada", "simular ubicación",
            "falsa ubicación", "cambiar ubicación", "gps spoof"
        )

        // ZONAS SEGURAS EN AJUSTES: Pantallas de desarrollo que NUNCA deben expulsarte
        private val SAFE_SETTINGS_CLASSES = listOf(
            "deviceinfo",         // Acerca del teléfono / Información del dispositivo
            "development",        // Opciones de desarrollador
            "usbdetails",         // Ajustes de conexión USB
            "applicationsettings" // Lista de aplicaciones
        )
    }

    private var adminComponent: ComponentName? = null
    private lateinit var telegramDataStore: TelegramDataStore
    private lateinit var accessController: AccessController

    private var isTelegramInForeground = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundCheckRunnable: Runnable? = null
    private var lastScanTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        adminComponent = DeviceAdminReceiver.getComponentName(this)
        telegramDataStore = TelegramDataStore(this)
        accessController = AccessController(this, telegramDataStore)
        Log.i(TAG, "Centinela de Accesibilidad conectado con protección de menú de desarrollo.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val currentPackage = event.packageName?.toString() ?: return
        val currentClass = event.className?.toString()?.lowercase() ?: ""

        // -----------------------------------------------------------------
        // 1. CONTROL DE TELEGRAM (Solo eventos de cambio de ventana)
        // -----------------------------------------------------------------
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleTelegramEvent(currentPackage)
        }

        // -----------------------------------------------------------------
        // 2. CAPA GEOCERCA: Bloquear Brave, YouTube y Lens si está en zona
        // -----------------------------------------------------------------
        if (BlocklistRepository.isGeofenceRestricted && AppVetoManager.GEOFENCE_APPS.contains(currentPackage)) {
            Log.w(TAG, "App restringida por geocerca detectada ($currentPackage). Expulsando...")
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // -----------------------------------------------------------------
        // 3. CAPA OPERA / MOCK GPS: Expulsión inmediata
        // -----------------------------------------------------------------
        val pkgLower = currentPackage.lowercase()
        if (FAKE_GPS_PACKAGES.contains(currentPackage) || AppVetoManager.BLOCKED_KEYWORDS.any { pkgLower.contains(it) }) {
            Log.w(TAG, "App prohibida detectada ($currentPackage). Expulsando...")
            performGlobalAction(GLOBAL_ACTION_HOME)
            adminComponent?.let { AppVetoManager.vetoPackage(this, it, currentPackage) }
            return
        }

        // -----------------------------------------------------------------
        // 4. CAPA AJUSTES DEL SISTEMA: INMUNIDAD PARA "ACERCA DEL TELÉFONO"
        // -----------------------------------------------------------------
        if (currentPackage == "com.android.settings") {
            // Si estás en "Acerca del teléfono", "Opciones de desarrollo" o USB, NO tocar nada
            if (SAFE_SETTINGS_CLASSES.any { currentClass.contains(it) }) {
                return // Acceso garantizado sin expulsiones
            }

            // En otras áreas de ajustes, solo verificar si intentan buscar "Fake GPS" en el buscador
            val rootNode = rootInActiveWindow ?: return
            if (containsMockGpsEvasion(rootNode)) {
                Log.w(TAG, "Intento de Mock Location en Ajustes. Expulsando...")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // -----------------------------------------------------------------
        // 5. TIENDAS E INSTALADORES: Evitar que busquen o descarguen Opera/Fake GPS
        // -----------------------------------------------------------------
        if (STORE_PACKAGES.contains(currentPackage)) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScanTime < 300) return
            lastScanTime = now

            val rootNode = rootInActiveWindow ?: return
            if (containsStoreForbiddenContent(rootNode)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun handleTelegramEvent(currentPackage: String) {
        val prefs: TelegramPreferences = telegramDataStore.getCurrentPreferences()
        val telegramPkg = prefs.telegramPackage
        if (telegramPkg.isEmpty()) return

        if (currentPackage == telegramPkg) {
            backgroundCheckRunnable?.let { mainHandler.removeCallbacks(it) }
            backgroundCheckRunnable = null

            if (!isTelegramInForeground) {
                isTelegramInForeground = true
                Log.d(TAG, "Telegram ENTRÓ a primer plano: $telegramPkg")

                val action = accessController.evaluateOnAppForeground(telegramPkg) {
                    isTelegramInForeground = false
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }

                if (action is SessionAction.BLOCK_AND_SUSPEND) {
                    isTelegramInForeground = false
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    adminComponent?.let { PackageSuspender.setSuspended(this, it, telegramPkg, true) }
                }
            }
        } else {
            if (isTransientOrSystemPackage(currentPackage)) {
                return
            }

            if (isTelegramInForeground) {
                backgroundCheckRunnable?.let { mainHandler.removeCallbacks(it) }
                backgroundCheckRunnable = Runnable {
                    val topPkg = rootInActiveWindow?.packageName?.toString()
                    if (topPkg != telegramPkg) {
                        Log.d(TAG, "Telegram SALIÓ a segundo plano de forma confirmada.")
                        isTelegramInForeground = false
                        accessController.evaluateOnAppBackground(telegramPkg)
                    }
                }
                mainHandler.postDelayed(backgroundCheckRunnable!!, 1200L)
            }
        }
    }

    private fun isTransientOrSystemPackage(packageName: String): Boolean {
        if (packageName == "com.android.systemui" || packageName == "android") return true

        if (packageName.contains("inputmethod") ||
            packageName.contains("keyboard") ||
            packageName.contains("ime") ||
            packageName == "com.google.android.inputmethod.latin" ||
            packageName == "com.touchtype.swiftkey" ||
            packageName == "com.sohu.inputmethod.sogou.xiaomi" ||
            packageName == "com.miui.securityadd") {
            return true
        }

        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            val imes = imm?.enabledInputMethodList
            if (imes != null) {
                for (ime in imes) {
                    if (ime.packageName == packageName) return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun containsMockGpsEvasion(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (LOCATION_EVASION_KEYWORDS.any { text.contains(it) || desc.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsMockGpsEvasion(child)) return true
        }
        return false
    }

    private fun containsStoreForbiddenContent(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (AppVetoManager.BLOCKED_KEYWORDS.any { text.contains(it) || desc.contains(it) } ||
            LOCATION_EVASION_KEYWORDS.any { text.contains(it) || desc.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsStoreForbiddenContent(child)) return true
        }
        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accesibilidad interrumpida.")
    }
}