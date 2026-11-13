package com.example.itsover02.telegram

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.itsover02.DeviceAdminReceiver

object BootRecoveryHelper {

    private const val TAG = "BootRecoveryHelper"

    fun handleBootRecovery(context: Context) {
        val dataStore = TelegramDataStore(context)
        val prefs: TelegramPreferences = dataStore.getCurrentPreferences()
        val targetPkg = prefs.telegramPackage

        if (targetPkg.isBlank()) return

        val adminComponent = DeviceAdminReceiver.getComponentName(context)
        val nowWall = System.currentTimeMillis()

        val currentBootId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.provider.Settings.Global.getString(context.contentResolver, "boot_count") ?: nowWall.toString()
        } else {
            SystemClock.elapsedRealtime().toString()
        }

        val isReboot = prefs.bootId.isNotEmpty() && prefs.bootId != currentBootId

        if (isReboot) {
            Log.w(TAG, "Se detectó reinicio del sistema. Aplicando recuperación conservadora...")

            if (prefs.isSessionActive) {
                Log.w(TAG, "Reinició durante sesión activa. Forzando Cooldown inmediato de protección.")
                val newCooldownWall = nowWall + AccessController.COOLDOWN_DURATION_MS
                val newCooldownElapsed = SystemClock.elapsedRealtime() + AccessController.COOLDOWN_DURATION_MS

                PackageSuspender.setSuspended(context, adminComponent, targetPkg, true)

                dataStore.updateState(
                    state = ControlState.COOLDOWN,
                    cooldownExpiresWall = newCooldownWall,
                    cooldownExpiresElapsed = newCooldownElapsed,
                    isSessionActive = false
                )

                AlarmScheduler.scheduleCooldownAlarm(context, newCooldownWall)

            } else if (prefs.controlState == ControlState.COOLDOWN) {
                if (nowWall >= prefs.cooldownExpiresWall) {
                    Log.i(TAG, "Cooldown expiró durante el reinicio. Reactivando Telegram.")
                    PackageSuspender.setSuspended(context, adminComponent, targetPkg, false)
                    dataStore.updateState(ControlState.AVAILABLE)
                } else {
                    Log.i(TAG, "Cooldown aún vigente. Manteniendo Telegram suspendido.")
                    PackageSuspender.setSuspended(context, adminComponent, targetPkg, true)
                    AlarmScheduler.scheduleCooldownAlarm(context, prefs.cooldownExpiresWall)
                }
            } else {
                PackageSuspender.setSuspended(context, adminComponent, targetPkg, false)
            }
        }
    }
}