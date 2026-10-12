package co.edu.udi.enph;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static co.edu.udi.enph.Domain.*;

/**
 * Persistencia local explícita (RNF-01), con reemplazo atómico y exclusión de
 * una segunda instancia. Guarda solo después de confirmaciones del usuario.
 * Un fallo no destruye el archivo confirmado. Los datos quedan en el directorio
 * privado del usuario; el prototipo académico no implementa cifrado en reposo.
 */
public final class LocalStore implements AutoCloseable {
    private final Path directory;
    private final FileChannel channel;
    private final FileLock lock;
    public LocalStore(Path directory) throws IOException {
        this.directory=directory.toAbsolutePath(); Files.createDirectories(this.directory);
        channel=FileChannel.open(this.directory.resolve(".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired=channel.tryLock(); } catch (OverlappingFileLockException e) { acquired=null; }
        if (acquired==null) { channel.close(); throw new IOException("Otra instancia está usando esta captura local."); }
        lock=acquired;
    }
    private Path path(String id) { UUID.fromString(id); return directory.resolve(id+".enph"); }
    public static List<List<String>> rows(LocalPeriod p) {
        List<List<String>> rows=new ArrayList<>();
        rows.add(List.of(p.id,p.household,p.author,p.device,p.start.toString(),
            p.closedAt==null?"":p.closedAt.toString(),p.receivedAt==null?"":p.receivedAt.toString()));
        for(var a:p.acquisitions) { List<String> row=new ArrayList<>(); row.add("A"); row.addAll(a.row()); rows.add(row); }
        p.absences.forEach((date,at)->rows.add(List.of("N",date.toString(),at.toString())));
        return rows;
    }
    public static LocalPeriod from(List<List<String>> rows) {
        require(!rows.isEmpty() && rows.get(0).size()==7,"Cabecera de captura inválida.");
        var h=rows.get(0); var p=new LocalPeriod(h.get(0),h.get(1),h.get(2),h.get(3),LocalDate.parse(h.get(4)));
        p.closedAt=h.get(5).isEmpty()?null:Instant.parse(h.get(5)); p.receivedAt=h.get(6).isEmpty()?null:Instant.parse(h.get(6));
        require(p.receivedAt==null || p.closedAt!=null,"Recepción sin finalización.");
        Set<String> ids=new HashSet<>();
        for(int i=1;i<rows.size();i++) { var r=rows.get(i);
            if(r.size()==21 && r.get(0).equals("A")) { var a=Acquisition.from(r.subList(1,r.size()));
                require(ids.add(a.id()),"Adquisición duplicada."); p.acquisitions.add(a); }
            else if(r.size()==3 && r.get(0).equals("N")) {
                require(p.absences.put(LocalDate.parse(r.get(1)),Instant.parse(r.get(2)))==null,"Ausencia duplicada."); }
            else throw new IllegalArgumentException("Renglón local inválido.");
        }
        return p;
    }
    public void save(LocalPeriod p) throws IOException {
        byte[] bytes=Wire.encode(rows(p)); Path target=path(p.id), temp=Files.createTempFile(directory,"captura-",".tmp");
        try {
            try(FileChannel output=FileChannel.open(temp,StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(bytes); while(b.hasRemaining()) output.write(b); output.force(true);
            }
            // Se exige ATOMIC_MOVE: si el sistema de archivos no lo soporta, se informa fallo.
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }
    public LocalPeriod load(String id) throws IOException {
        Path p=path(id); return Files.exists(p)?from(Wire.decode(Files.readAllBytes(p))):null;
    }
    public List<LocalPeriod> all() throws IOException {
        List<LocalPeriod> result=new ArrayList<>();
        try(var files=Files.newDirectoryStream(directory,"*.enph")) {
            for(Path f:files) result.add(from(Wire.decode(Files.readAllBytes(f))));
        }
        return result;
    }
    public void close() throws IOException { lock.release(); channel.close(); }
}
