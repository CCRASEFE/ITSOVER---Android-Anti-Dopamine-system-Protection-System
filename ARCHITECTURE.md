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
