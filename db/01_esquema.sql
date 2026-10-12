-- PUNTO 12 - Esquema físico propuesto DEC-041. Oracle 21c o compatible.
-- Ejecutar exclusivamente en un esquema NUEVO dedicado a este proyecto.
-- No crea usuarios Oracle ni modifica tablas existentes. No contiene DROP.
-- El fallo detiene SQL*Plus: revisar el mensaje antes de volver a ejecutar.
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET DEFINE OFF

CREATE TABLE usuario (
 id VARCHAR2(36 CHAR) PRIMARY KEY, nombre VARCHAR2(120 CHAR) NOT NULL,
 rol VARCHAR2(20 CHAR) NOT NULL CHECK (rol IN ('COORDINADOR','ENCUESTADOR','SUPERVISOR','CRITICO')),
 habilitado CHAR(1) DEFAULT 'S' NOT NULL CHECK (habilitado IN ('S','N'))
);
CREATE TABLE hogar (id VARCHAR2(36 CHAR) PRIMARY KEY, nombre VARCHAR2(160 CHAR) NOT NULL);
CREATE TABLE periodo (
 id VARCHAR2(36 CHAR) PRIMARY KEY, hogar_id VARCHAR2(36 CHAR) NOT NULL REFERENCES hogar(id),
 inicio DATE, dispositivo VARCHAR2(36 CHAR),
 CHECK (inicio IS NULL OR inicio=TRUNC(inicio))
);
-- Una fila abierta por período. El histórico conserva responsable y autor.
CREATE TABLE asignacion (
 id VARCHAR2(36 CHAR) PRIMARY KEY, periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES periodo(id),
 encuestador_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id),
 desde TIMESTAMP WITH TIME ZONE NOT NULL, hasta TIMESTAMP WITH TIME ZONE,
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), motivo VARCHAR2(500 CHAR),
 CHECK (hasta IS NULL OR hasta>=desde)
);
CREATE UNIQUE INDEX una_asignacion_abierta ON asignacion(CASE WHEN hasta IS NULL THEN periodo_id END);
CREATE TABLE programacion (
 id VARCHAR2(36 CHAR) PRIMARY KEY, periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES periodo(id),
 numero NUMBER(6,0) NOT NULL CHECK (numero>0),
 inicio DATE NOT NULL, autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id),
 registrada_en TIMESTAMP WITH TIME ZONE NOT NULL, motivo VARCHAR2(500 CHAR),
 UNIQUE(periodo_id,numero),
 CHECK (inicio=TRUNC(inicio))
);
CREATE TABLE visita_prevista (
 id VARCHAR2(36 CHAR) PRIMARY KEY, programacion_id VARCHAR2(36 CHAR) NOT NULL REFERENCES programacion(id),
 asignacion_id VARCHAR2(36 CHAR) NOT NULL REFERENCES asignacion(id), fecha TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Catálogos: códigos del instrumento como texto, incluido 2.1.
CREATE TABLE unidad (codigo VARCHAR2(4 CHAR) PRIMARY KEY, nombre VARCHAR2(100 CHAR) NOT NULL);
CREATE TABLE forma (codigo VARCHAR2(4 CHAR) PRIMARY KEY, nombre VARCHAR2(160 CHAR) NOT NULL);
CREATE TABLE lugar (codigo VARCHAR2(4 CHAR) PRIMARY KEY, nombre VARCHAR2(240 CHAR) NOT NULL);
CREATE TABLE frecuencia (codigo VARCHAR2(4 CHAR) PRIMARY KEY, nombre VARCHAR2(100 CHAR) NOT NULL);
CREATE TABLE clase_gasto (codigo VARCHAR2(4 CHAR) PRIMARY KEY, nombre VARCHAR2(60 CHAR) NOT NULL);
CREATE TABLE adquisicion (
 id VARCHAR2(36 CHAR) PRIMARY KEY, periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES periodo(id),
 fecha DATE NOT NULL, descripcion VARCHAR2(240 CHAR) NOT NULL,
 cantidad NUMBER(12,3) NOT NULL CHECK (cantidad>0), unidad VARCHAR2(4 CHAR) NOT NULL REFERENCES unidad(codigo),
 unidad_otra VARCHAR2(100 CHAR), equivalencia NUMBER(12,3) CHECK (equivalencia>0), equivalencia_unidad VARCHAR2(2 CHAR),
 forma VARCHAR2(4 CHAR) NOT NULL REFERENCES forma(codigo), forma_otra VARCHAR2(160 CHAR),
 lugar VARCHAR2(4 CHAR) REFERENCES lugar(codigo), lugar_otro VARCHAR2(240 CHAR),
 frecuencia VARCHAR2(4 CHAR) REFERENCES frecuencia(codigo), valor NUMBER(14,2) NOT NULL CHECK (valor>=0),
 clase VARCHAR2(4 CHAR) NOT NULL REFERENCES clase_gasto(codigo), categoria VARCHAR2(30 CHAR) NOT NULL,
 persona VARCHAR2(120 CHAR), edad NUMBER(2,0), autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id),
 registrada_en TIMESTAMP WITH TIME ZONE NOT NULL,
 CHECK (fecha=TRUNC(fecha)),
 CHECK ((unidad='8' AND unidad_otra IS NOT NULL) OR (unidad<>'8' AND unidad_otra IS NULL)),
 CHECK ((forma='7' AND forma_otra IS NOT NULL) OR (forma<>'7' AND forma_otra IS NULL)),
 CHECK ((equivalencia IS NULL AND equivalencia_unidad IS NULL) OR
        (equivalencia IS NOT NULL AND equivalencia_unidad IS NOT NULL AND unidad='8' AND equivalencia_unidad IN ('G','CC'))),
 CHECK ((forma='1' AND frecuencia IS NOT NULL) OR (forma<>'1' AND lugar IS NULL AND frecuencia IS NULL)),
 CHECK ((lugar='24' AND lugar_otro IS NOT NULL) OR ((lugar IS NULL OR lugar<>'24') AND lugar_otro IS NULL)),
 CHECK (categoria IN ('ALIMENTO_HOGAR','BOLSILLO_RESPONSABLE','BOLSILLO_MENOR','MESADA')),
 CHECK ((categoria='ALIMENTO_HOGAR' AND clase='1') OR (categoria<>'ALIMENTO_HOGAR' AND persona IS NOT NULL)),
 CHECK (categoria NOT IN ('BOLSILLO_RESPONSABLE','BOLSILLO_MENOR') OR clase='2'),
 CHECK ((categoria='BOLSILLO_MENOR' AND edad IS NOT NULL AND edad BETWEEN 0 AND 9) OR (categoria<>'BOLSILLO_MENOR' AND edad IS NULL)),
 CHECK (categoria<>'ALIMENTO_HOGAR' OR forma<>'1' OR lugar IS NOT NULL)
);
CREATE INDEX adquisicion_periodo_fecha ON adquisicion(periodo_id,fecha);
CREATE TABLE ausencia (
 periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES periodo(id), fecha DATE NOT NULL,
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), registrada_en TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(periodo_id,fecha), CHECK (fecha=TRUNC(fecha))
);
CREATE TABLE recepcion (
 periodo_id VARCHAR2(36 CHAR) PRIMARY KEY REFERENCES periodo(id), huella VARCHAR2(64 CHAR) NOT NULL,
 finalizada_en TIMESTAMP WITH TIME ZONE NOT NULL, confirmada_en TIMESTAMP WITH TIME ZONE NOT NULL,
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id)
);
CREATE TABLE accion (
 id VARCHAR2(36 CHAR) PRIMARY KEY, periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES periodo(id),
 descripcion VARCHAR2(500 CHAR) NOT NULL, motivo VARCHAR2(500 CHAR) NOT NULL,
 responsable_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), plazo TIMESTAMP WITH TIME ZONE NOT NULL,
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), registrada_en TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE seguimiento (
 id VARCHAR2(36 CHAR) PRIMARY KEY, accion_id VARCHAR2(36 CHAR) NOT NULL REFERENCES accion(id),
 numero NUMBER(6,0) NOT NULL, estado VARCHAR2(20 CHAR) NOT NULL CHECK (estado IN ('ABIERTA','EN_SEGUIMIENTO','ATENDIDA')),
 texto VARCHAR2(800 CHAR) NOT NULL, autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id),
 registrada_en TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(accion_id,numero)
);
CREATE TABLE novedad (
 id VARCHAR2(36 CHAR) PRIMARY KEY, periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES periodo(id),
 tipo VARCHAR2(24 CHAR) NOT NULL CHECK (tipo IN ('FINALIZACION_REPORTADA','OTRA')),
 texto VARCHAR2(800 CHAR) NOT NULL, origen VARCHAR2(180 CHAR) NOT NULL,
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), registrada_en TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE revision (
 periodo_id VARCHAR2(36 CHAR) PRIMARY KEY REFERENCES recepcion(periodo_id),
 alcance VARCHAR2(180 CHAR) NOT NULL, resultado VARCHAR2(32 CHAR) NOT NULL
 CHECK (resultado IN ('EN_CURSO','PENDIENTE_ACLARACION','REVISADO_CON_OBSERVACIONES','REVISADO_SIN_OBSERVACIONES')),
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), registrada_en TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE observacion (
 id VARCHAR2(36 CHAR) PRIMARY KEY, periodo_id VARCHAR2(36 CHAR) NOT NULL REFERENCES revision(periodo_id),
 fecha DATE, adquisicion_id VARCHAR2(36 CHAR) REFERENCES adquisicion(id), texto VARCHAR2(800 CHAR) NOT NULL,
 estado VARCHAR2(24 CHAR) NOT NULL CHECK (estado IN ('PENDIENTE_ACLARACION','ACLARADA','OBSERVACION_PERSISTE')),
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), registrada_en TIMESTAMP WITH TIME ZONE NOT NULL,
 evaluacion VARCHAR2(800 CHAR), evaluador_id VARCHAR2(36 CHAR) REFERENCES usuario(id), evaluada_en TIMESTAMP WITH TIME ZONE,
 CHECK ((fecha IS NULL AND adquisicion_id IS NOT NULL) OR (fecha IS NOT NULL AND adquisicion_id IS NULL))
);
CREATE TABLE aclaracion (
 id VARCHAR2(36 CHAR) PRIMARY KEY, observacion_id VARCHAR2(36 CHAR) NOT NULL REFERENCES observacion(id),
 texto VARCHAR2(800 CHAR) NOT NULL, origen VARCHAR2(180 CHAR) NOT NULL,
 autor_id VARCHAR2(36 CHAR) NOT NULL REFERENCES usuario(id), registrada_en TIMESTAMP WITH TIME ZONE NOT NULL
);

