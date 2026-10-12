package co.edu.udi.enph;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor central con HTTP del JDK y Oracle. Escucha solo en localhost: para
 * acceso remoto se requiere un proxy HTTPS autenticado con certificado válido.
 * Los tokens secretos se configuran fuera del repositorio; se relacionan con un
 * usuario habilitado en Oracle. No permite elegir arbitrariamente un rol.
 */
public final class Server {
    @FunctionalInterface public interface Backend {
        List<List<String>> execute(String user,List<List<String>> request) throws Exception;
    }
    private final HttpServer http;
    private final ExecutorService workers=Executors.newFixedThreadPool(8);
    public Server(int port,Map<String,String> tokens,Backend backend) throws Exception {
        Domain.require(!tokens.isEmpty(),"Configure usuarios y tokens antes de iniciar.");
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",port),32); http.setExecutor(workers);
        http.createContext("/rpc",exchange -> {
            try {
                Domain.require(exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/rpc"),"Ruta o método inválido.");
                String authorization=exchange.getRequestHeaders().getFirst("Authorization"); String actor=null;
                if(authorization!=null && authorization.startsWith("Bearer ")) {
                    byte[] candidate=authorization.substring(7).getBytes(StandardCharsets.UTF_8);
                    for(var entry:tokens.entrySet()) if(MessageDigest.isEqual(candidate,entry.getValue().getBytes(StandardCharsets.UTF_8))) actor=entry.getKey();
                }
                if(actor==null) { reply(exchange,401,List.of(List.of("Sesión no autorizada."))); return; }
                byte[] bytes=exchange.getRequestBody().readNBytes(Wire.MAX_BYTES+1);
                var response=backend.execute(actor,Wire.decode(bytes)); reply(exchange,200,response);
            } catch(IllegalArgumentException | java.io.EOFException e) {
                reply(exchange,400,List.of(List.of(e.getMessage()==null?"Solicitud inválida.":e.getMessage())));
            } catch(Exception e) {
                // No se envían sentencias, claves o detalles de conexión al cliente.
                System.err.println("Solicitud fallida: "+e.getClass().getSimpleName());
                reply(exchange,503,List.of(List.of("No se confirmó la operación. Revise el servidor y reintente; la captura local se conserva.")));
            } finally { exchange.close(); }
        });
    }
    private static void reply(HttpExchange e,int code,List<List<String>> rows) throws java.io.IOException {
        byte[] bytes=Wire.encode(rows); e.getResponseHeaders().set("Content-Type","application/octet-stream");
        e.sendResponseHeaders(code,bytes.length); e.getResponseBody().write(bytes);
    }
    public void start() { http.start(); }
    public int port() { return http.getAddress().getPort(); }
    public void stop() { http.stop(0); workers.shutdownNow(); }
    static String env(String name) {
        return Domain.text(System.getenv(name),2048,"Variable "+name);
    }
    public static void main(String[] args) throws Exception {
        Properties properties=new Properties();
        try(var reader=Files.newBufferedReader(Path.of(env("ENPH_TOKENS_FILE")),StandardCharsets.UTF_8)) { properties.load(reader); }
        Map<String,String> tokens=new HashMap<>();
        for(String id:properties.stringPropertyNames()) {
            UUID.fromString(id); String token=properties.getProperty(id); Domain.require(token.length()>=32,"Cada token necesita al menos 32 caracteres.");
            Domain.require(!tokens.containsValue(token),"Tokens repetidos."); tokens.put(id,token);
        }
        var repository=new OracleRepository(env("ENPH_DB_URL"),env("ENPH_DB_USER"),env("ENPH_DB_PASSWORD"),Clock.system(Domain.ZONE));
        // Verifica acceso y esquema antes de anunciar servidor listo.
        repository.execute(tokens.keySet().iterator().next(),List.of(List.of("ME")));
        int port=Integer.parseInt(System.getenv().getOrDefault("ENPH_PORT","8080"));
        var server=new Server(port,tokens,repository::execute); server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Servidor ENPH listo en 127.0.0.1:"+server.port()+". Persistencia central: Oracle.");
    }
}
