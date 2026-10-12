# Manual técnico del código - punto 12

## Propósito y bases

La guía del proyecto, página 4, solicita código documentado y URL de un repositorio
público GitHub. Se tomó el contexto maestro como antecedente y se aplicaron las
correcciones posteriores DEC-035/036: cinco CU, seis RF, RNF-01 y revisión sin
edición central. La guía no exige implementar once CU para el punto 12 aislado.
La entrega del código no declara cumplidos automáticamente los demás puntos.

Java, Oracle y Cliente-Servidor están confirmados en las instrucciones generales.
JavaFX aparece confirmado en el contexto maestro, DEC-004. Los componentes
adicionales de esta implementación están registrados como propuesta DEC-041.

## Responsabilidades

| Archivo | Responsabilidad e invariantes |
|---|---|
| Domain | Cantidades positivas, valores no negativos, precisión, catálogos, campos Otra, aplicabilidad, fechas, ausencia exclusiva y cierre de catorce fechas |
| LocalStore | Modelo local explícito, lectura sin deserialización Java, bloqueo de segunda instancia, guardado con archivo temporal y reemplazo atómico |
| Wire | Identificador ENPH1; filas y columnas acotadas; cadenas de DataOutputStream; máximo ocho MiB |
| ApiClient | Envío autenticado, tiempos de espera, redirecciones deshabilitadas y HTTPS fuera de localhost |
| OracleRepository | Validación de permisos desde Oracle, bloqueo del período, transacciones, integridad de recepción, supervisión y revisión |
| Server | Tokens externos, usuario autenticado, ejecución fuera del cliente, errores sin credenciales ni SQL |
| EnphApp | Formularios JavaFX, confirmación/cancelación, tablas, reloj Bogotá, trabajos de red fuera del hilo gráfico y reintento automático |
| DbSetup | DDL explícito en esquema vacío y catálogos/referencias ficticias |

## Modelo físico de esta versión

`db/01_esquema.sql` contiene PK, FK, CHECK, índices y disparadores. HOGAR se
relaciona 1:N con PERIODO; este conserva asignaciones y versiones de programación.
La asignación abierta es única mediante índice condicional; las citas pertenecen
a una programación y a una asignación. La última programación se determina por
su número secuencial, evitando depender del orden de un UUID o de marcas horarias
empatadas. Un encuestador es una referencia habilitada con rol operativo.

Cada adquisición conserva período, fecha, autor y momento de registro. Los
catálogos son tablas separadas y sus códigos se conservan como texto. Ausencia
tiene PK `(periodo_id, fecha)`. No se guardan días de semana, totales, ordinales
de fecha ni fecha final redundantes. Son valores derivados.

RECEPCION usa el período como PK, por lo que existe a lo sumo un recibo por
conjunto cerrado. REVISION depende de RECEPCION. OBSERVACION tiene exactamente
un objetivo: fecha o adquisición. ACLARACION conserva cada explicación por
separado. ACCION, SEGUIMIENTO y NOVEDAD conservan acompañamiento sin fingir
recepción. Una acción atendida no equivale a un gasto validado.

El modelo E-R conceptual DEC-038 sigue preliminar. Este SQL es una propuesta
física que debe contrastarse con dominio, objetos y clases del punto 7 y con el
relacional definitivo del punto 9. No sustituye silenciosamente esos modelos.

## Transacciones e integridad

Toda operación central abre su conexión JDBC con `autoCommit=false`. Si una
validación o sentencia falla, se ejecuta rollback y no se comunica éxito. El
commit ocurre antes de que el servidor envíe su respuesta. OracleRepository
bloquea el período para serializar modificaciones de sus datos. Para comprobar
disponibilidad de citas también bloquea la referencia del encuestador.

La recepción valida el conjunto completo y su reloj de finalización, inserta
adquisiciones y ausencias, y crea el recibo al final de la misma transacción.
SHA-256 identifica el contenido recibido; no es firma digital ni cifrado. Un
reintento idéntico devuelve el mismo recibo. Un conjunto diferente con la misma
identidad se rechaza; no sobrescribe ni compara versiones de gastos.

Los disparadores bloquean inserciones, modificaciones y borrados de gastos y
ausencias después del recibo. El usuario propietario del esquema puede alterar
DDL; en un despliegue real deben separarse propietario y cuenta de ejecución.
Las reglas cruzadas de autoría, calendario y conjunto íntegro se aplican en el
servicio. Este montaje no pretende que cada una esté duplicada en un disparador.

La persistencia local exige `ATOMIC_MOVE`. Si el sistema de archivos no permite
el reemplazo atómico, informa fallo y conserva el archivo anterior. Los cambios
se aplican a una copia cargada desde disco; no se confirma en la interfaz un
guardado fallido. Los archivos temporales abandonados no se toman como capturas.

