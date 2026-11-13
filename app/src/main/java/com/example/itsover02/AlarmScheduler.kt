package com.example.itsover02.telegram

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_COOLDOWN_EXPIRED = "com.example.itsover02.ACTION_COOLDOWN_EXPIRED"
    private const val ALARM_REQUEST_CODE = 2026

    fun scheduleCooldownAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CooldownAlarmReceiver::class.java).apply {
            action = ACTION_COOLDOWN_EXPIRED
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)

        try {
            // Comprobar si se pueden programar alarmas exactas (Android 13+ / API 33+)
            val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canSchedule) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Log.i(TAG, "Alarma exact y allow while idle programada para: $triggerAtMillis")
            } else {
                // Fallback seguro si no hay permiso de alarma exacta
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.w(TAG, "Permiso de alarma exacta denegado. Se programó alarma estándar.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al programar la alarma de cooldown", e)
        }
    }

    fun cancelCooldownAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CooldownAlarmReceiver::class.java).apply {
            action = ACTION_COOLDOWN_EXPIRED
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Alarma de cooldown cancelada.")
        }
    }
}