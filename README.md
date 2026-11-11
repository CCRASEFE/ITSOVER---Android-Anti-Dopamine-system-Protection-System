# 🛡️ DNS Shield (ITS OVER) — v1.0.0
> **Sistema local de filtrado DNS inmutable, persistente mediante Device Owner (MDM) y Dual-Stack (IPv4 / IPv6) para Android.**

[![Android](https://img.shields.io/badge/Platform-Android_8.0+_--_14+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0+-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Security](https://img.shields.io/badge/Security-Device_Owner_%7C_MDM-red?style=flat)](SECURITY.md)

---

## 📌 Visión General

**DNS Shield** es una solución nativa de ciberseguridad para dispositivos Android diseñada para operar como un **DNS Sinkhole local** autosuficiente, inmutable e inamovible. 

A diferencia de las aplicaciones de filtrado convencionales o servicios VPN comerciales:
* **No depende de servidores remotos:** Toda la inspección de paquetes ocurre directamente en la memoria RAM del dispositivo dentro de un túnel `VpnService` local.
* **Inmutabilidad Absoluta:** No expone interfaces para desactivar dominios ni pausar el escudo.
* **Control a Nivel de Sistema Operativo:** Aprovecha privilegios de **Device Owner (MDM)** para anclar la VPN como *Always-On*, bloquear su desinstalación física y desactivar vías de evasión en los Ajustes del sistema.

---

## 🚀 Características Principales

* 🔒 **Filtrado Dual-Stack (IPv4 / IPv6):** Inspección de consultas DNS en puerto 53 UDP tanto en direcciones IPv4 (`10.0.0.1`) como IPv6 (`fd00::1`) con cálculo de checksums RFC 8200 (cero fugas de red).
* ⚡ **Alto Rendimiento en Streaming:** Motor optimizado con corrutinas concurrentes capaz de evaluar más de **11.000 dominios** en microsegundos sin superar el límite de bytecode de 64 KB de la JVM.
* 🛡️ **Blindaje de Device Owner:** Modo *Always-On VPN* inmutable por el usuario, tolerante a reinicios del teléfono (`BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`) y a actualizaciones del APK (`MY_PACKAGE_REPLACED`).
* 🚫 **Mitigación de Evasiones:** Bloqueo de DNS Privado del sistema (DoT), señalización mediante Dominio Canario de Mozilla (`use-application-dns.net`) y directivas administradas para navegadores comerciales.
* 🧯 **Llave Maestra de Emergencia:** Receptor protegido que permite revocar los candados corporativos mediante un comando seguro por ADB en caso de mantenimiento.

---

## 📚 Documentación Técnica Adicional

* 📐 [**ARCHITECTURE.md**](ARCHITECTURE.md) — Documentación profunda sobre diseño de software, manejo de sockets de bajo nivel, parsing binario de paquetes y ciclo de vida de componentes.
* 🔐 [**SECURITY.md**](SECURITY.md) — Modelo de amenazas, análisis forense de bypasses (Firefox vs. Opera), pruebas de laboratorio y estado de las brechas de seguridad.

---

## 🛠️ Guía de Despliegue y Puesta en Marcha

Sigue esta guía paso a paso para compilar, aprovisionar y ejecutar el proyecto desde cero utilizando Android Studio.

### Requisitos
* **Android Studio** (Koala, Ladybug o superior) con SDK 34+.
* **Dispositivo físico o emulador** ejecutando Android 8.0+ (API 26+) hasta Android 14+ (probado exhaustivamente en Xiaomi HyperOS / MIUI).
* Cable USB y herramientas de línea de comandos (`adb`) configuradas en el PATH.

---

### Paso 0: Preparación Obligatoria del Teléfono (¡Crítico!)
Por especificación de seguridad de AOSP, el comando de **Device Owner solo puede asignarse si el dispositivo no contiene cuentas de usuario activas**:

1. Ve a **Ajustes > Contraseñas y Cuentas** (o *Cuentas y Sincronización*).
2. **Elimina temporalmente** todas las cuentas existentes (Google, WhatsApp, Xiaomi/Mi Account, Outlook, etc.).  
   *(Nota: Una vez ejecutado el comando del Paso 3, **podrás volver a iniciar sesión en todas tus cuentas normalmente** y el blindaje persistirá).*
3. Activa las **Opciones de desarrollador** y habilita la **Depuración USB**.
   * *En dispositivos Xiaomi / POCO:* Activa también **Instalar vía USB** y **Depuración USB (Ajustes de seguridad)**.

---

### Paso 1: Clonar y Abrir el Proyecto

1. Clona el repositorio y guardalo en la ubicación de tu preferencia.
2. Entra a Android Studio e ingresa a la carpeta donde clonaste el repositorio
3. Permite que Gradle sincronice las dependencias (Sync Project with Gradle Files)

---

### Paso 2: Compilar e Instalar el Binario

1. Conecta el dispositivo a la computadora por USB.
2. En la barra superior de Android Studio, ve a:
**Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. Abre la terminal de Android Studio y despliega el APK:
 ```bash
   adb install -r -d app\build\outputs\apk\debug\app-debug.apk 
```
4. Inicializa el proceso en el teléfono abriendo la actividad principal:
 ```bash
   adb shell am start -n com.example.itsover02/.MainActivity
```

---

### Paso 3: Aprovisionar Privilegios de Device Owner (DO)
Ejecuta el comando oficial de aprovisionamiento en la terminal:
 ```bash
   adb shell dpm set-device-owner com.example.itsover02/.DeviceAdminReceiver
```
* Salida requerida en consola:
 ```bash
   Success: Device owner set to package ComponentInfo{com.example.itsover02/com.example.itsover02.DeviceAdminReceiver}
Active admin set to component {com.example.itsover02/com.example.itsover02.DeviceAdminReceiver}
```
(Opcional: Si el dispositivo es Xiaomi / HyperOS, otorga el permiso de autoarranque):
```bash
   adb shell appops set com.example.itsover02 10008 allow
```

---

### Paso 4: Activación del Túnel y Restauración de Cuentas
1. En la aplicación, pulsa el botón "CONCEDER PERMISO Y ACTIVAR".
2. Acepta el cuadro de diálogo del sistema operativo para la conexión VPN.
3. El indicador cambiará a color verde: "FILTRADO ACTIVO".
4. Listo: Ahora puedes volver a vincular todas tus cuentas (Google, WhatsApp, redes sociales). La aplicación ha quedado anclada al kernel del dispositivo.

---

## 🧪 Pruebas de Funcionamiento Rápido
* **Filtrado en caliente:** Navega desde Chrome a cualquier dominio restringido (ej. tiktok.com). Recibirás un fallo inmediato ERR_NAME_NOT_RESOLVED. Sitios permitidos (google.com, wikipedia.org) cargarán con fluidez normal.
* **Test de Fugas DNS:** Ingresa a browserleaks.com/dns. Todas las consultas DNS detectadas (IPv4 e IPv6) pertenecerán al resolver upstream configurado (Google LLC), con cero fugas hacia tu proveedor de internet móvil.
* **Tolerancia a Reinicios:** Reinicia el teléfono (adb reboot). Al desbloquear la pantalla con tu PIN, el icono de la llave VPN se levantará por sí solo sin abrir la aplicación.
* **Protección Anti-Desinstalación:** Intenta desinstalar la app desde el cajón de aplicaciones o los Ajustes; el botón estará inhabilitado o el sistema arrojará "No se pudo eliminar".

---

## 🚨 Salida de Emergencia (Desinstalación Segura por ADB)
Al operar como Device Owner, la aplicación no puede ser desinstalada por métodos tradicionales. Para desinstalarla durante fases de desarrollo o mantenimiento, conecta el dispositivo por USB y ejecuta los dos comandos de la Llave Maestra:
```bash
# 1. Enviar el broadcast protegido que revoca el rol de Device Owner y desactiva los candados
adb shell am broadcast -a com.example.itsover02.EMERGENCY_UNLOCK

# 2. Desinstalar el paquete limpiamente
adb uninstall com.example.itsover02
```

