package co.edu.udi.enph;

import java.io.*;
import java.util.*;

/**
 * Contrato binario ENPH1 para listas de renglones de texto. Evita deserializar
 * objetos Java de la red. Los límites reducen cargas accidentales o malformadas.
 * El protocolo es interno del proyecto; no es formato de intercambio DANE.
 */
public final class Wire {
    public static final int MAX_BYTES=8*1024*1024;
    private Wire() { }
    public static byte[] encode(List<List<String>> rows) throws IOException {
        Domain.require(rows.size()<=12000,"Demasiados renglones.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeUTF("ENPH1"); out.writeInt(rows.size());
            for (List<String> row:rows) { Domain.require(row.size()<=40,"Demasiadas columnas."); out.writeInt(row.size());
                for (String value:row) out.writeUTF(value); }
        }
        Domain.require(bytes.size()<=MAX_BYTES,"Conjunto demasiado grande."); return bytes.toByteArray();
    }
    public static List<List<String>> decode(byte[] bytes) throws IOException {
        Domain.require(bytes.length<=MAX_BYTES,"Conjunto demasiado grande.");
        try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            Domain.require(in.readUTF().equals("ENPH1"),"Versión de protocolo inválida.");
            int count=in.readInt(); Domain.require(count>=0 && count<=12000,"Cantidad de renglones inválida.");
            List<List<String>> rows=new ArrayList<>();
            for (int i=0;i<count;i++) { int n=in.readInt(); Domain.require(n>=0 && n<=40,"Cantidad de columnas inválida.");
                List<String> row=new ArrayList<>(); for(int j=0;j<n;j++) row.add(in.readUTF()); rows.add(List.copyOf(row)); }
            Domain.require(in.available()==0,"Contenido adicional no permitido."); return List.copyOf(rows);
        }
    }
}
