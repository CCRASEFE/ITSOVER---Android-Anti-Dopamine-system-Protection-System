# 🔐 Auditoría de Ciberseguridad y Modelo de Amenazas — DNS Shield

Este informe documenta la postura de seguridad, las pruebas de laboratorio realizadas en hardware físico, las mitigaciones aplicadas y el estado transparente de las brechas técnicas identificadas al cierre de la **Versión 1.0.0**.

---

## 📊 Matriz de Estado de Seguridad (Status Dashboard)

| Vector / Brecha | Severidad | Estado en v1.0.0 | Mecanismo de Defensa Implementado |
| :--- | :---: | :---: | :--- |
| **Tampering / Desinstalación** | Crítica | 🛡️ **MITIGADO** | Directivas MDM Device Owner (`setUninstallBlocked = true`). |
| **Evasión por DNS Privado (DoT)** | Alta | 🛡️ **MITIGADO** | Desactivación por directiva global y bloqueo de UI (`DISALLOW_CONFIG_PRIVATE_DNS`). |
| **Fuga de DNS por IPv6 (IPv6 Leak)** | Alta | 🛡️ **MITIGADO** | Túnel Dual-Stack completo con `fd00::1` y parsing RFC 8200. |
| **DoH en Navegadores (Firefox / Chrome)**| Media | 🛡️ **MITIGADO** | Dominio Canario (`use-application-dns.net`) + Inyección de Managed Configs. |
| **Evasión por Proxy/VPN en Opera** | Media | ⚠️ **PARCIAL / PENDIENTE** | Mitigación heurística activa; persistencia en proxy web por TCP 443. |
| **Upstream en Texto Plano (MitM)** | Media | ❌ **ABIERTA** | Consultas permitidas salen a `8.8.8.8` vía UDP 53 sin cifrado TLS. |

---

## 🔎 Análisis Detallado de Brechas y Pruebas Forenses

---

### Brecha A: Fuga por "DNS Privado" del Sistema Operativo (DoT - Puerto 853)
* **Descripción de la Amenaza:** En Android 9+, si el usuario configura un servidor en *Ajustes > DNS Privado*, el sistema operativo desvía todas las consultas DNS por fuera de la VPN hacia el puerto TCP 853 con cifrado TLS.
* **Mitigación Implementada:**  
  A través de `DeviceAdminReceiver.enforceHardening()`, el Device Owner ejecuta:
  
  ```kotlin
  dpm.setGlobalSetting(adminComponent, "private_dns_mode", "off")
  dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

---

### Brecha B: DNS sobre HTTPS en Navegadores (DoH) y Proxies de Aplicación

**1. Caso Mozilla Firefox y Google Chrome**

* **Descripción:** Los navegadores encapsulan peticiones DNS en tráfico web HTTPS ordinario (puerto 443 TCP) hacia proveedores externos.
* **Mitigación Implementada:**
     1. **Dominio Canario:** `BlocklistRepository` responde con `NXDOMAIN` a `use-application-dns.net`. Al recibir esta respuesta, Firefox y Chrome desactivan automáticamente el motor DoH por estándar RFC.
     2. **DoH Sinkholing:** Bloqueo explícito de los hostnames de bootstrap (`cloudflare-dns.com`, `dns.google`, etc.).
     3. **Managed Configurations (`MdmPolicyManager`):** Se inyectan directivas corporativas a los paquetes:
        * Chrome: `DnsOverHttpsMode = "off"`, `IncognitoModeAvailability = 1`.
        * Firefox: `network.trr.mode = 5` (desactivado forzado), `InstallAddonsPermission = false`.
* **Resultado de Auditoría: ÉXITO.** DoH se apaga y el tráfico cae de nuevo al puerto 53 UDP donde la VPN lo neutraliza.

**2. Caso Específico: Navegador Opera (Proxy TLS / Evasión Persistente)**
* **Descripción del Fallo:** Opera (`com.opera.browser`, `com.opera.gx`) incorpora un interruptor de "VPN gratuita". Al activarlo, Opera no crea un `VpnService` (lo cual estaría impedido por nuestra app), sino que levanta un **Proxy HTTP/2 TLS en el puerto 443** hacia la red de SurfEasy/Opera.
* **Por qué fallaron las medidas estándar:**
     1. *El Split-Tunnel:* Como la VPN utiliza `.allowBypass()` para permitir la navegación web ordinaria sin sobrecargar la CPU, los paquetes TCP hacia el puerto 443 salen directo a la antena sin ser evaluados por el socket UDP 53.
     2. *IPs Precompiladas:* Aunque se devuelva `NXDOMAIN` a los dominios de Opera (`vpn.opera.com`), el binario compilado de Opera incluye direcciones IP numéricas hardcodeadas.
     3. *Incumplimiento de MDM:* Opera ignora silenciosamente los paquetes de `setApplicationRestrictions`.
* **Acción Aplicada en v1.0.0**
  Se implementó una **estrategia de cuarentena y contención**:
  * Un servicio centinela de accesibilidad (EvasiveAppBlockerAccessibilityService) expulsa al usuario al Home si intenta buscar o abrir Opera en la Play Store.
  * AppVetoManager mantiene un bucle en segundo plano que detecta el paquete, lo suspende (setPackagesSuspended) y ejecuta una orden de desinstalación silenciosa desatendida.
* **Dictamen de Seguridad: Solución perimetral.** Opera queda neutralizado por expulsión de interfaz y desinstalación de paquete, pero a nivel de red pura el vector del proxy por el puerto 443 sigue existiendo si el binario lograse ejecutarse.

---

### Brecha C: Fuga por Redes Móviles IPv6 (IPv6 DNS Leak)
* **Descripción de la Amenaza:** En redes móviles 4G/5G, los operadores entregan direcciones y servidores DNS en formato IPv6 nativo. Como la versión inicial solo interceptaba IPv4 (`10.0.0.1`), las consultas salían por la tarjeta física del operador puenteando el filtro.
* **Mitigación Implementada:**
  1. Asignación de interfaz Dual-Stack en `LocalDnsBlockerService`:
```kotlin

