package co.edu.udi.enph;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Reglas compartidas del proyecto académico: primera tabla D, CU-06/RF-07.
 * No representa un aplicativo oficial del DANE. DEC-029 adapta a catorce días
 * los gastos personales; DEC-035/036 prohíben corregir adquisiciones centrales.
 * Las categorías de aplicabilidad son una propuesta técnica (DEC-041).
 */
public final class Domain {
    private Domain() { }
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");
    public enum Role { COORDINADOR, ENCUESTADOR, SUPERVISOR, CRITICO }
    public enum Category { ALIMENTO_HOGAR, BOLSILLO_RESPONSABLE, BOLSILLO_MENOR, MESADA,
        EXCLUIDO_D, COMIDA_FUERA_D1 }
    public record User(String id, String name, Role role) {
        @Override public String toString() { return name; }
    }
    public record Option(String code, String name) {
        @Override public String toString() { return name; }
    }
    public static final List<Option> UNITS = options("1|Centímetros cúbicos", "2|Litro", "3|Gramos",
        "4|Onzas", "5|Libra", "6|Kilo", "7|Arroba", "8|Otra");
    public static final List<Option> FORMS = options("1|Compra", "2|Recibidos como pago por trabajo",
        "3|Regalo o donación", "4|Intercambio o trueque", "5|Traídos de la finca o producidos por el hogar",
        "6|Tomados de un negocio del hogar", "7|Otra");
    public static final List<Option> PLACES = options(
        "1|Almacenes, supermercados de cadena, tiendas por departamento o hipermercados",
        "4|Supermercados de cajas de compensación, cooperativas, fondos de empleados y comisariatos",
        "6|Supermercados o tiendas de barrio, cigarrerías, salsamentarias y delicatessen",
        "7|Misceláneas de barrio y cacharrerías", "10|Plazas de mercado, galerías, mercados móviles, centrales mayoristas y graneros",
        "13|Vendedores ambulantes", "14|Sanandresitos, bodegas y fábricas",
        "16|Establecimiento especializado en el artículo o servicio", "17|Farmacias y droguerías",
        "18|Restaurantes", "19|Cafeterías y establecimientos de comidas rápidas", "20|Persona particular",
        "21|Ferias especializadas", "22|A través de internet", "23|Televentas y ventas por catálogo",
        "24|Otro", "25|Plazoleta de comidas");
    public static final List<Option> FREQUENCIES = options("2|Diario", "2.1|Varias veces por semana",
        "3|Semanal", "4|Quincenal", "5|Mensual", "6|Bimestral", "7|Trimestral", "9|Esporádica");
    public static final List<Option> CLASSES = options("1|Gasto del hogar", "2|Gasto personal");
    private static List<Option> options(String... values) {
        return Arrays.stream(values).map(s -> s.split("\\|", 2)).map(a -> new Option(a[0], a[1])).toList();
    }
    public static String label(List<Option> list, String code) {
        return list.stream().filter(o -> o.code().equals(code)).map(Option::name).findFirst().orElse("");
    }
    public static String id() { return UUID.randomUUID().toString(); }
    public static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
    public static String text(String value, int max, String name) {
        String v = value == null ? "" : value.trim();
        require(!v.isEmpty() && v.length() <= max, name + ": obligatorio, máximo " + max + " caracteres.");
        return v;
    }
    public static void code(List<Option> list, String value, String name) {
        require(!label(list, value).isEmpty(), name + " no pertenece al catálogo.");
    }
    public static BigDecimal decimal(String value) {
        try { return new BigDecimal(value.trim().replace(',', '.')); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Número inválido. Use 4500 o 1,5, sin separadores de miles."); }
    }
    private static void amount(BigDecimal v, int precision, int scale, boolean positive, String name) {
        require(v != null && (positive ? v.signum() > 0 : v.signum() >= 0), name + " inválido.");
        require(Math.max(v.scale(), 0) <= scale && v.precision() - v.scale() <= precision - scale,
            name + ": máximo " + (precision-scale) + " enteros y " + scale + " decimales.");
    }

    /** Un renglón de D; fecha de adquisición separada de fecha/hora de registro. */
    public record Acquisition(String id, LocalDate date, String description, BigDecimal quantity,
        String unit, String unitOther, BigDecimal equivalent, String equivalentUnit,
        String form, String formOther, String place, String placeOther, String frequency,
        BigDecimal value, String expenseClass, Category category, String beneficiary,
        String age, String author, Instant registeredAt) {
        public Acquisition {
            id = text(id, 36, "Identidad"); description = text(description, 240, "Artículo o servicio");
            Objects.requireNonNull(date); Objects.requireNonNull(registeredAt); Objects.requireNonNull(category);
            amount(quantity, 12, 3, true, "Cantidad"); amount(value, 14, 2, false, "Valor");
            code(UNITS, unit, "Unidad"); code(FORMS, form, "Forma"); code(CLASSES, expenseClass, "Clase");
            unitOther = clean(unitOther); formOther = clean(formOther); place = clean(place);
            placeOther = clean(placeOther); frequency = clean(frequency); beneficiary = clean(beneficiary);
            age = clean(age); equivalentUnit = clean(equivalentUnit); author = text(author, 36, "Autor");
            require(unit.equals("8") == !unitOther.isEmpty(), "Especifique Otra unidad solo cuando corresponda.");
            require(form.equals("7") == !formOther.isEmpty(), "Especifique Otra forma solo cuando corresponda.");
            if (!unitOther.isEmpty()) text(unitOther, 100, "Otra unidad");
            if (!formOther.isEmpty()) text(formOther, 160, "Otra forma");
            if (equivalent != null) {
                require(unit.equals("8"), "Equivalencia: deje en blanco para las unidades tradicionales.");
                amount(equivalent, 12, 3, true, "Equivalencia");
                require(Set.of("G", "CC").contains(equivalentUnit), "Equivalencia requiere gramos o centímetros cúbicos.");
            } else require(equivalentUnit.isEmpty(), "Unidad de equivalencia sin cantidad.");
            if (form.equals("1")) {
                code(FREQUENCIES, frequency, "Frecuencia");
                if (!place.isEmpty()) code(PLACES, place, "Lugar");
                require(category != Category.ALIMENTO_HOGAR || !place.isEmpty(), "Una compra de alimentos requiere lugar.");
                require(place.equals("24") == !placeOther.isEmpty(), "Especifique Otro lugar solo cuando corresponda.");
                if (!placeOther.isEmpty()) text(placeOther, 240, "Otro lugar");
            } else require(place.isEmpty() && placeOther.isEmpty() && frequency.isEmpty(),
                "En adquisiciones sin compra, lugar y frecuencia quedan vacíos; el valor es estimado.");
            require(category != Category.EXCLUIDO_D && category != Category.COMIDA_FUERA_D1,
                "La categoría no corresponde a la primera tabla D seleccionada.");
            require(category != Category.ALIMENTO_HOGAR || expenseClass.equals("1"), "Alimento del hogar requiere clase hogar.");
            if (category != Category.ALIMENTO_HOGAR) text(beneficiary, 120, "Persona relacionada");
            if (category == Category.BOLSILLO_RESPONSABLE || category == Category.BOLSILLO_MENOR)
                require(expenseClass.equals("2"), "Gasto de bolsillo requiere clase personal.");
            if (category == Category.BOLSILLO_MENOR) {
                try { int n = Integer.parseInt(age); require(n >= 0 && n < 10, "El menor debe tener menos de diez años."); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Edad del menor inválida."); }
            } else require(age.isEmpty(), "La edad solo se pide para gastos de menores.");
        }
        public boolean paid() { return form.equals("1"); }
        public List<String> row() {
            return List.of(id,date.toString(),description,quantity.toPlainString(),unit,unitOther,
                equivalent == null ? "" : equivalent.toPlainString(),equivalentUnit,form,formOther,
                place,placeOther,frequency,value.toPlainString(),expenseClass,category.name(),beneficiary,age,
                author,registeredAt.toString());
        }
        public static Acquisition from(List<String> r) {
            require(r.size()==20, "Renglón inválido.");
            return new Acquisition(r.get(0),LocalDate.parse(r.get(1)),r.get(2),decimal(r.get(3)),r.get(4),r.get(5),
                r.get(6).isEmpty()?null:decimal(r.get(6)),r.get(7),r.get(8),r.get(9),r.get(10),r.get(11),r.get(12),
                decimal(r.get(13)),r.get(14),Category.valueOf(r.get(15)),r.get(16),r.get(17),r.get(18),Instant.parse(r.get(19)));
        }
    }
    public static String clean(String s) { return s == null ? "" : s.trim(); }

    /** Estado local cerrado: no se modifica después de finalizar, salvo su recibo técnico. */
    public static final class LocalPeriod {
        public final String id, household, author, device;
        public final LocalDate start;
        public final List<Acquisition> acquisitions = new ArrayList<>();
        public final Map<LocalDate, Instant> absences = new TreeMap<>();
        public Instant closedAt, receivedAt;
        public LocalPeriod(String id, String household, String author, String device, LocalDate start) {
            this.id=text(id,36,"Período"); this.household=text(household,160,"Hogar");
            this.author=text(author,36,"Encuestador"); this.device=text(device,36,"Dispositivo"); this.start=Objects.requireNonNull(start);
        }
        public LocalDate end() { return start.plusDays(13); }
        public void dateValid(LocalDate date, Clock clock) {
            require(!date.isBefore(start) && !date.isAfter(end()), "Fecha fuera del período.");
            require(!date.isAfter(LocalDate.now(clock.withZone(ZONE))), "No se registran días futuros.");
        }
        private void editable() { require(closedAt == null, "Período finalizado: la captura está cerrada."); }
        public void add(Acquisition a, Clock clock) {
            editable(); dateValid(a.date(),clock); require(a.author().equals(author), "Autor distinto del responsable local.");
            require(!absences.containsKey(a.date()), "Retire primero la declaración de ausencia.");
            require(acquisitions.stream().noneMatch(x -> x.id().equals(a.id())), "Identidad de adquisición repetida.");
            require(acquisitions.size()<10000, "Límite técnico de 10.000 renglones por período.");
            acquisitions.add(a);
        }
        public void remove(String id) { editable(); require(acquisitions.removeIf(a->a.id().equals(id)), "Adquisición no encontrada."); }
        public void absent(LocalDate d, Clock clock) {
            editable(); dateValid(d,clock); require(acquisitions.stream().noneMatch(a->a.date().equals(d)), "El día contiene adquisiciones.");
            absences.put(d,clock.instant());
        }
        public void undoAbsent(LocalDate d) { editable(); require(absences.remove(d)!=null,"No hay declaración que retirar."); }
        public String dayState(LocalDate d) {
            if (absences.containsKey(d)) return "Sin adquisiciones — declarado";
            return acquisitions.stream().anyMatch(a->a.date().equals(d)) ? "Con adquisiciones" : "Pendiente de registrar";
        }
        public void complete(Clock clock) {
            require(!end().isAfter(LocalDate.now(clock.withZone(ZONE))), "Todavía no han transcurrido las catorce fechas.");
            Set<String> ids=new HashSet<>();
            for (Acquisition a:acquisitions) { dateValid(a.date(),clock); require(ids.add(a.id()),"Identidad duplicada.");
                require(a.author().equals(author),"Autor inválido."); require(!absences.containsKey(a.date()),"Día contradictorio."); }
            for (LocalDate d:absences.keySet()) dateValid(d,clock);
            for (int i=0;i<14;i++) require(!dayState(start.plusDays(i)).equals("Pendiente de registrar"),
                "Fecha pendiente: " + start.plusDays(i));
        }
        public void finish(Clock clock) { editable(); complete(clock); closedAt=clock.instant(); }
        public String state() { return receivedAt!=null ? "Información recibida" : closedAt!=null ? "Pendiente de envío" : "Editable"; }
    }
}