## Protocolo

Una solicitud consta de un primer renglón con el nombre de operación y otro con
sus argumentos. PROGRAM puede incluir citas adicionales; RECEIVE incluye la
cabecera y todas las filas de la captura. La respuesta contiene filas de texto.
No se usa `ObjectInputStream` ni se ejecuta código enviado por el cliente.

| Operación | Permiso | Resultado |
|---|---|---|
| ME, USERS, PERIODS | Usuario habilitado | Identidad, referencias y períodos; encuestador ve sus asignados |
| ASSIGN, PROGRAM | Coordinador | Responsable/calendario e historial |
| CHECKOUT, RECEIVE | Encuestador responsable | Entrega local exclusiva/recibo íntegro |
| DATA | Supervisor o Crítico | Adquisiciones y ausencias, solo tras recepción |
| INFO | Coordinador, Supervisor o Crítico | Historial y seguimiento; hallazgos solo recibidos |
| ACTION, FOLLOW, REPORT | Supervisor | Acompañamiento y novedades reportadas |
| OBSERVE, CLARIFY, EVALUATE, REVIEW | Crítico | Atención del hallazgo y resultado sin corregir gastos |

HTTP 200 indica operación confirmada; 401 sesión no autorizada; 400 regla o
solicitud inválida; 503 fallo técnico sin confirmación. El cliente conserva la
captura ante falta de respuesta. RNF-01 reintenta automáticamente; otras operaciones
no se reintentan solas. Si su confirmación se pierde, consulte el historial antes
de repetir una acción central; esta versión no ofrece idempotencia general.

## Configuración y operación

| Variable | Uso |
|---|---|
| JAVA_HOME | JDK para scripts |
| OJDBC_JAR | Ruta al controlador oficial Oracle |
| ENPH_DB_URL / ENPH_DB_USER / ENPH_DB_PASSWORD | Conexión central; únicamente servidor, instalación y pruebas |
| ENPH_TOKENS_FILE | Archivo privado identidad=token para servidor |
| ENPH_TOKEN | Token privado del cliente |
| ENPH_SERVER_URL | URL del endpoint /rpc, HTTP local o HTTPS remoto |
| ENPH_LOCAL_DIR | Carpeta local distinta por cliente; estable para conservar dispositivo y datos |
| ENPH_PORT | Puerto local del servidor; 8080 por defecto |

Java 17 es el nivel de compilación propuesto. La verificación local se ejecutó
con JDK 26.0.2.1; no se afirma una ejecución adicional sobre JDK 17. Los scripts
usan bibliotecas nativas JavaFX para Windows x64. Otro sistema exige bibliotecas
de su plataforma y verificación real. La configuración de IDE queda abierta.

`CreateTokens.ps1` no muestra secretos y no sobrescribe un archivo existente.
La sesión local guardada contiene identidad y rol, nunca el token. La captura
sin red solo se retoma para un encuestador con sesión previa y referencias
preparadas. Los archivos locales no están cifrados: protegerlos con permisos del
usuario, cifrado de disco y una política de respaldo antes de tratar datos reales.

El cliente debe permanecer abierto para reintentar envíos. No hay servicio de
sincronización que opere después de cerrar JavaFX. No copie ni regenere la
identidad del dispositivo para eludir una entrega exclusiva.

## Restricciones y mantenimiento

- No hay edición central de gastos ni resolución de versiones de adquisición.
- No hay liberación de la captura ni transferencia entre tabletas. Un fallo de
  dispositivo requiere analizar continuidad antes de desbloquear manualmente.
- Las citas reservan una hora. Es política propuesta, no regla oficial ENPH.
- No se capturan visitas realizadas. Todas las citas conservan su estado de
  previstas. Sigue abierta la dependencia PD-09 de CU-03.
- La categoría se declara por el operador; no hay reconocimiento semántico de
  artículos por su nombre. Seleccionar una categoría válida para un producto
  excluido no lo convierte en admisible. La validación de dominio y la revisión
  humana deben completar la matriz de aplicabilidad PD-06.
- No se afirma cumplimiento de protección de datos, producción, autenticación
  institucional ni autorización granular por hogar para los roles centrales.
- La referencia de período no se cambia al reprogramar; se conserva su identidad.
- No hay módulo de mantenimiento de usuarios ni CRUD de capítulos fuera del
  subconjunto. El punto 13 debe comprobarse y documentarse por separado.

## Fuentes técnicas

- OpenJFX, [Getting Started](https://openjfx.io/openjfx-docs/): módulos y ejecución de JavaFX.
- Oracle, [JDBC Developer's Guide 21c](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/): controlador, tipos y transacciones.
- JDK, [HttpServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
  y [Files.move](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): contrato de servidor y reemplazo atómico.
- Fuentes académicas locales y decisiones: véase la matriz de trazabilidad.

