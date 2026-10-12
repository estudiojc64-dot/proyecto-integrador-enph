# Trazabilidad y matriz de evidencia

## Correspondencia de la implementación

| Necesidad del software | CU / RF | Código | Datos | Evidencia de prueba |
|---|---|---|---|---|
| Responsable vigente y conservación del antecedente | CU-02 / RF-02, RF-04 | OracleRepository ASSIGN; EnphApp coordinator | ASIGNACION, USUARIO, PERIODO | OracleIntegrationTest: Laura → Pedro antes de captura; bloqueo posterior |
| Calendario de catorce fechas y citas compatibles | CU-03 / RF-03 | PROGRAM, currentVisits, checkAvailability | PROGRAMACION, VISITA_PREVISTA | Fin calculado, programación Oracle y bloqueo con captura entregada |
| Captura, ausencia explícita y cierre | CU-06 / RF-07 | Domain, LocalStore, EnphApp | Archivo local; ADQUISICION/AUSENCIA tras recepción | Dieciséis escenarios y conjunto de catorce fechas en Oracle |
| Supervisión con información disponible | CU-09 / RF-10 | INFO, ACTION, FOLLOW, REPORT | ACCION, SEGUIMIENTO, NOVEDAD | Reporte/acción atendida sin habilitar consulta previa a recibir |
| Revisar y documentar hallazgos sin cambiar el dato | CU-10 / RF-11 | OBSERVE, CLARIFY, EVALUATE, REVIEW | REVISION, OBSERVACION, ACLARACION | Leche conserva 48000; resultado sin observaciones bloqueado ante pendiente |
| Envío y confirmación sin duplicar | RNF-01 | sendPending, RECEIVE, transacción JDBC | RECEPCION PK período; huella SHA-256 | Repetición devuelve el mismo recibo y una sola fila |

El problema y los objetivos académicos deben conservar el alcance vigente de la
propuesta corregida: apoyo a la tabla D y a su seguimiento. Este código no añade
resultados estadísticos del DANE ni atribuye deficiencias operativas a la entidad.
Los modelos UML y BD previos no están cerrados por esta implementación; su
correspondencia debe verificarse antes de entregar los puntos 7 y 9 como definitivos.

## Afirmación → Fuente → Evidencia → Estado → Uso

| Afirmación | Fuente | Evidencia | Estado | Uso |
|---|---|---|---|---|
| Punto 12 pide código documentado y URL pública | Guía oficial `sources/Proyecto Integrador IV.pdf` | Página 4; extracción local de la guía | Verificado documentalmente | Entrega de código y repositorio |
| POO, Cliente-Servidor y persistencia relacional | Misma guía | Página 2, requerimiento | Verificado documentalmente | Separación cliente/servidor/Oracle |
| JavaFX fue confirmado | Contexto maestro extraído en `trabajo/referencias/maestro.txt` | Sección tecnología; DEC-004 | Decisión del equipo documentada; extracción histórica | Interfaz JavaFX, sin inventar IDE |
| Cinco CU y seis RF, sin corrección central | `entrega/Revision-final-CU-y-prototipo-DEC035-036.md` | Tabla y resultado de revisión; descripciones vigentes | Verificado documentalmente en estado posterior | Delimitar código y retirar RF-12/RF-13/CU-11 |
| Ausencia debe ser expresa y cierre exige catorce fechas | CU-06 vigente DEC-035 | Precondiciones, postcondiciones y alternativas | Decisión del equipo documentada | Domain y pruebas |
| Catorce días también para bolsillo | DEC-029 | Simplificación académica aprobada | Decisión de diseño del equipo | No aplicar la regla histórica de siete días al código |
| Limpieza, cuidado personal, vestuario y otros están excluidos de D | Cuadernillo 2 Urbano suministrado | Página física 3, Qué NO DEBO registrar | Verificado documentalmente | Categoría excluida y revisión humana; no catálogo automático |
| Catálogos incluyen 2.1 y códigos de lugar discontinuos | Cuadernillo y diccionario preliminar | Hoja de catálogos; transcripción previamente inspeccionada | Verificado documentalmente; transcripción local | Strings de código y tablas de catálogo |
| E-R y diccionario no están aprobados ni incluyen DDL definitivo | DEC-038 y LEEME BD preliminar | Estado y PD-01 a PD-09 | Verificado documentalmente | Mantener cambios físicos como propuesta |
| Archivos locales, protocolo, JDBC y reserva de citas de una hora | Código nuevo y DEC-041 | Implementación concreta y alternativas | Decisión de diseño propuesta | Prueba revisable; requiere aprobación del equipo |
| Código compila y pasan dieciséis escenarios | Ejecución local | ProjectTests y verificación de compilación | Verificado técnico | Evidencia de software; no prueba toda la interfaz |
| Oracle 21c XE funciona con esta versión | Oracle local autorizado | DDL, instalación e integración real en ENPH_P12_TEST | Verificado técnico en ese entorno | No extrapolar al servidor universitario |
| El flujo con captura pendiente puede reasignarse entre tabletas | No hay mecanismo confirmado | El código bloquea la acción | Pendiente; afirmación de capacidad no sustentada | No declarar CU-02 completo en esa variante |
| Compatible con tableta real Android/iOS | Sin plataforma confirmada | Solo se prueba Windows x64 | Pendiente | No afirmar compatibilidad móvil |

Las rutas de fuentes remiten al espacio de trabajo académico de referencia; no
se copian los documentos originales al repositorio público. No hay afirmaciones
acerca de la situación **actual** del DANE ni resultados empíricos inventados.

