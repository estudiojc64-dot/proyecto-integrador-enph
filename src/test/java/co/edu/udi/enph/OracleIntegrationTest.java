package co.edu.udi.enph;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static co.edu.udi.enph.Domain.*;

/**
 * Prueba REAL opcional del servidor contra un esquema Oracle dedicado con
 * DbSetup instalado. Deja un hogar marcado PRUEBA para inspeccionar evidencias.
 * No borra datos ni opera sobre hogares del ejemplo o datos de usuarios.
 * Requiere ENPH_DB_URL, ENPH_DB_USER y ENPH_DB_PASSWORD. No hay sustituto en memoria.
 */
public final class OracleIntegrationTest {
    private static List<List<String>> request(String command,String... args) {return List.of(List.of(command),List.of(args));}
    private static void rejected(OracleRepository r,String actor,List<List<String>> request) throws Exception {
        try{r.execute(actor,request);throw new AssertionError("Oracle aceptó operación inválida.");}catch(IllegalArgumentException expected){ }
    }
    public static void main(String[] args) throws Exception {
        String url=Server.env("ENPH_DB_URL"),user=Server.env("ENPH_DB_USER"),password=Server.env("ENPH_DB_PASSWORD");
        String p=id(),household=id(),device=id(); Clock clock=Clock.system(ZONE);
        var repository=new OracleRepository(url,user,password,clock);
        try(Connection c=DriverManager.getConnection(url,user,password)) {
            OracleRepository.update(c,"INSERT INTO hogar VALUES(?,?)",household,"PRUEBA INTEGRACIÓN "+p);
            OracleRepository.update(c,"INSERT INTO periodo(id,hogar_id) VALUES(?,?)",p,household);
        }
        repository.execute(DbSetup.COORDINATOR,request("ASSIGN",p,DbSetup.SUBSTITUTE,""));
        repository.execute(DbSetup.COORDINATOR,request("ASSIGN",p,DbSetup.INTERVIEWER,"Continuidad antes de captura"));
        LocalDate start=LocalDate.now(clock).minusDays(13);
        repository.execute(DbSetup.COORDINATOR,request("PROGRAM",p,start.toString(),"Prueba retrospectiva sin citas"));
        rejected(repository,DbSetup.CRITIC,request("DATA",p));
        rejected(repository,DbSetup.CRITIC,request("ASSIGN",p,DbSetup.SUBSTITUTE,"Sin permiso"));
        repository.execute(DbSetup.SUPERVISOR,request("REPORT",p,"FINALIZACION_REPORTADA","Reporte ficticio","Prueba"));
        rejected(repository,DbSetup.CRITIC,request("DATA",p));
        String action=repository.execute(DbSetup.SUPERVISOR,request("ACTION",p,"Verificar recepción","Prueba",DbSetup.INTERVIEWER,clock.instant().plusSeconds(86400).toString())).get(0).get(0);
        repository.execute(DbSetup.SUPERVISOR,request("FOLLOW",p,action,"ATENDIDA","Se documentó la llamada ficticia"));
        var local=LocalStore.from(repository.execute(DbSetup.INTERVIEWER,request("CHECKOUT",p,device)));
        rejected(repository,DbSetup.COORDINATOR,request("ASSIGN",p,DbSetup.SUBSTITUTE,"No debe perder captura"));
        rejected(repository,DbSetup.COORDINATOR,request("PROGRAM",p,start.plusDays(1).toString(),"No debe cambiar fechas"));
        var a=new Acquisition(id(),start,"Leche pasteurizada en caja",BigDecimal.ONE,"2","",null,"","1","","6","","3",
            new BigDecimal("48000"),"1",Category.ALIMENTO_HOGAR,"","",DbSetup.INTERVIEWER,clock.instant());
        local.add(a,clock);for(int i=1;i<14;i++)local.absent(start.plusDays(i),clock);local.finish(clock);
        var transfer=new ArrayList<List<String>>();transfer.add(List.of("RECEIVE"));transfer.addAll(LocalStore.rows(local));
        String first=repository.execute(DbSetup.INTERVIEWER,transfer).get(0).get(0);
        String retry=repository.execute(DbSetup.INTERVIEWER,transfer).get(0).get(0);
        ProjectTests.check(first.equals(retry),"Reintento produjo otro recibo.");
        var altered=new ArrayList<List<String>>(transfer);var alteredRow=new ArrayList<String>(altered.get(2));
        alteredRow.set(14,"49000");altered.set(2,alteredRow);
        rejected(repository,DbSetup.INTERVIEWER,altered);
        repository.execute(DbSetup.CRITIC,request("OBSERVE",p,"ADQUISICION",a.id(),"Verificar valor de 48000"));
        var info=repository.execute(DbSetup.CRITIC,request("INFO",p));String obs=info.stream().filter(r->r.get(0).equals("Observación")).findFirst().orElseThrow().get(1);
        rejected(repository,DbSetup.CRITIC,request("REVIEW",p,"D completo","REVISADO_SIN_OBSERVACIONES"));
        repository.execute(DbSetup.CRITIC,request("CLARIFY",p,obs,"El informante confirma el valor","Informante ficticio"));
        repository.execute(DbSetup.CRITIC,request("EVALUATE",p,obs,"OBSERVACION_PERSISTE","El dato recibido conserva el valor"));
        repository.execute(DbSetup.CRITIC,request("REVIEW",p,"D completo","REVISADO_CON_OBSERVACIONES"));
        try(Connection c=DriverManager.getConnection(url,user,password)) {
            ProjectTests.check(OracleRepository.one(c,"SELECT COUNT(*) FROM recepcion WHERE periodo_id=?",p).get(0).equals("1"),"Recibo duplicado.");
            ProjectTests.check(OracleRepository.one(c,"SELECT COUNT(*) FROM ausencia WHERE periodo_id=?",p).get(0).equals("13"),"Ausencias perdidas.");
            ProjectTests.check(new BigDecimal(OracleRepository.one(c,"SELECT valor FROM adquisicion WHERE id=?",a.id()).get(0)).compareTo(new BigDecimal("48000"))==0,"La revisión cambió el valor.");
            try{OracleRepository.update(c,"UPDATE adquisicion SET valor=1 WHERE id=?",a.id());throw new AssertionError("El disparador permitió alterar gasto recibido.");}
                catch(SQLException expected){ProjectTests.check(expected.getErrorCode()==20001,"Falló por una causa distinta de inmutabilidad.");}
        }
        // Fuerza un fallo después de insertar un renglón válido: debe revertirse TODO.
        String second=id();try(Connection c=DriverManager.getConnection(url,user,password)) {
            OracleRepository.update(c,"INSERT INTO periodo(id,hogar_id) VALUES(?,?)",second,household);
        }
        repository.execute(DbSetup.COORDINATOR,request("ASSIGN",second,DbSetup.INTERVIEWER,""));
        repository.execute(DbSetup.COORDINATOR,request("PROGRAM",second,start.toString(),"Prueba de rollback"));
        var secondLocal=LocalStore.from(repository.execute(DbSetup.INTERVIEWER,request("CHECKOUT",second,id())));
        var newAcquisition=new Acquisition(id(),start,"Banano",BigDecimal.ONE,"6","",null,"","1","","6","","3",
            new BigDecimal("4500"),"1",Category.ALIMENTO_HOGAR,"","",DbSetup.INTERVIEWER,clock.instant());
        secondLocal.add(newAcquisition,clock);secondLocal.add(a,clock);
        for(int i=1;i<14;i++)secondLocal.absent(start.plusDays(i),clock);secondLocal.finish(clock);
        var failedTransfer=new ArrayList<List<String>>();failedTransfer.add(List.of("RECEIVE"));failedTransfer.addAll(LocalStore.rows(secondLocal));
        try{repository.execute(DbSetup.INTERVIEWER,failedTransfer);throw new AssertionError("Se admitió identidad ya recibida en otro período.");}
            catch(SQLException expected){ProjectTests.check(expected.getErrorCode()==1,"El fallo no fue por identidad duplicada.");}
        try(Connection c=DriverManager.getConnection(url,user,password)) {
            ProjectTests.check(OracleRepository.one(c,"SELECT COUNT(*) FROM adquisicion WHERE periodo_id=?",second).get(0).equals("0"),"Quedó una recepción parcial.");
            ProjectTests.check(OracleRepository.one(c,"SELECT COUNT(*) FROM recepcion WHERE periodo_id=?",second).get(0).equals("0"),"Se confirmó una recepción fallida.");
        }
        // Verifica el recorrido HTTP -> permisos -> Oracle -> respuesta real.
        String token="prueba-temporal-"+id();var server=new Server(0,Map.of(DbSetup.CRITIC,token),repository::execute);server.start();
        try{
            var client=new ApiClient("http://127.0.0.1:"+server.port()+"/rpc",token);
            var data=client.call("DATA",p);ProjectTests.check(data.size()==14,"El HTTP real no devolvió el conjunto recibido.");
            ProjectTests.check(client.call("ME").get(0).get(2).equals("CRITICO"),"El HTTP no obtuvo el rol de Oracle.");
        }finally{server.stop();}
        System.out.println("ORACLE OK: asignación, historial, calendario, supervisión, captura, recepción idempotente, revisión sin edición y disparador. Período PRUEBA: "+p);
    }
}
