# DEC-041 - Implementación propuesta del punto 12

Fecha de referencia del trabajo: 11 de octubre de 2026, America/Bogota.
**Estado: propuesta técnica implementada para revisión; no aprobada automáticamente
por el equipo ni por el docente.** La autorización de generar/publicar código y
de crear el esquema de prueba no equivale a aprobar todas sus decisiones de diseño.

## Qué se propone

Java con nivel de compilación 17, JavaFX 17.0.16 en Windows x64, servidor HTTP
incluido en el JDK, JDBC Oracle, protocolo binario explícito, archivos locales con
reemplazo atómico y dispositivo exclusivo por período. No se agrega framework
servidor ni sistema de construcción Maven/Gradle. Se proponen UUID estables,
permisos por rol, un rol operativo por referencia ficticia, citas de una hora,
historial numerado de programación y una revisión retomable con hallazgos.

## Motivo y alternativas

La petición autoriza producir una implementación concreta. El contexto no cierra
la tecnología local ni el mecanismo de transferencia. Los archivos explícitos
permiten compilar sin un motor local adicional, pero carecen de consultas SQL y
cifrado propio. Una BD local ofrecería transacciones y consultas con dependencias
adicionales; requiere elección y verificación de plataforma antes de adoptarse.
JDBC permite la conexión Java-Oracle requerida; un framework servidor ofrece más
servicios a costa de configuración y dependencias que no están confirmadas.

La captura exclusiva congela cambios desconocidos del calendario y reasignación
operativa. Se prefirió conservar datos y advertir el pendiente, en vez de fingir
una transferencia inexistente. Una transferencia verificable entre dispositivos
debe diseñarse con aceptación y autoría conservada; no se implementa ni se declara
aprobada en esta versión.

La categoría explícita facilita reglas de aplicabilidad, pero requiere una matriz
aceptada para servicios, mesadas y productos excluidos. Un catálogo de artículos
autorizados sería otra alternativa y no existe como decisión aprobada. No se
introduce silenciosamente ese catálogo ni una clasificación automática de texto.

## Impacto y elementos afectados

CU-02/03/06/09/10, RF-02/03/04/07/10/11, RNF-01, prototipo funcional, clases,
dominio, objetos, relacional, diccionario, Oracle, pruebas, plan y manuales.
Este código no convierte las dependencias de información DEC-040 en llamadas
de software ni añade los CU del alcance general.

| Pendiente previo | Tratamiento de esta versión | Qué falta para cerrar |
|---|---|---|
| PD-01 UML/BD | Separación de reglas, persistencia y UI documentada | Contraste con dominio, objetos y clases reales en Astah |
| PD-02 Local/plataforma | Archivos locales, protocolo y JavaFX Windows verificados técnicamente | Aprobar mecanismo; verificar tableta real |
| PD-03 Reasignación con pendientes | Reasignación bloqueada después de entregar captura | Diseñar transferencia; revisar CU/prototipo/modelos/pruebas |
| PD-04 Fechas desconocidas | Reprogramación bloqueada después de entregar captura | Decidir continuidad y aceptación del bloqueo |
| PD-05 Permisos/disponibilidad | Roles consultados en Oracle; encuestador solo sus períodos; citas de una hora | Aprobar permisos centrales por hogar y disponibilidad real |
| PD-06 Aplicabilidad D | Categoría explícita; persona para mesada/bolsillo; menor <10; lugar opcional en servicios personales | Aprobar matriz; verificar clasificación de productos/servicios |
| PD-07 Acciones/revisión | Plazo nuevo futuro, estados documentados y evaluación separada | Aprobar dominios y criterios de resultado |
| PD-08 Oracle/tipos | Oracle 21c XE real y tipos probados | Confirmar Oracle universitario y revisar dimensiones definitivas |
| PD-09 Visitas realizadas | No se marcan citas como realizadas; historial conservado | Precisar CU-03 o fuente externa verificable |

## DEC-042 - Publicación solicitada

- Qué se decidió: crear repositorio público `estudiojc64-dot/proyecto-integrador-enph`
  y subir el código documentado del punto 12.
- Estado: **autorizado expresamente por el usuario** mediante respuesta a la pregunta
  de cuenta y repositorio en esta conversación.
- Motivo: requisito académico de URL pública y petición directa del usuario.
- Impacto: publicación de código, SQL, scripts y documentación; se excluyen fuentes
  originales, credenciales, capturas locales, datos reales y bibliotecas binarias.
- Elementos afectados: entrega del punto 12 y registro de publicación.

## DEC-043 - Esquema Oracle de prueba

- Qué se decidió: crear `ENPH_P12_TEST` exclusivamente en XEPDB1, con permisos
  limitados, cuota de cuarenta MB y datos ficticios, guardando su contraseña
  aleatoria únicamente en `.runtime-test/oracle-test.json` local.
- Estado: **autorizado expresamente por el usuario**, después del rechazo de la
  revisión automática a la creación inicial por falta de autorización específica.
- Motivo: ejecutar pruebas de integración reales sin modificar tablas existentes.
- Impacto: cuenta y datos persistentes de prueba, disponibles para inspección;
  ningún secreto se incluye en repositorio o ZIP de entrega.
- Elementos afectados: instalación de prueba, evidencia Oracle y verificación.
- Ciclo de vida: se conserva para revisión conforme a la autorización; esta entrega
  no borra el esquema ni modifica otras cuentas. Su eliminación requiere instrucción.

La creación autorizada pasó y se ejecutaron las pruebas. No se presenta como una
decisión de despliegue universitario ni como aprobación institucional del diseño.

