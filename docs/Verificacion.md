# Verificación de la entrega

## Evidencias ejecutadas

Entorno: Windows x64, JDK 26.0.2.1, compilación `--release 17`, JavaFX 17.0.16,
Oracle Database 21c Express Edition y PDB XEPDB1 abierta. El esquema
`ENPH_P12_TEST` se creó tras autorización expresa del usuario; cuarenta MB de
cuota, CREATE SESSION/TABLE/TRIGGER y ninguna modificación a tablas existentes.

**Compilación:** todos los archivos principales y de pruebas compilaron.

**ProjectTests:** dieciséis escenarios aprobados: catorce fechas, días vacíos,
fechas futuras/fuera del período, regalos, exclusión de ausencia/gasto, Otra,
menor en día catorce, exclusión D1 y categorías, precisión monetaria, cierre
inmutable, renglones de igual nombre, recuperación y segunda instancia, Unicode
y truncamiento, identidades duplicadas, autenticación HTTP y rechazo de HTTP remoto.
Esta suite usa un doble de backend para su único escenario HTTP; no simula Oracle
en los resultados de integración que siguen.

**OracleIntegrationTest:** ejecución real aprobada. Período de evidencia inicial:
`9586d7d0-8e87-43a0-8792-f4d7217fde2d`, hogar `PRUEBA INTEGRACIÓN`.

1. Asignar Laura, reasignar Pedro antes de captura y conservar historial.
2. Programar catorce fechas retrospectivas ficticias.
3. Bloquear rol sin permiso y consulta del crítico antes de recepción.
4. Registrar reporte de finalización, acción y seguimiento atendido sin fingir recepción.
5. Entregar al dispositivo; bloquear reasignación y cambio de fechas posteriores.
6. Recibir una adquisición y trece declaraciones de ausencia en la misma transacción.
7. Repetir envío: mismo recibo y una sola fila de recepción.
8. Observar leche de 48000, documentar aclaración y evaluar observación persistente.
9. Bloquear resultado sin observaciones mientras hay hallazgo pendiente.
10. Confirmar revisión con observaciones, conservar el valor 48000 y comprobar que
    el disparador rechaza su cambio directo con ORA-20001.
11. Rechazar contenido diferente con la misma identidad de recepción.
12. Provocar una identidad de gasto duplicada después de insertar un primer gasto
    válido: rollback completo, cero adquisiciones y cero recibos en el período fallido.
13. Consultar datos mediante HTTP autenticado conectado al repositorio Oracle real;
    el conjunto conserva una adquisición y trece ausencias y el rol se obtiene de Oracle.

La verificación ampliada se repitió después de estos cambios y pasó. Período de
esa ejecución: `095aabb4-773e-42ac-8767-47ecac42e2c7`. También se ejecutaron
los scripts entregados de compilación y pruebas y se generó JavaDoc correctamente.

## Alcance de la evidencia

La suite de integración valida servicio y Oracle. No acredita que cada botón de
JavaFX haya sido recorrido por una persona, una tableta real, la totalidad de
fallos de energía o el servidor de la universidad. No se declara cobertura total.
La compilación JavaFX es evidencia técnica diferente de una prueba visual y de
interacción. La dependencia de visitas realizadas y la transferencia entre
dispositivos continúan pendientes, señaladas en DEC-041.

## Repetición

```powershell
./scripts/Build.ps1
./scripts/Test.ps1
./scripts/Test.ps1 -Oracle
./scripts/Javadoc.ps1
```

La prueba Oracle requiere conexión y esquema dedicado instalado previamente.
Cada ejecución agrega un nuevo hogar de prueba; no borra evidencia existente.
Las pruebas generan únicamente datos ficticios. Las credenciales y capturas no
forman parte del ZIP o del repositorio.

