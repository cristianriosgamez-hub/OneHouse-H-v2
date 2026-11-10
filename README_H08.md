# OneHouse H09.2 TEST — Entrega 36

**Experimento de eventos KNX en Cocina** sobre la base validada H08.2.2 / Entrega 34.
El monitor se activa manualmente **solo después de un PASS**, dura como máximo
90 segundos, escucha únicamente 1/2/6 y 1/2/5, no emite GroupValueRead
ni GroupValueWrite y se detiene al salir a segundo plano. No interfiere
con el lector H08.2.2 y dispone de un CSV independiente.

Lee `LEEME_H09_2_ENTREGA36_Correcciones_Auditoria_TEST.txt` para instrucciones
de instalación y pruebas antes de dar esta fase por validada.

---

# OneHouse H08.2.2 TEST — Entrega 34

**Ruta de prueba:** WiFi anclada, diagnostico de errores UDP/EPERM, verificacion pasiva
ante cambio de red y recuperacion manual controlada tras incidencias. Sigue siendo
una version experimental con dos controles de Cocina; no es version estable.

Consulta `LEEME_H08_2_2_ENTREGA34_UDP_Recuperacion_Segura.txt` para el protocolo
completo y las limitaciones verificadas.

---

# OneHouse H08.2.1 — Entrega 33

Corrección experimental de enrutamiento WiFi del socket KNX/IP y diagnóstico de arranque sin CONNECT_RESPONSE.

**Motor H: un solo receptor, 35 GAs, sin reintentos automáticos nuevos.**
Controles reales limitados a las dos luces de Cocina. Icono naranja TEST.

Consultar `LEEME_H08_2_1_ENTREGA33_Conexion_WiFi_Diagnostico.txt` para pruebas y límites.

---

# OneHouse H08.2 TEST — Entrega 32

Esta versión sustituye experimentalmente H08.1 manteniendo la lectura H de 35 GAs
que ya fue validada por el usuario en la app real. **Solo** reactiva los dos
interruptores de la pantalla real Cocina (Luz `1/1/6` y Fluorescente `1/1/5`),
mediante el ejecutor KNX/IP de H07.1, con confirmación, una orden total por
lectura limpia, sin reintentos y sin solapamiento de túneles. Los demás controles
siguen bloqueados. No hay automatizaciones ni monitorización continua.

La app lleva **icono naranja TEST** y se llama `OneHouse H08.2 TEST`. El ID
`com.onehouse.app.h08` es distinto del OneHouse estable, por lo que no lo pisa.

**Lee `LEEME_H08_2_ENTREGA32_Controles_Cocina_TEST.txt` antes de probar.**

---

# OneHouse H08.1 — Integración real, diagnóstico CSV (Entrega 31)

Aplicación completa OneHouse en modo **solo lectura**, con motor H de 35 solicitudes KNX/IP y repositorio compartido con pantallas reales. La versión 2.0.0 estable está separada.

**H08.1** conserva el motor H y corrige los diagnósticos: ID único y número por lectura, distinción de primera exportación frente a reexportación y conservación del resultado al guardar la misma IP. Cambiar de gateway invalida el CSV anterior.

Los controles, el lector KNX antiguo, las automatizaciones y las programaciones siguen desactivados. El usuario debe verificar 35/35 y comparar las pantallas con el estado físico.

Consulta `LEEME_H08_1_ENTREGA31_Diagnostico_CSV.txt` antes de compilar y probar.
