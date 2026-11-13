package com.example.itsover02.telegram

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.itsover02.DeviceAdminReceiver

sealed class SessionAction {
    object ALLOW : SessionAction()
    object BLOCK_AND_SUSPEND : SessionAction()
}

class AccessController(
    private val context: Context,
    private val dataStore: TelegramDataStore
) {

    companion object {
        private const val TAG = "AccessController"
        const val SESSION_DURATION_MS = 20_000L   // 30 segundos
        const val COOLDOWN_DURATION_MS = 20*60_000L  // 1 minuto de prueba
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sessionTimeoutRunnable: Runnable? = null

    fun evaluateOnAppForeground(targetPkg: String, onForceClose: () -> Unit): SessionAction {
        val prefs = dataStore.getCurrentPreferences()
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        // 1. Si estaba en COOLDOWN, verificar si el tiempo ya expiró
        if (prefs.controlState == ControlState.COOLDOWN) {
            if (nowElapsed >= prefs.cooldownExpiresElapsed || nowWall >= prefs.cooldownExpiresWall) {
                Log.i(TAG, "Cooldown expirado. Reactivando estado AVAILABLE.")
                cancelSessionTimeout()
                dataStore.updateState(ControlState.AVAILABLE)
                val adminComponent = DeviceAdminReceiver.getComponentName(context)
                PackageSuspender.setSuspended(context, adminComponent, targetPkg, false)
            } else {
                Log.w(TAG, "Telegram aún en COOLDOWN. Bloqueando acceso.")
                return SessionAction.BLOCK_AND_SUSPEND
            }
        }

        val updatedPrefs = dataStore.getCurrentPreferences()
        return when (updatedPrefs.controlState) {
            ControlState.AVAILABLE -> {
                Log.i(TAG, "Sesión de 30s iniciada para Telegram.")
                dataStore.updateState(
                    state = ControlState.ACTIVE_SESSION,
                    sessionStartElapsed = nowElapsed,
                    isSessionActive = true
                )
                scheduleSessionTimeout(targetPkg, onForceClose)
                SessionAction.ALLOW
            }

            ControlState.ACTIVE_SESSION -> {
                val elapsed = nowElapsed - updatedPrefs.sessionStartElapsed
                if (elapsed >= SESSION_DURATION_MS) {
                    Log.w(TAG, "Límite de 30s alcanzado. Forzando suspensión.")
                    cancelSessionTimeout()
                    triggerCooldown(targetPkg)
                    SessionAction.BLOCK_AND_SUSPEND
                } else {
                    // Mantener el temporizador restante si la app sigue activa
                    scheduleRemainingTimeout(targetPkg, SESSION_DURATION_MS - elapsed, onForceClose)
                    SessionAction.ALLOW
                }
            }

            ControlState.COOLDOWN -> {
                SessionAction.BLOCK_AND_SUSPEND
            }
        }
    }

    fun evaluateOnAppBackground(targetPkg: String) {
        val prefs = dataStore.getCurrentPreferences()
        if (prefs.controlState == ControlState.ACTIVE_SESSION) {
            Log.i(TAG, "Usuario salió de Telegram antes de 30s. Activando COOLDOWN.")
            cancelSessionTimeout()
            triggerCooldown(targetPkg)
        }
    }

    private fun scheduleSessionTimeout(targetPkg: String, onForceClose: () -> Unit) {
        cancelSessionTimeout()
        sessionTimeoutRunnable = Runnable {
            Log.w(TAG, "30 segundos cumplidos. Expulsando usuario y suspendiendo Telegram.")
            triggerCooldown(targetPkg)
            onForceClose()
        }
        mainHandler.postDelayed(sessionTimeoutRunnable!!, SESSION_DURATION_MS)
    }

    private fun scheduleRemainingTimeout(targetPkg: String, remainingMs: Long, onForceClose: () -> Unit) {
        if (sessionTimeoutRunnable != null) return
        sessionTimeoutRunnable = Runnable {
            Log.w(TAG, "Tiempo restante de 30s cumplido. Suspendiendo Telegram.")
            triggerCooldown(targetPkg)
            onForceClose()
        }
        mainHandler.postDelayed(sessionTimeoutRunnable!!, remainingMs.coerceAtLeast(100L))
    }

    private fun cancelSessionTimeout() {
        sessionTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        sessionTimeoutRunnable = null
    }

    private fun triggerCooldown(targetPkg: String) {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val cooldownWall = nowWall + COOLDOWN_DURATION_MS
        val cooldownElapsed = nowElapsed + COOLDOWN_DURATION_MS

        dataStore.updateState(
            state = ControlState.COOLDOWN,
            cooldownExpiresWall = cooldownWall,
            cooldownExpiresElapsed = cooldownElapsed,
            isSessionActive = false
        )

        val adminComponent = DeviceAdminReceiver.getComponentName(context)
        PackageSuspender.setSuspended(context, adminComponent, targetPkg, true)
        AlarmScheduler.scheduleCooldownAlarm(context, cooldownWall)
    }
}