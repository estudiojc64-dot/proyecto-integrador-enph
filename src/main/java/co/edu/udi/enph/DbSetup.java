package co.edu.udi.enph;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static co.edu.udi.enph.Domain.*;

/**
 * Instalador explícito en esquema Oracle dedicado. Solo se ejecuta por orden
 * del operador; el servidor nunca crea tablas al arrancar. El DDL Oracle hace
 * commits implícitos: un fallo exige revisar el esquema antes de reintentar.
 * Carga catálogos documentados y referencias enteramente ficticias.
 */
public final class DbSetup {
    public static final String COORDINATOR="00000000-0000-0000-0000-000000000001";
    public static final String INTERVIEWER="00000000-0000-0000-0000-000000000002";
    public static final String SUPERVISOR="00000000-0000-0000-0000-000000000003";
    public static final String CRITIC="00000000-0000-0000-0000-000000000004";
    public static final String SUBSTITUTE="00000000-0000-0000-0000-000000000005";
    public static void main(String[] args) throws Exception {
        require(args.length==1,"Indique db/01_esquema.sql.");
        try(Connection c=DriverManager.getConnection(Server.env("ENPH_DB_URL"),Server.env("ENPH_DB_USER"),Server.env("ENPH_DB_PASSWORD"))) {
            require(OracleRepository.one(c,"SELECT table_name FROM user_tables FETCH FIRST 1 ROWS ONLY")==null,
                "El esquema no está vacío. Se exige un esquema dedicado nuevo para evitar conflictos.");
            StringBuilder sql=new StringBuilder();boolean block=false;
            for(String line:Files.readAllLines(Path.of(args[0]))) {
                String trimmed=line.trim(); if(trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("WHENEVER ") || trimmed.startsWith("SET "))continue;
                if(trimmed.startsWith("CREATE OR REPLACE TRIGGER"))block=true;
                if(trimmed.equals("/")) {
                    execute(c,sql.toString());sql.setLength(0);block=false;continue;
                }
                sql.append(line).append('\n');
                if(!block && trimmed.endsWith(";")) {execute(c,sql.toString().trim().replaceFirst(";$",""));sql.setLength(0);}
            }
            require(sql.toString().isBlank(),"DDL incompleto.");
            require(OracleRepository.query(c,"SELECT name,text FROM user_errors").isEmpty(),"Oracle encontró errores en disparadores; consulte USER_ERRORS.");
            c.setAutoCommit(false);
            try {
                catalog(c,"unidad",UNITS);catalog(c,"forma",FORMS);catalog(c,"lugar",PLACES);catalog(c,"frecuencia",FREQUENCIES);catalog(c,"clase_gasto",CLASSES);
                user(c,COORDINATOR,"Ana · ejemplo ficticio",Role.COORDINADOR);user(c,INTERVIEWER,"Pedro · ejemplo ficticio",Role.ENCUESTADOR);
                user(c,SUPERVISOR,"Jorge · ejemplo ficticio",Role.SUPERVISOR);user(c,CRITIC,"Sofía · ejemplo ficticio",Role.CRITICO);
                user(c,SUBSTITUTE,"Laura · ejemplo ficticio",Role.ENCUESTADOR);
                for(int i=1;i<=3;i++) {
                    String household=String.format("10000000-0000-0000-0000-%012d",i),p=String.format("20000000-0000-0000-0000-%012d",i);
                    OracleRepository.update(c,"INSERT INTO hogar VALUES(?,?)",household,"Hogar académico ficticio "+i);
                    OracleRepository.update(c,"INSERT INTO periodo(id,hogar_id) VALUES(?,?)",p,household);
                }
                c.commit();
            } catch(Exception e){c.rollback();throw e;}
            System.out.println("Esquema y referencias ficticias instalados. Ningún período se declara capturado o recibido.");
        }
    }
    private static void catalog(Connection c,String table,List<Option> options) throws SQLException {
        for(var option:options)OracleRepository.update(c,"INSERT INTO "+table+" VALUES(?,?)",option.code(),option.name());
    }
    private static void execute(Connection c,String sql) throws SQLException {
        try(Statement statement=c.createStatement()) { statement.execute(sql); }
    }
    private static void user(Connection c,String id,String name,Role role) throws SQLException {
        OracleRepository.update(c,"INSERT INTO usuario(id,nombre,rol) VALUES(?,?,?)",id,name,role.name());
    }
}
