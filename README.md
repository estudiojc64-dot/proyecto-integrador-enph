# Proyecto Integrador IV - ENPH, primera tabla D

Código documentado del **punto 12 del segundo avance**. Repositorio público:
https://github.com/estudiojc64-dot/proyecto-integrador-enph

Aplicación académica propia; no es un sistema oficial del DANE. Implementa los
flujos seleccionados de asignación, programación, captura, supervisión y revisión.
La captura local conserva adquisiciones y ausencias expresas; el servidor persiste
en Oracle. Las adquisiciones recibidas no se corrigen centralmente.

## Estado de entrega

- Código Java y JavaFX completo de esta versión, SQL, scripts y pruebas incluidos.
- Documentación **dentro de los archivos**: JavaDoc en `.java`, comentarios SQL y
  PowerShell. Manual técnico, decisiones y trazabilidad en `docs/`.
- Compilación realizada con JDK 26.0.2.1 usando `--release 17`; JavaFX 17.0.16.
- Dieciséis escenarios de reglas/local/HTTP aprobados.
- Prueba real contra Oracle 21c XE/XEPDB1 ejecutada en el esquema aislado
  `ENPH_P12_TEST`, autorizado por el usuario, con datos ficticios.
- No acredita cierre del primer avance, aprobación del modelo preliminar DEC-038,
  instalación en el servidor universitario ni compatibilidad con una tableta real.
- La reasignación después de entregar una captura a un dispositivo está bloqueada
  preventivamente. El mecanismo de transferencia entre tabletas sigue pendiente.

## Alcance y navegación

| Caso | Rol | Funciones |
|---|---|---|
| CU-02 / RF-02, RF-04 | Coordinador | Asignar y reasignar antes de entregar captura; responsable único e historial |
| CU-03 / RF-03 | Coordinador | Inicio, fin calculado, citas previstas, incompatibilidades e historial |
| CU-06 / RF-07 | Encuestador | Captura local, consulta, edición local, ausencia explícita, catorce fechas y cierre |
| CU-09 / RF-10 | Supervisor | Programación, plazos, acciones, seguimientos, novedades y datos recibidos |
| CU-10 / RF-11 | Crítico-digitador | Observaciones, aclaraciones, evaluación y resultado sin editar adquisiciones |
| RNF-01 | Sistema | Envío automático del conjunto cerrado, confirmación y reintento sin duplicados |

Sin D1/D2, sin CU-11 y sin RF-12/RF-13. Referencias de usuarios/hogares y catálogos
se precargan; no se agregan módulos de identificación, visitas realizadas o usuarios.

## Estructura

```text
src/main/java/co/edu/udi/enph/
  Domain.java             Reglas, catálogos y modelo local
  LocalStore.java         Archivos locales atómicos
  Wire.java               Contrato de intercambio con límites
  ApiClient.java          Cliente autenticado HTTP/HTTPS
  OracleRepository.java   Transacciones, permisos y persistencia Oracle
  Server.java             Servidor central
  EnphApp.java            Interfaz JavaFX
  DbSetup.java            Instalación explícita y datos ficticios
src/test/java/co/edu/udi/enph/
  ProjectTests.java       Reglas, recuperación local y HTTP
  OracleIntegrationTest.java  Prueba real de Oracle
db/01_esquema.sql         Tablas, restricciones e inmutabilidad
scripts/                 Compilación, dependencias, ejecución y JavaDoc
docs/                    Manual, trazabilidad, decisiones y evidencias
```

## Preparación en Windows

