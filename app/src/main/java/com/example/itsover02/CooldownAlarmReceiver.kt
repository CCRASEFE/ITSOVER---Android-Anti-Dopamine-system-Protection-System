package com.example.itsover02.telegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.itsover02.DeviceAdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CooldownAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CooldownAlarmReceiver"
    }

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == AlarmScheduler.ACTION_COOLDOWN_EXPIRED) {
            Log.i(TAG, "¡Alarma de cooldown recibida! El período de bloqueo ha finalizado.")

            val dataStore = TelegramDataStore(context)
            val adminComponent = DeviceAdminReceiver.getComponentName(context)

            receiverScope.launch {
                val prefs = dataStore.getCurrentPreferences()
                val targetPkg = prefs.telegramPackage

                if (targetPkg.isNotEmpty()) {
                    // Reactivar Telegram (quitar suspensión)
                    val success = PackageSuspender.setSuspended(context, adminComponent, targetPkg, false)
                    if (success) {
                        Log.i(TAG, "Telegram reactivado automáticamente tras expiración de cooldown: $targetPkg")
                    }
                }

                // Restaurar estado a AVAILABLE
                dataStore.updateState(ControlState.AVAILABLE)
            }
        }
    }
}