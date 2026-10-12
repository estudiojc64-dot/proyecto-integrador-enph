package co.edu.udi.enph;

import java.math.BigDecimal;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static co.edu.udi.enph.Domain.*;

/**
 * Persistencia central de los cinco CU seleccionados. Cada operación de negocio
 * usa una conexión, una transacción y parámetros enlazados. El período se bloquea
 * con SELECT FOR UPDATE para serializar asignación, programación y recepción.
 * El cliente jamás recibe credenciales Oracle. JDBC es propuesta DEC-041.
 */
public final class OracleRepository {
    private final String url,user,password;
    private final Clock clock;
    public OracleRepository(String url,String user,String password,Clock clock) {
        this.url=url; this.user=user; this.password=password; this.clock=clock;
    }
    /** Ejecuta una solicitud autenticada por Server; no confía en un rol enviado por el cliente. */
    public List<List<String>> execute(String userId,List<List<String>> request) throws Exception {
        require(!request.isEmpty() && !request.get(0).isEmpty(),"Solicitud vacía.");
        String command=request.get(0).get(0);
        List<String> a=request.size()>1?request.get(1):List.of();
        try(Connection c=DriverManager.getConnection(url,user,password)) {
            c.setAutoCommit(false);
            try {
                var u=one(c,"SELECT id,nombre,rol FROM usuario WHERE id=? AND habilitado='S'",userId);
                require(u!=null,"Usuario no habilitado."); User actor=new User(u.get(0),u.get(1),Role.valueOf(u.get(2)));
                List<List<String>> result;
                if(command.equals("ME")) result=List.of(u);
                else if(command.equals("USERS")) result=query(c,"SELECT id,nombre,rol FROM usuario WHERE habilitado='S' ORDER BY nombre");
                else if(command.equals("PERIODS")) result=periods(c,actor);
                else {
                    require(!a.isEmpty(),"Falta período."); String p=a.get(0); UUID.fromString(p);
                    var period=one(c,"SELECT inicio,dispositivo FROM periodo WHERE id=? FOR UPDATE",p);
                    require(period!=null,"Período no encontrado.");
                    result=dispatch(c,actor,command,a,request,p,period);
                }
                c.commit(); return result;
            } catch(Exception e) { try { c.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
        }
    }
    private List<List<String>> periods(Connection c,User actor) throws SQLException {
        String sql="SELECT p.id,h.nombre,p.inicio,u.nombre,r.confirmada_en,p.dispositivo,v.resultado "
            +"FROM periodo p JOIN hogar h ON h.id=p.hogar_id "
            +"LEFT JOIN asignacion a ON a.periodo_id=p.id AND a.hasta IS NULL LEFT JOIN usuario u ON u.id=a.encuestador_id "
            +"LEFT JOIN recepcion r ON r.periodo_id=p.id LEFT JOIN revision v ON v.periodo_id=p.id ";
        return actor.role()==Role.ENCUESTADOR ? query(c,sql+"WHERE a.encuestador_id=? ORDER BY h.nombre",actor.id())
            :query(c,sql+"ORDER BY h.nombre");
    }
    private void role(User actor,Role... roles) {
        require(Arrays.asList(roles).contains(actor.role()),"Su rol no tiene permiso para esta operación.");
    }
    private List<String> assigned(Connection c,String p) throws SQLException {
        var r=one(c,"SELECT id,encuestador_id,desde FROM asignacion WHERE periodo_id=? AND hasta IS NULL",p);
        require(r!=null,"El período necesita un responsable vigente."); return r;
    }
    private boolean received(Connection c,String p) throws SQLException {
        return one(c,"SELECT periodo_id FROM recepcion WHERE periodo_id=?",p)!=null;
    }
    private List<List<String>> ok(String message) { return List.of(List.of(message)); }
    private List<List<String>> dispatch(Connection c,User actor,String cmd,List<String> a,
        List<List<String>> request,String p,List<String> period) throws Exception {
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        switch(cmd) {
            case "ASSIGN": {
                role(actor,Role.COORDINADOR); require(a.size()==3,"Asignación incompleta.");
                require(period.get(1).isEmpty() && !received(c,p),
                    "La captura fue entregada a una tableta. Continuidad de reasignación pendiente: se conserva el responsable.");
                var target=one(c,"SELECT id FROM usuario WHERE id=? AND rol='ENCUESTADOR' AND habilitado='S'",a.get(1));
                require(target!=null,"Seleccione un encuestador habilitado.");
                var old=one(c,"SELECT encuestador_id FROM asignacion WHERE periodo_id=? AND hasta IS NULL",p);
                if(old!=null && old.get(0).equals(a.get(1))) return ok("Ya es el responsable vigente.");
                if(old!=null) text(a.get(2),500,"Motivo de reasignación");
                // Las citas de la última programación deben ser compatibles con el sustituto.
                for(var v:currentVisits(c,p)) checkAvailability(c,a.get(1),p,Instant.parse(v.get(0)));
                update(c,"UPDATE asignacion SET hasta=? WHERE periodo_id=? AND hasta IS NULL",now,p);
                update(c,"INSERT INTO asignacion(id,periodo_id,encuestador_id,desde,autor_id,motivo) VALUES(?,?,?,?,?,?)",
                    id(),p,a.get(1),now,actor.id(),clean(a.get(2)));
                return ok("Responsable registrado; se conserva el historial.");
            }
            case "PROGRAM": {
                role(actor,Role.COORDINADOR); require(a.size()==3,"Programación incompleta.");
                require(period.get(1).isEmpty() && !received(c,p),
                    "No se cambian fechas con una captura entregada o recibida; la compatibilidad local no está verificada.");
                var assignment=assigned(c,p); LocalDate start=LocalDate.parse(a.get(1));
                List<Instant> visits=new ArrayList<>();
                for(int i=2;i<request.size();i++) {
                    require(request.get(i).size()==1,"Cita inválida."); Instant at=Instant.parse(request.get(i).get(0));
                    LocalDate d=at.atZone(ZONE).toLocalDate();
                    require(!d.isBefore(start) && !d.isAfter(start.plusDays(13)),"Visita fuera de las catorce fechas.");
                    require(!at.isBefore(Instant.parse(assignment.get(2))),"Cita anterior a la asignación vigente.");
                    checkAvailability(c,assignment.get(1),p,at);
                    for(Instant prev:visits) require(Math.abs(Duration.between(prev,at).toMinutes())>=60,"Citas superpuestas.");
                    visits.add(at);
                }
                String program=id(); String reason=clean(a.get(2)); if(!reason.isEmpty()) text(reason,500,"Motivo");
                int version=Integer.parseInt(one(c,"SELECT NVL(MAX(numero),0)+1 FROM programacion WHERE periodo_id=?",p).get(0));
                update(c,"INSERT INTO programacion(id,periodo_id,numero,inicio,autor_id,registrada_en,motivo) VALUES(?,?,?,?,?,?,?)",
                    program,p,version,start,actor.id(),now,reason);
                for(Instant at:visits) update(c,"INSERT INTO visita_prevista(id,programacion_id,asignacion_id,fecha) VALUES(?,?,?,?)",
                    id(),program,assignment.get(0),at);
                update(c,"UPDATE periodo SET inicio=? WHERE id=?",start,p);
                return ok("Calendario registrado. Una cita prevista no acredita una visita realizada.");
            }
            case "CHECKOUT": {
                role(actor,Role.ENCUESTADOR); require(a.size()==2,"Dispositivo no identificado."); UUID.fromString(a.get(1));
                require(assigned(c,p).get(1).equals(actor.id()),"Hogar de otro encuestador.");
                require(!period.get(0).isEmpty() && !received(c,p),"No hay calendario editable disponible.");
                require(period.get(1).isEmpty() || period.get(1).equals(a.get(1)),"Otro dispositivo tiene la captura de este período.");
                update(c,"UPDATE periodo SET dispositivo=? WHERE id=?",a.get(1),p);
                var household=one(c,"SELECT h.nombre FROM periodo p JOIN hogar h ON h.id=p.hogar_id WHERE p.id=?",p);
                return LocalStore.rows(new LocalPeriod(p,household.get(0),actor.id(),a.get(1),LocalDate.parse(period.get(0))));
            }
            case "RECEIVE": {
                role(actor,Role.ENCUESTADOR);
                // El primer renglón tras el comando es la cabecera del conjunto local.
                var local=LocalStore.from(request.subList(1,request.size()));
                require(local.id.equals(p) && local.author.equals(actor.id()),"Autor o período ajeno.");
                require(local.device.equals(period.get(1)),"Identidad de dispositivo incompatible.");
                require(local.start.toString().equals(period.get(0)),"Calendario incompatible.");
                require(local.closedAt!=null && !local.closedAt.isAfter(now),"Finalización inválida.");
                local.receivedAt=null; local.complete(Clock.fixed(local.closedAt,ZONE));
                for(var acquisition:local.acquisitions) require(!acquisition.registeredAt().isAfter(local.closedAt),"Registro posterior al cierre.");
                for(var at:local.absences.values()) require(!at.isAfter(local.closedAt),"Declaración posterior al cierre.");
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Wire.encode(LocalStore.rows(local))));
                var receipt=one(c,"SELECT huella,confirmada_en FROM recepcion WHERE periodo_id=?",p);
                if(receipt!=null) {
                    require(receipt.get(0).equals(hash),"Ya existe un recibo para un conjunto diferente; no se sobrescribe.");
                    return ok(receipt.get(1));
                }
                require(assigned(c,p).get(1).equals(actor.id()),"Responsabilidad cambió antes de recibir.");
                for(var acquisition:local.acquisitions) insertAcquisition(c,p,acquisition);
                for(var entry:local.absences.entrySet()) update(c,"INSERT INTO ausencia(periodo_id,fecha,autor_id,registrada_en) VALUES(?,?,?,?)",
                    p,entry.getKey(),actor.id(),entry.getValue());
                // El recibo se inserta al final. El commit confirma datos y recibo conjuntamente.
                update(c,"INSERT INTO recepcion(periodo_id,huella,finalizada_en,confirmada_en,autor_id) VALUES(?,?,?,?,?)",
                    p,hash,local.closedAt,now,actor.id());
                return ok(now.toString());
            }
            case "DATA": {
                role(actor,Role.SUPERVISOR,Role.CRITICO); require(received(c,p),"Información aún no recibida.");
                List<List<String>> rows=new ArrayList<>();
                for(var r:query(c,"SELECT id,fecha,descripcion,cantidad,unidad,unidad_otra,equivalencia,equivalencia_unidad,"
                    +"forma,forma_otra,lugar,lugar_otro,frecuencia,valor,clase,categoria,persona,edad,autor_id,registrada_en "
                    +"FROM adquisicion WHERE periodo_id=? ORDER BY fecha,registrada_en,id",p)) {
                    var row=new ArrayList<String>(); row.add("A"); row.addAll(r); rows.add(row);
                }
                for(var r:query(c,"SELECT fecha,registrada_en FROM ausencia WHERE periodo_id=? ORDER BY fecha",p))
                    rows.add(List.of("N",r.get(0),r.get(1)));
                return rows;
            }
            case "INFO": {
                role(actor,Role.COORDINADOR,Role.SUPERVISOR,Role.CRITICO); List<List<String>> rows=new ArrayList<>();
                append(rows,"Asignación",query(c,"SELECT u.nombre,a.desde,a.hasta,a.motivo FROM asignacion a JOIN usuario u ON u.id=a.encuestador_id WHERE a.periodo_id=? ORDER BY a.desde",p));
                append(rows,"Calendario histórico",query(c,"SELECT inicio,registrada_en,motivo FROM programacion WHERE periodo_id=? ORDER BY registrada_en",p));
                append(rows,"Cita prevista",currentVisits(c,p));
                append(rows,"Acción",query(c,"SELECT a.id,a.descripcion,a.motivo,u.nombre,a.plazo FROM accion a JOIN usuario u ON u.id=a.responsable_id WHERE periodo_id=? ORDER BY a.registrada_en",p));
                append(rows,"Seguimiento",query(c,"SELECT s.accion_id,s.numero,s.estado,s.texto,s.registrada_en FROM seguimiento s JOIN accion a ON a.id=s.accion_id WHERE a.periodo_id=? ORDER BY s.numero",p));
                append(rows,"Novedad reportada",query(c,"SELECT tipo,texto,origen,registrada_en FROM novedad WHERE periodo_id=? ORDER BY registrada_en",p));
                if(received(c,p)) {
                    append(rows,"Revisión",query(c,"SELECT alcance,resultado,registrada_en FROM revision WHERE periodo_id=?",p));
                    append(rows,"Observación",query(c,"SELECT id,fecha,adquisicion_id,texto,estado,evaluacion FROM observacion WHERE periodo_id=? ORDER BY registrada_en",p));
                    append(rows,"Aclaración",query(c,"SELECT a.observacion_id,a.texto,a.origen,a.registrada_en FROM aclaracion a JOIN observacion o ON o.id=a.observacion_id WHERE o.periodo_id=? ORDER BY a.registrada_en",p));
                }
                return rows;
            }
            case "ACTION": {
                role(actor,Role.SUPERVISOR); require(a.size()==5,"Acción incompleta.");
                var target=one(c,"SELECT id FROM usuario WHERE id=? AND habilitado='S'",a.get(3)); require(target!=null,"Responsable inválido.");
                Instant deadline=Instant.parse(a.get(4)); require(!deadline.isBefore(now),"El plazo de la nueva acción debe ser futuro.");
                String action=id(); update(c,"INSERT INTO accion VALUES(?,?,?,?,?,?,?,?)",action,p,text(a.get(1),500,"Acción"),
                    text(a.get(2),500,"Motivo"),a.get(3),deadline,actor.id(),now); return ok(action);
            }
            case "FOLLOW": {
                role(actor,Role.SUPERVISOR); require(a.size()==4,"Seguimiento incompleto.");
                require(one(c,"SELECT id FROM accion WHERE id=? AND periodo_id=?",a.get(1),p)!=null,"Acción de otro período.");
                require(Set.of("ABIERTA","EN_SEGUIMIENTO","ATENDIDA").contains(a.get(2)),"Estado inválido.");
                int number=Integer.parseInt(one(c,"SELECT NVL(MAX(numero),0)+1 FROM seguimiento WHERE accion_id=?",a.get(1)).get(0));
                update(c,"INSERT INTO seguimiento VALUES(?,?,?,?,?,?,?)",id(),a.get(1),number,a.get(2),text(a.get(3),800,"Seguimiento"),actor.id(),now);
                return ok("Seguimiento registrado.");
            }
            case "REPORT": {
                role(actor,Role.SUPERVISOR); require(a.size()==4,"Novedad incompleta.");
                require(Set.of("FINALIZACION_REPORTADA","OTRA").contains(a.get(1)),"Tipo de novedad inválido.");
                update(c,"INSERT INTO novedad VALUES(?,?,?,?,?,?,?)",id(),p,a.get(1),text(a.get(2),800,"Novedad"),text(a.get(3),180,"Origen"),actor.id(),now);
                return ok("Novedad reportada conservada. No equivale a recepción.");
            }
            case "OBSERVE": {
                role(actor,Role.CRITICO); require(a.size()==4 && received(c,p),"Requiere recepción y observación completa.");
                LocalDate date=null; String acquisition="";
                if(a.get(1).equals("DIA")) {
                    date=LocalDate.parse(a.get(2)); LocalDate start=LocalDate.parse(period.get(0));
                    require(!date.isBefore(start) && !date.isAfter(start.plusDays(13)),"Fecha de otro período.");
                } else {
                    require(a.get(1).equals("ADQUISICION"),"Objetivo inválido."); acquisition=a.get(2);
                    require(one(c,"SELECT id FROM adquisicion WHERE id=? AND periodo_id=?",acquisition,p)!=null,"Adquisición de otro período.");
                }
                beginReview(c,p,actor,now);
                update(c,"INSERT INTO observacion(id,periodo_id,fecha,adquisicion_id,texto,estado,autor_id,registrada_en) VALUES(?,?,?,?,?,?,?,?)",
                    id(),p,date,acquisition,text(a.get(3),800,"Observación"),"PENDIENTE_ACLARACION",actor.id(),now);
                update(c,"UPDATE revision SET resultado='PENDIENTE_ACLARACION',autor_id=?,registrada_en=? WHERE periodo_id=?",actor.id(),now,p);
                return ok("Observación registrada; la adquisición conserva su valor.");
            }
            case "CLARIFY": {
                role(actor,Role.CRITICO); require(a.size()==4,"Aclaración incompleta."); checkObservation(c,p,a.get(1));
                update(c,"INSERT INTO aclaracion VALUES(?,?,?,?,?,?)",id(),a.get(1),text(a.get(2),800,"Aclaración"),text(a.get(3),180,"Origen"),actor.id(),now);
                return ok("Aclaración registrada. Evalúe el hallazgo antes de confirmar el resultado.");
            }
            case "EVALUATE": {
                role(actor,Role.CRITICO); require(a.size()==4,"Evaluación incompleta."); checkObservation(c,p,a.get(1));
                require(Set.of("ACLARADA","OBSERVACION_PERSISTE","PENDIENTE_ACLARACION").contains(a.get(2)),"Estado inválido.");
                if(a.get(2).equals("ACLARADA")) require(one(c,"SELECT id FROM aclaracion WHERE observacion_id=? FETCH FIRST 1 ROWS ONLY",a.get(1))!=null,"Registre primero la aclaración.");
                update(c,"UPDATE observacion SET estado=?,evaluacion=?,evaluador_id=?,evaluada_en=? WHERE id=?",
                    a.get(2),text(a.get(3),800,"Evaluación"),actor.id(),now,a.get(1));
                update(c,"UPDATE revision SET resultado='EN_CURSO',autor_id=?,registrada_en=? WHERE periodo_id=?",actor.id(),now,p);
                return ok("Evaluación registrada; adquisición sin cambios.");
            }
            case "REVIEW": {
                role(actor,Role.CRITICO); require(a.size()==3 && received(c,p),"Requiere recepción y resultado completo.");
                String result=a.get(2); require(Set.of("PENDIENTE_ACLARACION","REVISADO_CON_OBSERVACIONES","REVISADO_SIN_OBSERVACIONES").contains(result),"Resultado inválido.");
                int pending=Integer.parseInt(one(c,"SELECT COUNT(*) FROM observacion WHERE periodo_id=? AND estado='PENDIENTE_ACLARACION'",p).get(0));
                int persistent=Integer.parseInt(one(c,"SELECT COUNT(*) FROM observacion WHERE periodo_id=? AND estado='OBSERVACION_PERSISTE'",p).get(0));
                require(!result.equals("REVISADO_SIN_OBSERVACIONES") || pending+persistent==0,"Hay hallazgos pendientes o persistentes.");
                require(!result.equals("PENDIENTE_ACLARACION") || pending>0,"No existen aclaraciones pendientes.");
                require(!result.equals("REVISADO_CON_OBSERVACIONES") || pending+persistent>0,"No existen observaciones pendientes o persistentes.");
                beginReview(c,p,actor,now);
                update(c,"UPDATE revision SET alcance=?,resultado=?,autor_id=?,registrada_en=? WHERE periodo_id=?",
                    text(a.get(1),180,"Alcance revisado"),result,actor.id(),now,p);
                return ok("Resultado de D registrado. No acredita todo el cuadernillo.");
            }
            default: throw new IllegalArgumentException("Operación no admitida.");
        }
    }
    private void beginReview(Connection c,String p,User actor,Instant now) throws SQLException {
        if(one(c,"SELECT periodo_id FROM revision WHERE periodo_id=?",p)==null)
            update(c,"INSERT INTO revision VALUES(?,?,?,?,?)",p,"Primera tabla D","EN_CURSO",actor.id(),now);
    }
    private void checkObservation(Connection c,String p,String obs) throws SQLException {
        require(received(c,p) && one(c,"SELECT id FROM observacion WHERE id=? AND periodo_id=?",obs,p)!=null,"Observación de otro período o no recibida.");
    }
    private List<List<String>> currentVisits(Connection c,String p) throws SQLException {
        return query(c,"SELECT v.fecha FROM visita_prevista v JOIN programacion pr ON pr.id=v.programacion_id "
            +"WHERE pr.id=(SELECT id FROM programacion WHERE periodo_id=? ORDER BY numero DESC FETCH FIRST 1 ROWS ONLY) ORDER BY v.fecha",p);
    }
    private void checkAvailability(Connection c,String responsible,String p,Instant at) throws SQLException {
        // Política técnica propuesta: cada cita reserva una hora. Serializar también
        // la referencia del encuestador evita carreras entre dos hogares diferentes.
        one(c,"SELECT id FROM usuario WHERE id=? FOR UPDATE",responsible);
        for(var r:query(c,"SELECT v.fecha FROM visita_prevista v JOIN programacion pr ON pr.id=v.programacion_id "
            +"JOIN asignacion a ON a.periodo_id=pr.periodo_id AND a.hasta IS NULL "
            +"WHERE a.encuestador_id=? AND pr.periodo_id<>? "
            +"AND pr.id=(SELECT id FROM programacion x WHERE x.periodo_id=pr.periodo_id ORDER BY numero DESC FETCH FIRST 1 ROWS ONLY)",responsible,p))
            require(Math.abs(Duration.between(Instant.parse(r.get(0)),at).toMinutes())>=60,"El encuestador tiene otra cita en esa hora.");
    }
    private static void append(List<List<String>> into,String tag,List<List<String>> rows) {
        for(var row:rows) { var r=new ArrayList<String>(); r.add(tag); r.addAll(row); into.add(r); }
    }
    private void insertAcquisition(Connection c,String p,Acquisition acquisition) throws SQLException {
        var r=acquisition.row(); List<Object> values=new ArrayList<>(); values.add(r.get(0)); values.add(p);
        values.add(acquisition.date()); values.add(r.get(2)); values.add(acquisition.quantity());
        values.add(r.get(4)); values.add(r.get(5)); values.add(acquisition.equivalent());
        for(int i=7;i<=12;i++) values.add(r.get(i)); values.add(acquisition.value());
        for(int i=14;i<=16;i++) values.add(r.get(i)); values.add(r.get(17).isEmpty()?null:Integer.parseInt(r.get(17)));
        values.add(r.get(18)); values.add(acquisition.registeredAt());
        update(c,"INSERT INTO adquisicion VALUES("+String.join(",",Collections.nCopies(21,"?"))+")",values.toArray());
    }
    private static void bind(PreparedStatement statement,Object... args) throws SQLException {
        for(int i=0;i<args.length;i++) { Object value=args[i];
            if(value instanceof Instant instant) value=instant.atOffset(ZoneOffset.UTC);
            if(value instanceof LocalDate date) value=java.sql.Date.valueOf(date);
            if(value instanceof String s && s.isEmpty()) value=null; statement.setObject(i+1,value);
        }
    }
    static int update(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) { bind(s,args); return s.executeUpdate(); }
    }
    static List<String> one(Connection c,String sql,Object... args) throws SQLException {
        var rows=query(c,sql,args); return rows.isEmpty()?null:rows.get(0);
    }
    static List<List<String>> query(Connection c,String sql,Object... args) throws SQLException {
        List<List<String>> rows=new ArrayList<>();
        try(PreparedStatement s=c.prepareStatement(sql)) { bind(s,args); try(ResultSet rs=s.executeQuery()) {
            ResultSetMetaData meta=rs.getMetaData();
            while(rs.next()) { List<String> r=new ArrayList<>();
                for(int i=1;i<=meta.getColumnCount();i++) {
                    if(rs.getObject(i)==null) r.add("");
                    else if(meta.getColumnType(i)==Types.DATE || meta.getColumnType(i)==Types.TIMESTAMP)
                        r.add(rs.getDate(i).toLocalDate().toString());
                    else if(meta.getColumnType(i)==Types.TIMESTAMP_WITH_TIMEZONE || meta.getColumnTypeName(i).contains("TIME ZONE"))
                        r.add(rs.getObject(i,OffsetDateTime.class).toInstant().toString());
                    else r.add(rs.getString(i));
                } rows.add(r);
            }
        }} return rows;
    }
}
