package co.edu.udi.enph;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Cliente del servidor: tiempo máximo de espera, token externo y HTTPS remoto. */
public final class ApiClient {
    private final URI endpoint;
    private final String token;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public ApiClient(String url,String token) {
        endpoint=URI.create(url); this.token=Domain.text(token,2048,"Token");
        Domain.require(endpoint.getUserInfo()==null && endpoint.getQuery()==null && endpoint.getFragment()==null,"URL inválida.");
        boolean local=Set.of("127.0.0.1","localhost","[::1]").contains(Objects.toString(endpoint.getHost(),""));
        Domain.require(endpoint.getScheme().equals("https") || (endpoint.getScheme().equals("http") && local),"Use HTTPS fuera del equipo local.");
    }
    public List<List<String>> call(String command,String... args) throws Exception {
        return callRows(command,args.length==0?List.of():List.of(List.of(args)));
    }
    public List<List<String>> callRows(String command,List<List<String>> payload) throws Exception {
        List<List<String>> rows=new ArrayList<>(); rows.add(List.of(command)); rows.addAll(payload);
        var req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
            .header("Authorization","Bearer "+token).header("Content-Type","application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(Wire.encode(rows))).build();
        var response=http.send(req,HttpResponse.BodyHandlers.ofInputStream());
        List<List<String>> result;
        try(var body=response.body()) { result=Wire.decode(body.readNBytes(Wire.MAX_BYTES+1)); }
        if(response.statusCode()!=200) throw new IllegalArgumentException(result.isEmpty()?"Servidor no disponible.":result.get(0).get(0));
        return result;
    }
}
