# Reglas permanentes del proyecto OneHouse-H-v2

1. Trabajar exclusivamente en la rama `dev` o en ramas de trabajo derivadas de ella. No modificar `main` sin autorización.
2. Preservar el comportamiento validado del motor KNX H08.2.2 / Entrega 34.
3. No modificar `UnifiedReceiverRunner.kt` ni `KnxProtocol.kt` sin autorización expresa.
4. No cambiar direcciones de grupo KNX sin autorización.
5. Mantener las 35 lecturas de la base estable y el comportamiento de los sensores de lluvia y viento.
6. Consultar los documentos README y LEEME antes de desarrollar funcionalidades.
7. Realizar cambios pequeños, controlados y verificables.
8. Comprobar la compilación Gradle después de modificar código, cuando el entorno lo permita.
9. No introducir contraseñas, claves o datos privados de la vivienda en Git.
10. Preparar un LEEME específico para cada nueva entrega, indicando versión, cambios, pruebas y posibles riesgos.
11. No realizar commits, push ni merges sin autorización del usuario.
12. No declarar PASS funcional KNX únicamente porque la aplicación compile: requiere las pruebas reales correspondientes.
