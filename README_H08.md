# OneHouse H09.5 TEST — Entrega 39

Monitor único Cocina + Comedor: `1/2/6`, `1/2/5`, `1/2/2`.
ON/OFF de Comedor confirmado por el usuario mediante H09.5 DIAG.
Validación física: 5/5 PASS comunicados por el usuario, Xiaomi 14T Pro, 11/11/2026.
Incluye tres luces, rotación/navegación, Home y lecturas H08 inicial/final.
CSV originales no analizados por Codex. Saltos reales, WiFi extrema y carreras
Android siguen pendientes; alcance limitado a esas pruebas, sin monitor permanente.
Consulta `LEEME_H09_5_ENTREGA39_Monitor_Cocina_Comedor_TEST.txt`.

# OneHouse H09.4 TEST — Entrega 38

Continuidad del monitor durante la recreación de Activity por rotación.
La salida real a segundo plano y el bloqueo de pantalla solicitan STOP.
Recreación sin Activity visible durante más de un segundo: cierre de seguridad.
Referencia física anterior: H09.3, 4/4 pruebas principales PASS comunicadas por el usuario.
H09.4: 6/6 pruebas físicas principales PASS en Xiaomi 14T Pro el 11/11/2026,
según resultados comunicados por el usuario; CSV originales no analizados por Codex.
Validados en esa batería: lectura inicial/final 35/35, rotación, rotaciones repetidas,
Home y bloqueo. Recuperación ante saltos reales y condiciones extremas WiFi pendientes.
Alcance experimental limitado a esas pruebas; no es monitorización permanente
ni validación de toda la funcionalidad KNX. H08.2.2 / Entrega 34 sigue como base estable.

Consulta `LEEME_H09_4_ENTREGA38_Ciclo_Vida_Rotacion_TEST.txt`.

---

# OneHouse H09.3 TEST — Entrega 37

Recuperación estricta de secuencias del monitor H09: espera acotada a 4 s,
recuperación solo al recibir la secuencia pendiente y cierre controlado si falla.
Sin reconexiones automáticas ni solicitudes de túnel salientes del monitor.
Referencia experimental con 4/4 pruebas físicas principales PASS comunicadas por el usuario.
Recuperación ante un salto de secuencia real pendiente de validación física.
H08.2.2 / Entrega 34 sigue siendo la base estable.

Consulta `LEEME_H09_3_ENTREGA37_Recuperacion_Secuencias_KNX_TEST.txt`.

---

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
