package co.edu.udi.enph;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static co.edu.udi.enph.Domain.*;

/**
 * Escenarios de aceptación ejecutables sin Oracle ni bibliotecas de pruebas.
 * Prueban las reglas, la recuperación local y el contrato HTTP autenticado.
 * El backend de la prueba HTTP es un doble explícito; no acredita Oracle.
 */
public final class ProjectTests {
    private static int passed;
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-11T20:00:00Z"),ZONE);
    private static final String AUTHOR=DbSetup.INTERVIEWER;
    interface Work {void run() throws Exception;}
    static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    static void rejected(Work work) throws Exception {
        try{work.run();throw new AssertionError("Se aceptó una operación inválida.");}catch(IllegalArgumentException|IOException expected){ }
    }
    static void test(String name,Work work) throws Exception {work.run();passed++;System.out.println("OK "+name);}
    static LocalPeriod period() {return new LocalPeriod(id(),"Hogar ficticio",AUTHOR,id(),LocalDate.parse("2026-09-28"));}
    static Acquisition acquisition(LocalDate date,String form,String unit,String other,String frequency,String category,String age,BigDecimal value) {
        return new Acquisition(id(),date,"Leche pasteurizada en caja",BigDecimal.ONE,unit,other,null,"",form,"",
            form.equals("1")?"6":"","",frequency,value,category.equals("ALIMENTO_HOGAR")?"1":"2",Category.valueOf(category),
            category.equals("ALIMENTO_HOGAR")?"":"Persona ficticia",age,AUTHOR,CLOCK.instant());
    }
    static LocalPeriod completed() {
        var p=period();p.add(acquisition(p.start,"1","2","","3","ALIMENTO_HOGAR","",new BigDecimal("48000")),CLOCK);
        for(int i=1;i<14;i++)p.absent(p.start.plusDays(i),CLOCK);p.finish(CLOCK);return p;
    }
    public static void main(String[] args) throws Exception {
        test("14 fechas contando inicio y fin",()->check(period().end().equals(LocalDate.parse("2026-10-11")),"Fin incorrecto."));
        test("Un día vacío bloquea finalización",()->rejected(()->period().finish(CLOCK)));
        test("Fecha futura y fecha fuera del período",()->{var p=period();rejected(()->p.absent(p.end().plusDays(1),CLOCK));
            var early=Clock.fixed(Instant.parse("2026-09-28T20:00:00Z"),ZONE);rejected(()->p.absent(p.start.plusDays(1),early));rejected(()->p.finish(early));});
        test("Regalo usa estimado; no implica ausencia",()->{var p=period();var a=acquisition(p.start,"3","6","","","ALIMENTO_HOGAR","",new BigDecimal("3500"));
            p.add(a,CLOCK);check(!a.paid(),"Regalo contado como pagado.");rejected(()->p.absent(p.start,CLOCK));});
        test("No se mezcla ausencia con gasto; retiro explícito",()->{var p=period();p.absent(p.start,CLOCK);var a=acquisition(p.start,"1","2","","2.1","ALIMENTO_HOGAR","",BigDecimal.ZERO);
            rejected(()->p.add(a,CLOCK));p.undoAbsent(p.start);p.add(a,CLOCK);check(p.acquisitions.size()==1,"No se guardó gasto.");});
        test("Otra unidad exige especificación",()->rejected(()->acquisition(period().start,"1","8","","3","ALIMENTO_HOGAR","",BigDecimal.ONE)));
        test("Menor de 8 años en día 14 según adaptación aprobada",()->{var p=period();p.add(acquisition(p.end(),"1","8","Viaje","2","BOLSILLO_MENOR","8",new BigDecimal("3000")),CLOCK);
            rejected(()->acquisition(p.end(),"1","8","Viaje","2","BOLSILLO_MENOR","10",BigDecimal.ONE));});
        test("D1 y categoría excluida se rechazan",()->{rejected(()->acquisition(period().start,"1","6","","3","EXCLUIDO_D","",BigDecimal.ONE));
            rejected(()->acquisition(period().start,"1","6","","3","COMIDA_FUERA_D1","",BigDecimal.ONE));});
        test("Valor negativo y exceso de precisión",()->{rejected(()->acquisition(period().start,"1","2","","3","ALIMENTO_HOGAR","",new BigDecimal("-1")));
            rejected(()->acquisition(period().start,"1","2","","3","ALIMENTO_HOGAR","",new BigDecimal("1.001")));});
        test("Finalización bloquea captura, eliminación y ausencia",()->{var p=completed();rejected(()->p.remove(p.acquisitions.get(0).id()));
            rejected(()->p.undoAbsent(p.start.plusDays(1)));rejected(()->p.add(p.acquisitions.get(0),CLOCK));check(p.receivedAt==null,"Finalizar simuló recepción.");});
        test("Mismo nombre permite dos adquisiciones distintas",()->{var p=period();p.add(acquisition(p.start,"1","2","","3","ALIMENTO_HOGAR","",BigDecimal.ONE),CLOCK);
            p.add(acquisition(p.start,"1","2","","3","ALIMENTO_HOGAR","",BigDecimal.TEN),CLOCK);check(p.acquisitions.size()==2,"Deduplicación por nombre indebida.");});
        test("Recuperación de archivo, recibo y bloqueo de segunda instancia",()->{
            Path directory=Files.createTempDirectory("enph-test-");var p=completed();
            try(var storage=new LocalStore(directory)){storage.save(p);rejected(()->{try(var duplicate=new LocalStore(directory)){ }});
                var copy=storage.load(p.id);check(copy.acquisitions.get(0).value().compareTo(new BigDecimal("48000"))==0,"Valor cambió al leer.");
                copy.receivedAt=CLOCK.instant();storage.save(copy);check(storage.load(p.id).receivedAt!=null,"Recibo no conservado.");}
            try(var storage=new LocalStore(directory)){check(storage.all().size()==1,"Recuperación incorrecta.");}
            // Elimina únicamente los archivos temporales de esta prueba.
            try(var files=Files.newDirectoryStream(directory)){for(Path file:files)Files.delete(file);}Files.delete(directory);
        });
        test("Protocolo preserva campos Unicode y detecta truncamiento",()->{var rows=LocalStore.rows(completed());byte[] bytes=Wire.encode(rows);
            check(Wire.decode(bytes).equals(rows),"Contrato no conserva campos.");rejected(()->Wire.decode(Arrays.copyOf(bytes,bytes.length-1)));});
        test("LocalStore rechaza identidades y renglones duplicados",()->{var rows=new ArrayList<>(LocalStore.rows(completed()));rows.add(rows.get(1));rejected(()->LocalStore.from(rows));});
        test("HTTP autentica usuario; rechazo no registra operación",()->{
            int[] calls={0};var server=new Server(0,Map.of(AUTHOR,"token-ficticio-prueba-32-caracteres"),(user,rows)->{
                check(user.equals(AUTHOR),"Usuario distinto del token.");calls[0]++;return List.of(List.of("confirmado"));});
            server.start();try {String url="http://127.0.0.1:"+server.port()+"/rpc";
                var good=new ApiClient(url,"token-ficticio-prueba-32-caracteres");check(good.call("ME").get(0).get(0).equals("confirmado"),"Respuesta incorrecta.");
                rejected(()->new ApiClient(url,"token-incorrecto").call("ME"));check(calls[0]==1,"Petición no autorizada invocó persistencia.");
            }finally{server.stop();}
        });
        test("Credenciales por HTTP remoto se rechazan",()->rejected(()->new ApiClient("http://example.org/rpc","token-ficticio")));
        System.out.println("RESULTADO: "+passed+" escenarios aprobados; Oracle no forma parte de esta ejecución.");
    }
}