Se requieren un **JDK 17 o posterior**, PowerShell, Oracle 21c o compatible y un
controlador JDBC oficial compatible. La ejecución verificada usa Windows x64.
No se necesita Maven ni Gradle. Desde la carpeta del repositorio:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-26.0.2.1' # Cambiar a su JDK
./scripts/Dependencies.ps1
./scripts/Build.ps1
./scripts/Test.ps1
./scripts/Javadoc.ps1
```

`Dependencies.ps1` descarga tres bibliotecas oficiales desde Maven Central con
versión y SHA-256 fijos. No se publican binarios de terceros en este repositorio.
`build/javadoc/index.html` contiene la documentación generada desde el código.
Para compilar únicamente reglas, servidor y pruebas: `Build.ps1 -CoreOnly`.

## Oracle

El administrador debe proporcionar un **esquema nuevo, dedicado y vacío** en
XEPDB1 con `CREATE SESSION`, `CREATE TABLE`, `CREATE TRIGGER` y cuota suficiente
en el tablespace asignado. Los scripts del repositorio no crean cuentas Oracle.
No use SYS, SYSTEM ni un esquema que contenga tablas de otros trabajos.

Configure las variables en la terminal del operador. La contraseña se introduce
mediante lectura oculta; no se escribe como literal en un archivo del repositorio:

```powershell
$env:ENPH_DB_URL = 'jdbc:oracle:thin:@//127.0.0.1:1521/XEPDB1'
$env:ENPH_DB_USER = 'SU_ESQUEMA_DEDICADO'
$enphSecret = Read-Host 'Contraseña Oracle' -AsSecureString
$env:ENPH_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $enphSecret).Password
$env:OJDBC_JAR = 'C:/Oracle/dbhomeXE/jdbc/lib/ojdbc11.jar' # Ajustar si corresponde
./scripts/SetupOracle.ps1
./scripts/Test.ps1 -Oracle
```

El instalador rechaza un esquema con tablas. Oracle hace commits implícitos al
ejecutar DDL: si falla la instalación, revise el error y el esquema antes de
reintentar; no hay borrado automático. La prueba de integración conserva un hogar
`PRUEBA INTEGRACIÓN` para inspeccionar los resultados.

## Servidor y clientes

Genere los tokens fuera del código público:

```powershell
./scripts/CreateTokens.ps1
$env:ENPH_TOKENS_FILE = (Resolve-Path './.private/tokens.properties').Path
./scripts/Server.ps1
```

En otra terminal, configure JDK, servidor y token privado del usuario:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-26.0.2.1'
$env:ENPH_SERVER_URL = 'http://127.0.0.1:8080/rpc'
$enphSecret = Read-Host 'Token del operador' -AsSecureString
$env:ENPH_TOKEN = [System.Net.NetworkCredential]::new('', $enphSecret).Password
$env:ENPH_LOCAL_DIR = (Join-Path $env:LOCALAPPDATA 'ENPH/Pedro')
./scripts/Client.ps1
```

Las identidades ficticias de `DbSetup` terminan en `001` Coordinador, `002`
Encuestador Pedro, `003` Supervisor, `004` Crítico y `005` Encuestadora Laura.
El archivo de tokens contiene esas identidades completas. **El servidor determina
el rol consultando Oracle**; el cliente no permite elegir un rol libremente.

Para cuatro clientes simultáneos, use una carpeta local distinta por operador.
La carpeta local conserva la identidad del dispositivo. Después del primer inicio
con conexión, el encuestador puede abrir sus capturas ya preparadas sin red.
Las funciones centrales requieren conexión. El envío reintenta cada ocho segundos
en segundo plano mientras el cliente del encuestador está abierto.

El servidor escucha exclusivamente en `127.0.0.1`. Para varios equipos use un
proxy HTTPS con certificado válido y la URL HTTPS del servidor; el cliente rechaza
HTTP remoto. No se declara instalado ese despliegue universitario.

## Recorrido para demostración

1. Coordinador: seleccionar hogar ficticio; asignar responsable; programar inicio
   y citas. Puede reasignar antes de preparar la captura. Una cita reserva una
   hora, como política propuesta, y no demuestra una visita realizada.
2. Encuestador: seleccionar hogar; **Preparar captura local** con conexión;
   registrar adquisiciones por fecha. Un día vacío permanece pendiente.
3. Para cada día sin adquisiciones, declarar expresamente su ausencia. Compras
   usan valor pagado; regalos y otras formas usan estimado. No hay datos cargados
   automáticamente como si fueran capturados por el usuario.
4. Una vez transcurridas las catorce fechas y sin pendientes, finalizar y confirmar.
   La edición local se cierra. El sistema envía automáticamente; si no hay red,
   conserva `Pendiente de envío`. Al recuperar conexión confirma la recepción.
5. Supervisor: antes del recibo, examinar programación y registrar acciones o
   novedades reportadas. Tras recibir, consultar datos. Un reporte no equivale a
   recepción y un plazo cumplido no demuestra finalización local.
6. Crítico: seleccionar adquisición o fecha en **Información recibida**, registrar
   observación, aclaración y evaluación; confirmar un resultado compatible. La
   cantidad y el valor originales permanecen iguales.

La prueba Oracle utiliza fechas retrospectivas ficticias para ejecutar el ciclo
completo sin esperar catorce días. En el cliente normal no se permite capturar una
fecha futura ni finalizar anticipadamente.

## Documentación y estado académico

Consulte [manual técnico](docs/Manual-tecnico.md), [trazabilidad y evidencia](docs/Trazabilidad-y-evidencia.md),
[decisiones pendientes](docs/DEC-041-y-pendientes.md) y [pruebas](docs/Verificacion.md).
El código materializa una propuesta revisable; las decisiones técnicas nuevas
no se presentan como aprobadas por el equipo ni por los docentes.

