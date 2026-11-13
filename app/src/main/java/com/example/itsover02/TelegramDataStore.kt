package com.example.itsover02.telegram

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TelegramDataStore(private val context: Context) {

    private val prefs: SharedPreferences = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val protectedContext = context.createDeviceProtectedStorageContext()
        protectedContext.getSharedPreferences("telegram_control_prefs", Context.MODE_PRIVATE)
    } else {
        context.getSharedPreferences("telegram_control_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_TELEGRAM_PACKAGE = "telegram_package"
        private const val KEY_CONTROL_STATE = "control_state"
        private const val KEY_SESSION_START_ELAPSED = "session_start_elapsed"
        private const val KEY_COOLDOWN_EXPIRES_WALL = "cooldown_expires_wall"
        private const val KEY_COOLDOWN_EXPIRES_ELAPSED = "cooldown_expires_elapsed"
        private const val KEY_BOOT_ID = "boot_id"
        private const val KEY_IS_SESSION_ACTIVE = "is_session_active"

        val KNOWN_TELEGRAM_PACKAGES = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
            "org.telegram.plus",
            "org.telegram.BifToGram"
        )
    }

    init {
        if (getSavedPackage().isEmpty()) {
            val detected = autoDetectTelegramPackage()
            if (detected.isNotEmpty()) {
                saveTelegramPackage(detected)
            }
        }
    }

    private fun autoDetectTelegramPackage(): String {
        val pm = context.packageManager
        for (pkg in KNOWN_TELEGRAM_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return "org.telegram.messenger"
    }

    private fun getSavedPackage(): String {
        return prefs.getString(KEY_TELEGRAM_PACKAGE, "") ?: ""
    }

    fun saveTelegramPackage(packageName: String) {
        prefs.edit().putString(KEY_TELEGRAM_PACKAGE, packageName).apply()
    }

    val telegramPreferencesFlow: Flow<TelegramPreferences> = callbackFlow {
        trySend(getCurrentPreferences())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(getCurrentPreferences())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun getCurrentPreferences(): TelegramPreferences {
        val stateStr = prefs.getString(KEY_CONTROL_STATE, ControlState.AVAILABLE.name)
        val controlState = try {
            ControlState.valueOf(stateStr ?: ControlState.AVAILABLE.name)
        } catch (_: Exception) {
            ControlState.AVAILABLE
        }

        // Fijado directo al paquete real detectado
        val pkg = "org.telegram.messenger"

        return TelegramPreferences(
            telegramPackage = pkg,
            controlState = controlState,
            sessionStartElapsed = prefs.getLong(KEY_SESSION_START_ELAPSED, 0L),
            cooldownExpiresWall = prefs.getLong(KEY_COOLDOWN_EXPIRES_WALL, 0L),
            cooldownExpiresElapsed = prefs.getLong(KEY_COOLDOWN_EXPIRES_ELAPSED, 0L),
            bootId = prefs.getString(KEY_BOOT_ID, "") ?: "",
            isSessionActive = prefs.getBoolean(KEY_IS_SESSION_ACTIVE, false)
        )
    }

    fun updateState(
        state: ControlState,
        sessionStartElapsed: Long = 0L,
        cooldownExpiresWall: Long = 0L,
        cooldownExpiresElapsed: Long = 0L,
        isSessionActive: Boolean = false
    ) {
        val currentBootId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.provider.Settings.Global.getString(context.contentResolver, "boot_count") ?: System.currentTimeMillis().toString()
        } else {
            SystemClock.elapsedRealtime().toString()
        }

        prefs.edit().apply {
            putString(KEY_CONTROL_STATE, state.name)
            putLong(KEY_SESSION_START_ELAPSED, sessionStartElapsed)
            putLong(KEY_COOLDOWN_EXPIRES_WALL, cooldownExpiresWall)
            putLong(KEY_COOLDOWN_EXPIRES_ELAPSED, cooldownExpiresElapsed)
            putString(KEY_BOOT_ID, currentBootId)
            putBoolean(KEY_IS_SESSION_ACTIVE, isSessionActive)
            apply()
        }
    }
}