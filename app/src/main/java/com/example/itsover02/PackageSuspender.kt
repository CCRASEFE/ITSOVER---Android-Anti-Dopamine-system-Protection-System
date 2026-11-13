package com.example.itsover02.telegram

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

object PackageSuspender {

    private const val TAG = "PackageSuspender"

    fun setSuspended(context: Context, adminComponent: ComponentName, targetPackage: String, suspend: Boolean): Boolean {
        if (targetPackage.isBlank()) return false
        if (targetPackage == context.packageName) {
            Log.e(TAG, "¡ALERTA DE SEGURIDAD! Se intentó suspender la propia aplicación de control. Operación abortada.")
            return false
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "No se puede suspender paquetes: La app no es Device Owner.")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // setPackagesSuspended devuelve un array con los paquetes que NO pudieron ser suspendidos
                val failedPackages = dpm.setPackagesSuspended(adminComponent, arrayOf(targetPackage), suspend)
                val success = failedPackages.isEmpty()
                if (success) {
                    Log.i(TAG, "Paquete $targetPackage suspendido exitosamente = $suspend")
                } else {
                    Log.w(TAG, "El sistema no pudo suspender el paquete: $targetPackage")
                }
                success
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción ejecutando setPackagesSuspended para $targetPackage", e)
            false
        }
    }

    fun isSuspended(context: Context, adminComponent: ComponentName, targetPackage: String): Boolean {
        if (targetPackage.isBlank()) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.isPackageSuspended(adminComponent, targetPackage)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando isPackageSuspended para $targetPackage", e)
            false
        }
    }
}