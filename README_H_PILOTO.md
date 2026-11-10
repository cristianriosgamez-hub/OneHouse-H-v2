# OneHouse H PILOTO v2.0.0-H07.7.3 — Entrega 29

**Diagnostico del ventilador corregido, sin cambios en el motor H ni escrituras KNX.**

La prueba H07.7.2 (7 noviembre 2026) recibio 35/35 estados, incluido 5/3/5 =
0x80 (MEDIA), con clima OFF, pero tambien registro un tiempo de espera transitorio
`H_RESPONSE_TIMEOUT` en 5/3/3 (Temperatura Daikin). Ese timeout ajeno al
ventilador provocaba `INVALID_READ` en la auditoria del FAN.

H07.7.3 separa: (a) evidencia de lectura del FAN/ON-OFF, (b) advertencias
ajenas al FAN, (c) requisito ESTRICTO para autorizar cualquier nueva orden.
Por ejemplo, en una H 35/35 cerrada correctamente, sin retransmisiones ni
ACK faltantes, un timeout transitorio de 5/3/3 NO invalida el valor 0x80
recibido y confirmado en 5/3/5; **SI mantiene el bloqueo estricto de controles**.

El CSV de cada nueva lectura incluye `H0773_EVIDENCIA_LECTURA_FAN_SOLO_DIAGNOSTICO`,
incluso si no se envia ninguna orden. Tras una orden FAN se amplia
`H077_DIAGNOSTICO_CLIMA_FAN_MEDIA` para distinguir confirmado, aviso y fisica.

**LEEME:** `LEEME_H0773_ENTREGA29_Diagnostico_Fan.txt`.

## Seguridad y versiones anteriores
- Piloto experimental `com.onehouse.app.hpilot`. No modifica OneHouse estable.
- Un solo GroupValueWrite a `5/2/5` con `0x5E` (MEDIA 37 %); se conserva.
- No envia ON/OFF ni enciende climatizacion para cambiar velocidad.
- Estado MEDIA 5/3/5 = `0x80` observado; clima OFF = solo confirmacion KNX.
- Motor H 35 lecturas (160 ms minimo), ACK, cierre, lluvia y viento solo eventos,
  control luces/persianas/clima y automatizaciones del piloto: sin cambios.
- No se reducen requisitos de autorizacion de nuevas escrituras.

# OneHouse H PILOTO v2.0.0-H07.7.2 — Entrega 28

**Cambio BAJA→MEDIA permitido con climatizador OFF**, sin enviar ON, conservando la interpretación MEDIA anterior y el motor H.

- La orden MEDIA del proyecto continúa enviando **37 % (`0x5E`)** a la GA `5/2/5`.
- La respuesta de estado MEDIA **observada y comprobada físicamente** en H07.7 fue **`0x80`** en `5/3/5` (50,2 % de escala bruta DPT 5.001).
- H07.7.1 y H07.7.2 diferencian ambos valores en la pantalla del piloto, en la auditoría y en el CSV; **no altera las escrituras KNX**.
- Se mantiene una única orden por lectura H limpia, confirmación expresa, sin reintentos y sin automatizaciones.
- OneHouse estable `com.onehouse.app` no se modifica. El piloto sigue usando `com.onehouse.app.hpilot`.

**Instrucciones y pruebas H07.7.2:** `LEEME_H0772_ENTREGA28_Control_Ventilador_OFF.txt`.

---

# ONEHOUSE H PILOTO v2.0.0-H07.7 — ENTREGA 26

Proyecto Android Studio de validacion KNX: **velocidad BAJA → MEDIA del ventilador**, un solo comando
tras una lectura H limpia, con ACK y comprobacion posterior. Mando `5/2/5`, estado `5/3/5`, DPT 5.001.

**LEEME principal:** `LEEME_H077_ENTREGA26_Velocidad_Ventilador.txt`.
**Valores del proyecto:** BAJA 25% (`0x40`), MEDIA 37% (`0x5E`), ALTA 100%. No mandar
ninguna orden si la configuracion o los valores leidos difieren.

La app principal OneHouse (`com.onehouse.app`) NO se modifica; este piloto conserva
el identificador `com.onehouse.app.hpilot`.

---

# OneHouse H PILOTO v2.0.0-H07.6 — Entrega 25

Aplicación experimental separada (`com.onehouse.app.hpilot`) basada en H07.5.
Añade **una sola orden controlada de modo AUTO (DPT20.105, `5/2/4`)**, exclusivamente
cuando el clima está apagado y tras una lectura H 35/35 limpia.

El estado se verifica por `5/3/4`. No se tocan la consigna, el ON/OFF ni el ventilador.
Mantiene las luces y dos persianas probadas; motor H de lectura sin cambios.

**Instrucciones detalladas, seguridad, direcciones y CSV:**
`LEEME_H076_ENTREGA25_Modo_AUTO_Climatizacion.txt`.

En Android Studio: abrir la carpeta del proyecto, sincronizar Gradle, ejecutar la app
H PILOTO en el móvil. No instalar sobre la aplicación estable OneHouse.

## H07.7.2 — fan BAJA→MEDIA con clima OFF
Esta entrega permite enviar UNA escritura a 5/2/5 sin mandar ON en 5/2/2. Requiere H limpia, BAJA recibida y modo AUTO/VENTILADOR conocido. Con OFF, ACK o evento de estado no equivalen a velocidad fisica aplicada. Ver LEEME_H0772_ENTREGA28_Control_Ventilador_OFF.txt.
