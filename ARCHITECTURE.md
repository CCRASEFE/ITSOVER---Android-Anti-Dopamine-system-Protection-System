# 📐 Arquitectura del Sistema y Métodos de Ingeniería — DNS Shield

Este documento detalla el diseño técnico, las decisiones arquitectónicas, el flujo de procesamiento de paquetes y las tecnologías que componen **DNS Shield v1.0.0**.

---

## 1. Pila Tecnológica (Tech Stack)

* **Lenguaje:** Kotlin 2.0.21 (compilador nativo con integración de Compose Compiler Gradle Plugin).
* **Entorno de Compilación:** Android Gradle Plugin (AGP) 8.x / 9.x, Gradle 8.x+, Java 11.
* **Compatibilidad de Plataforma:** Min SDK 26 (Android 8.0 Oreo) — Target SDK 34/37 (Android 14+ / 15).
* **Interfaz de Usuario:** Jetpack Compose (BOM 2024.04.01), Material 3, arquitectura reactiva con `StateFlow` y ciclo de vida de arquitectura limpia (MVVM).
* **Concurrencia:** Kotlin Coroutines (`Dispatchers.IO`, `SupervisorJob`, `CoroutineScope`).
* **Subsistema de Redes:** `android.net.VpnService`, sockets nativos BSD (`DatagramSocket`), buffers binarios `java.nio.ByteBuffer` y llamadas a descriptores de archivo del kernel Linux (`ParcelFileDescriptor`).
* **Seguridad y MDM:** `android.app.admin.DevicePolicyManager`, `android.os.UserManager`, almacenamiento cifrado FBE (`createDeviceProtectedStorageContext`).

---

## 2. Diagrama de Flujo de Red (Data Path)

El sistema intercepta exclusivamente las transacciones DNS sin procesar el tráfico web masivo (HTTP/HTTPS):

```text
               Dispositivo Android (Espacio de Usuario / Kernel)
┌────────────────────────────────────────────────────────────────────────┐
│  Aplicaciones / Navegadores (Chrome, Firefox, Apps nativas)             │
│   │                                                                    │
│   ├── [Consultas DNS: UDP 53 a 10.0.0.1 / fd00::1]                     │
│   │                                                                    │
│   ▼                                                                    │
│  Túnel Virtual VPN (/dev/tun / tun2) <── Enrutado por VpnService       │
│   │                                                                    │
│   ├── [Lectura en Streaming: FileInputStream (MTU 32767)]              │
│   ▼                                                                    │
│  LocalDnsBlockerService (CoroutinePool / Dispatchers.IO)               │
│   │                                                                    │
│   ├── 1. Parseo de Cabecera IP (Byte 0: Versión 4 vs. Versión 6)       │
│   ├── 2. Validación de Protocolo (Byte 9/6: UDP 17)                    │
│   ├── 3. Extracción de Payload DNS y Decodificación de QNAME (RFC 1035)│
│   │                                                                    │
│   ├── ¿Dominio en BlocklistRepository?                                 │
│   │     ├── SÍ:                                                        │
│   │     │   ├── Construir Respuesta Sintética NXDOMAIN (RCODE 3)       │
│   │     │   ├── Recalcular Checksums (IP Checksum / IPv6 UDP Checksum) │
│   │     │   └── Escribir directamente en FileOutputStream del túnel    │
│   │     │                                                              │
│   │     └── NO:                                                        │
│   │         ├── Socket UDP Protegido con protect(socket)               │
│   │         ├── Salida por red subyacente física (Wi-Fi/LTE) a 8.8.8.8 │
│   │         ├── Recepción de respuesta real de Internet                │
│   │         └── Reempaquetar paquete IP y escribir en FileOutputStream │
│   │                                                                    │
│   └── [Tráfico Web TCP 80/443 (allowBypass)]:                          │
│       Sale directo a la antena sin pasar por la CPU de la app          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Módulos y Responsabilidades del Código
```text
app/src/main/java/com/example/itsover02/
├── MainActivity.kt                      # UI mínima reactiva en Compose y solicitud inicial de VpnService.
├── MainViewModel.kt                     # Exposición del estado StateFlow del servicio hacia la UI.
├── LocalDnsBlockerService.kt            # Motor principal del túnel TUN, loop de red, y resolución upstream.
├── BlocklistRepository.kt               # Repositorio inmutable en memoria con streaming desde assets.
├── DeviceAdminReceiver.kt               # Orquestador de políticas corporativas DevicePolicyManager.
├── BootReceiver.kt                      # Receptor Direct-Boot Aware para autoarranque del sistema.
├── EmergencyUninstallReceiver.kt        # Receptor de contingencia para revocación de permisos MDM.
├── MdmPolicyManager.kt                  # Inyector de Managed Configurations en navegadores comerciales.
├── AppRestrictionManager.kt             # Gestor de listas negras de paquetes y suspensión Device Owner.
├── PackageChangeReceiver.kt             # Centinela reactivo ante eventos PACKAGE_ADDED.
└── EvasiveAppBlockerAccessibilityService.kt # Inspector de UI en tiempo real para detección heurística.
```

---

## 4. Métodos y Técnicas de Bajo Nivel

### A. Evasión del Límite de Bytecode de la JVM (64 KB)

En la especificación de la JVM (Section 4.7.3), ningún método compilado puede exceder 65.535 bytes de bytecode.

* **Problema:** Declarar colecciones estáticas en código Kotlin con miles de literales (`hashSetOf("...", "...")`) hace que el compilador inserte instrucciones `LDC` en el método `<clinit>`, provocando `MethodTooLargeException`.
* **Solución:** La lista de **11.000+** dominios se empaqueta sellada en `app/src/main/assets/blocklist.txt`. `BlocklistRepository.loadFromAssets()` la consume mediante `BufferedReader.useLines()` en streaming durante el arranque del servicio, poblando un `HashSet<String>(15000)` en ~15 milisegundos con cero impacto de bytecode y un consumo de heap de solo **13.7 MB** aproximandamente.

### B. Algoritmo de Matching Recursivo O(Labels)

Para verificar si un dominio o cualquiera de sus subdominios está bloqueado, no se utilizan expresiones regulares pesadas:

```bash
fun isBlocked(domain: String): Boolean {
    val normalized = domain.lowercase().trimEnd('.')
    if (BLOCKED_DOMAINS.contains(normalized)) return true

    var dotIndex = normalized.indexOf('.')
    while (dotIndex != -1) {
        val parentDomain = normalized.substring(dotIndex + 1)
        if (BLOCKED_DOMAINS.contains(parentDomain)) return true
        dotIndex = normalized.indexOf('.', dotIndex + 1)
    }
    return false
}
```
Complejidad temporal acotada a la cantidad de subdominios (L≤5), ejecutándose en nanosegundos por paquete.
