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