builder.addAddress("fd00::2", 128)
builder.addDnsServer("fd00::1")
builder.addRoute("fd00::1", 128)
``````
  2. Analizador de paquetes para `version == 6` (cabeceras de 40 bytes) y cálculo matemático estricto del checksum UDP para IPv6 (RFC 8200) utilizando la pseudo-cabecera.
* **Prueba de Laboratorio (Hardware Físico Xiaomi + Red Móvil Claro Perú):**
Se auditó en browserleaks.com/dns con datos móviles activos.
  * **Resultado:** `Found 90 Servers, 1 ISP (Google LLC)`.
  * Direcciones IPv6 detectadas: `2800:3f0:4001:...` y `2800:3f0:4003:...` (nodos de Anycast de Google en Chile y Brasil).
  * **Fugas hacia la operadora física (América Móvil Perú): CERO (0%)**.
  * **Dictamen: BRECHA 100% RESUELTA Y AUDITADA**.

---

### Brecha D: Upstream en Texto Plano (Man-in-the-Middle) — [ACTIVA]
* **Descripción de la Vulnerabilidad:**
Cuando una consulta corresponde a un dominio permitido (ej. `google.com`), `LocalDnsBlockerService.forwardDnsQuery()` la reenvía a `8.8.8.8` a través de un `DatagramSocket` en texto plano mediante el puerto 53 UDP.
* **Impacto Potencial:**
  * **Espionaje de Tráfico (Eavesdropping):** En redes Wi-Fi públicas o a nivel de ISP, un observador pasivo con Wireshark puede registrar qué dominios permitidos visita el dispositivo.
  * **DNS Spoofing / Cache Poisoning:** Un atacante en la red local podría emitir respuestas UDP falsificadas antes de que llegue la respuesta legítima de Google.
* **Estado Actual: ABIERTA.** La versión 1.0.0 cierra el ciclo de filtrado local de forma exitosa, pero no implementa cifrado criptográfico en el tramo de subida hacia internet.

---

## 🗺️ Hoja de Ruta de Seguridad (Roadmap v1.1.0)
Para las siguientes versiones del proyecto, las prioridades de desarrollo serán:
1. **Resolución de la Brecha D (Cifrado DoT de Salida)**:
Migrar el método `forwardDnsQuery()` de sockets UDP planos a un socket seguro DNS-over-TLS (RFC 7858) sobre el puerto TCP 853 hacia `dns.google` o `cloudflare-dns.com`, garantizando confidencialidad e integridad criptográfica total en el upstream.
2. **Bloqueo CIDR de Proxies Comerciales:**
Identificar los bloques de enrutamiento IP (BGP / ASN) pertenecientes a la infraestructura de servidores proxy de Opera (AS39832) e inyectar esas subredes dentro de las rutas del `VpnService` para descartar los paquetes (drop) a nivel de kernel, cerrando la puerta trasera del puerto 443 sin requerir un proxy de inspección profunda.