-- La carga de gastos siempre precede al recibo, dentro de UNA transacción.
-- Tras el recibo se bloquean INSERT/UPDATE/DELETE incluso desde SQL directo.
CREATE OR REPLACE TRIGGER gastos_inmutables
BEFORE INSERT OR UPDATE OR DELETE ON adquisicion FOR EACH ROW
DECLARE n NUMBER; p VARCHAR2(36);
BEGIN
 p:=CASE WHEN INSERTING THEN :NEW.periodo_id ELSE :OLD.periodo_id END;
 SELECT COUNT(*) INTO n FROM recepcion WHERE periodo_id=p;
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20001,'Adquisiciones recibidas inmutables'); END IF;
 IF UPDATING THEN
   SELECT COUNT(*) INTO n FROM recepcion WHERE periodo_id=:NEW.periodo_id;
   IF n>0 THEN RAISE_APPLICATION_ERROR(-20001,'Destino recibido inmutable'); END IF;
 END IF;
END;
/
CREATE OR REPLACE TRIGGER ausencias_inmutables
BEFORE INSERT OR UPDATE OR DELETE ON ausencia FOR EACH ROW
DECLARE n NUMBER; p VARCHAR2(36);
BEGIN
 p:=CASE WHEN INSERTING THEN :NEW.periodo_id ELSE :OLD.periodo_id END;
 SELECT COUNT(*) INTO n FROM recepcion WHERE periodo_id=p;
 IF n>0 THEN RAISE_APPLICATION_ERROR(-20002,'Ausencias recibidas inmutables'); END IF;
 IF UPDATING THEN
   SELECT COUNT(*) INTO n FROM recepcion WHERE periodo_id=:NEW.periodo_id;
   IF n>0 THEN RAISE_APPLICATION_ERROR(-20002,'Destino recibido inmutable'); END IF;
 END IF;
END;
/
-- Las restricciones entre días, autores, vigencias y recepción íntegra también
-- se verifican en OracleRepository bajo bloqueo de la fila del período.
-- La cuenta de ejecución no debe tener privilegios DDL ni acceso de terceros.
COMMIT;
