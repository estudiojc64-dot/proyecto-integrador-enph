package co.edu.udi.enph;

import javafx.application.*;
import javafx.animation.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static co.edu.udi.enph.Domain.*;

/**
 * Cliente JavaFX del punto 12. Monitor para funciones centrales y ventana
 * adaptable para simular tableta Windows. No se afirma soporte Android/iOS.
 * La identidad procede del servidor; no hay un selector libre de roles.
 * Las solicitudes de red corren fuera del hilo gráfico. Las confirmaciones
 * preceden a operaciones de negocio y el envío de RNF-01 es automático.
 */
public final class EnphApp extends Application {
    private final Clock clock=Clock.system(ZONE);
    private final ScheduledExecutorService jobs=Executors.newSingleThreadScheduledExecutor();
    private final Locale spanish=Locale.forLanguageTag("es-CO");
    private final DateTimeFormatter longDate=DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",spanish);
    private final DateTimeFormatter dateTime=DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu HH:mm",spanish).withZone(ZONE);
    private ApiClient api;
    private LocalStore local;
    private Path directory;
    private String device;
    private User actor;
    private List<User> users=List.of();
    private LocalPeriod capture;
    private List<List<String>> centralInfo=List.of();
    private final BorderPane root=new BorderPane();
    private final ComboBox<List<String>> periods=new ComboBox<>();
    private final Label status=new Label("Conectando…"), now=new Label(), transmission=new Label();
    private final TableView<Acquisition> acquisitions=new TableView<>();
    private final TableView<List<String>> days=new TableView<>();
    private final TextArea details=new TextArea();
    private final DatePicker selectedDate=new DatePicker();
    private Timeline ticker;
    private static String setting(String name,String fallback) { return System.getenv().getOrDefault(name,fallback); }
    @Override public void start(Stage stage) throws Exception {
        directory=Path.of(setting("ENPH_LOCAL_DIR",Path.of(System.getProperty("user.home"),".enph-academico").toString()));
        local=new LocalStore(directory);
        Path deviceFile=directory.resolve("dispositivo.txt");
        device=Files.exists(deviceFile)?Files.readString(deviceFile).trim():id(); UUID.fromString(device);
        if(!Files.exists(deviceFile)) Files.writeString(deviceFile,device,StandardOpenOption.CREATE_NEW);
        api=new ApiClient(setting("ENPH_SERVER_URL","http://127.0.0.1:8080/rpc"),Server.env("ENPH_TOKEN"));
        root.setPadding(new Insets(16)); root.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-background-color: #f4f7fb;");
        VBox heading=new VBox(8,new Label("ENPH · Primera tabla D"),now,status);
        periods.setMaxWidth(Double.MAX_VALUE); periods.setPromptText("Seleccione un hogar y período");
        periods.setCellFactory(view->periodCell()); periods.setButtonCell(periodCell());
        Button reload=new Button("Actualizar hogares"); reload.setOnAction(e->refresh());
        heading.getChildren().add(new HBox(10,periods,reload)); root.setTop(heading);
        root.setBottom(transmission); details.setEditable(false); details.setWrapText(true);
        stage.setTitle("Proyecto Integrador IV · ENPH"); stage.setScene(new Scene(root,1180,780)); stage.setMinWidth(800); stage.setMinHeight(650);
        stage.show(); ticker=new Timeline(new KeyFrame(Duration.seconds(1),e->now.setText(dateTime.format(clock.instant())))); ticker.setCycleCount(Animation.INDEFINITE); ticker.play();
        periods.setOnAction(e->select());
        run(()-> {
            try {
                var r=api.call("ME").get(0); Files.write(directory.resolve("sesion.enph"),Wire.encode(List.of(r)));
                return new User(r.get(0),r.get(1),Role.valueOf(r.get(2)));
            } catch(Exception offline) {
                if(!Files.exists(directory.resolve("sesion.enph"))) throw offline;
                var r=Wire.decode(Files.readAllBytes(directory.resolve("sesion.enph"))).get(0);
                require(r.get(2).equals("ENCUESTADOR"),"Se requiere conexión para funciones centrales.");
                return new User(r.get(0),r.get(1),Role.ENCUESTADOR);
            }
        },user->{actor=user; layout(); refresh(); jobs.scheduleWithFixedDelay(this::sendPending,2,8,TimeUnit.SECONDS);});
    }
    private ListCell<List<String>> periodCell() {
        return new ListCell<>() { @Override protected void updateItem(List<String> row,boolean empty) {
            super.updateItem(row,empty); setText(empty||row==null?null:row.get(1)+" · "+(row.get(2).isEmpty()?"Sin programar":longDate.format(LocalDate.parse(row.get(2)))));
        }};
    }
    private void layout() {
        status.setText(actor.name()+" · "+roleName(actor.role()));
        TabPane tabs=new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        if(actor.role()==Role.COORDINADOR) tabs.getTabs().add(new Tab("Responsables y calendario",coordinator()));
        if(actor.role()==Role.ENCUESTADOR) tabs.getTabs().add(new Tab("Captura diaria",capturePane()));
        if(actor.role()==Role.SUPERVISOR || actor.role()==Role.CRITICO) tabs.getTabs().add(new Tab("Información recibida",receivedPane()));
        if(actor.role()==Role.SUPERVISOR) tabs.getTabs().add(new Tab("Acompañamiento",supervisor()));
        if(actor.role()==Role.CRITICO) tabs.getTabs().add(new Tab("Revisión",reviewer()));
        tabs.getTabs().add(new Tab("Historial y seguimiento",details)); root.setCenter(tabs);
    }
    private String roleName(Role role) {
        return switch(role) {case COORDINADOR->"Coordinador";case ENCUESTADOR->"Encuestador";case SUPERVISOR->"Supervisor";case CRITICO->"Crítico-digitador";};
    }
    private VBox panel(Node... nodes) { VBox b=new VBox(12,nodes); b.setPadding(new Insets(18)); return b; }
    private Button button(String label,Runnable action) {
        var b=new Button(label); b.setOnAction(e->{try{action.run();}catch(Exception error){error(error);}}); return b;
    }
    private Node coordinator() {
        return panel(new Label("Establezca el responsable y programe las catorce fechas del hogar seleccionado."),
            button("Asignar o reasignar responsable",()-> {
                String p=periodId(); Form f=new Form("Responsable del hogar"); var responsible=f.user("Encuestador",Role.ENCUESTADOR);
                var reason=f.text("Motivo (obligatorio al reasignar)","");
                f.show(()->mutate("ASSIGN",p,Objects.requireNonNull(responsible.getValue(),"Seleccione encuestador.").id(),reason.getText()));
            }),button("Programar acompañamiento",()-> {
                String p=periodId(); Form f=new Form("Calendario de acompañamiento");
                var start=f.date("Inicio",LocalDate.now(ZONE)); var end=new Label();
                Runnable update=()->end.setText(start.getValue()==null?"": "Final: "+longDate.format(start.getValue().plusDays(13)));
                start.valueProperty().addListener((obs,old,n)->update.run()); update.run(); f.node("Catorce fechas consecutivas",end);
                var visits=f.area("Visitas previstas: una por línea (2026-10-11 09:00)","");
                var reason=f.text("Motivo del cambio (si aplica)","");
                f.show(()-> {
                    List<List<String>> payload=new ArrayList<>(); payload.add(List.of(p,start.getValue().toString(),reason.getText()));
                    for(String line:visits.getText().split("\\R")) if(!line.isBlank()) payload.add(List.of(parseTime(line.trim()).toString()));
                    run(()->api.callRows("PROGRAM",payload),r->{notice(r.get(0).get(0)); refresh();});
                });
            }),new Label("Vigencia de asignación: desde la confirmación. Las programaciones anteriores se conservan."),
            new Label("Una cita prevista no se marca como realizada. La continuidad entre tabletas sigue pendiente."));
    }
    private Node capturePane() {
        configureTables();
        selectedDate.valueProperty().addListener((obs,old,n)->displayLocal());
        days.getSelectionModel().selectedItemProperty().addListener((obs,old,row)->{if(row!=null)selectedDate.setValue(LocalDate.parse(row.get(0)));});
        var buttons=new FlowPane(10,10,
            button("Preparar captura local",this::checkout),button("Agregar adquisición",()->editAcquisition(null)),
            button("Editar selección",()->editAcquisition(Objects.requireNonNull(acquisitions.getSelectionModel().getSelectedItem(),"Seleccione una adquisición."))),
            button("Eliminar selección",()-> {
                var a=Objects.requireNonNull(acquisitions.getSelectionModel().getSelectedItem(),"Seleccione una adquisición.");
                if(confirm("¿Eliminar esta adquisición local antes de finalizar?")) localChange(p->p.remove(a.id()));
            }),button("Declarar sin adquisiciones",()->{if(confirm("¿Confirma que esta fecha no tuvo adquisiciones?"))localChange(p->p.absent(selectedDate.getValue(),clock));}),
            button("Retirar declaración",()->{if(confirm("¿Retirar la declaración de ausencia de esta fecha?"))localChange(p->p.undoAbsent(selectedDate.getValue()));}),
            button("Finalizar período",()-> {
                require(capture!=null,"Prepare primero la captura local."); capture.complete(clock);
                if(confirm("Se comprobaron las catorce fechas. ¿Finalizar y cerrar la edición local? El envío será automático."))localChange(p->p.finish(clock));
            }));
        days.setPrefHeight(210); acquisitions.setPrefHeight(260);
        VBox pane=panel(buttons,new HBox(10,new Label("Fecha de adquisición"),selectedDate),days,acquisitions);
        VBox.setVgrow(acquisitions,Priority.ALWAYS); return pane;
    }
    private Node receivedPane() {
        configureTables(); days.setPrefHeight(220); acquisitions.setPrefHeight(310);
        VBox pane=panel(new Label("Solo se muestran adquisiciones después de confirmar la recepción íntegra."),
            button("Consultar recepción",this::select),days,acquisitions); VBox.setVgrow(acquisitions,Priority.ALWAYS); return pane;
    }
    private void configureTables() {
        if(!days.getColumns().isEmpty()) return;
        for(int i=0;i<4;i++) { final int n=i; TableColumn<List<String>,String> col=new TableColumn<>(List.of("Fecha","Estado","Valor pagado","Valor estimado").get(i));
            col.setCellValueFactory(c->new ReadOnlyStringWrapper(n==0?"Día "+c.getValue().get(4)+" · "+longDate.format(LocalDate.parse(c.getValue().get(n))):c.getValue().get(n)));
            col.setPrefWidth(i==1?300:220); days.getColumns().add(col); }
        column("Fecha",a->longDate.format(a.date()),150); column("Artículo o servicio",Acquisition::description,220);
        column("Cantidad",a->a.quantity().toPlainString(),85); column("Unidad",a->label(UNITS,a.unit())+(a.unitOther().isEmpty()?"":" / "+a.unitOther()),120);
        column("Forma",a->label(FORMS,a.form()),170); column("Valor",a->money(a.value())+(a.paid()?" pagado":" estimado"),140);
        column("Registro local",a->dateTime.format(a.registeredAt()),180);
    }
    private void column(String name,Function<Acquisition,String> value,int width) {
        var c=new TableColumn<Acquisition,String>(name); c.setCellValueFactory(a->new ReadOnlyStringWrapper(value.apply(a.getValue()))); c.setPrefWidth(width); acquisitions.getColumns().add(c);
    }
    private void checkout() {
        String p=periodId();
        try { synchronized(local) { var existing=local.load(p); if(existing!=null) { capture=existing; displayLocal(); return; } }} catch(Exception e){error(e);return;}
        if(!confirm("¿Entregar este período a este dispositivo para captura sin conexión? Se protegerán responsable y fechas hasta su recepción."))return;
        run(()-> {
            var downloaded=LocalStore.from(api.call("CHECKOUT",p,device));
            synchronized(local) { var old=local.load(p); if(old!=null)return old; local.save(downloaded); } return downloaded;
        },value->{capture=value; selectedDate.setValue(capture.start.isAfter(LocalDate.now(ZONE))?capture.start:LocalDate.now(ZONE).isAfter(capture.end())?capture.end():LocalDate.now(ZONE)); displayLocal();});
    }
    private void localChange(Consumer<LocalPeriod> change) {
        try {
            require(capture!=null,"Prepare primero la captura local.");
            synchronized(local) { var copy=local.load(capture.id); change.accept(copy); local.save(copy); capture=copy; }
            displayLocal();
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException("No se confirmó el guardado local. La captura anterior se conserva.",e); }
    }
    private void displayLocal() {
        if(capture==null)return;
        if(selectedDate.getValue()==null) selectedDate.setValue(capture.start);
        days.setItems(FXCollections.observableArrayList(dayRows(capture)));
        acquisitions.setItems(FXCollections.observableArrayList(capture.acquisitions.stream().filter(a->a.date().equals(selectedDate.getValue())).toList()));
        transmission.setText(capture.state()+(capture.receivedAt==null?"":" · "+dateTime.format(capture.receivedAt)));
    }
    private String money(BigDecimal amount) { return java.text.NumberFormat.getCurrencyInstance(spanish).format(amount); }
    private List<List<String>> dayRows(LocalPeriod p) {
        List<List<String>> rows=new ArrayList<>();
        for(int i=0;i<14;i++) { LocalDate d=p.start.plusDays(i); BigDecimal paid=BigDecimal.ZERO,estimated=BigDecimal.ZERO;
            for(var a:p.acquisitions) if(a.date().equals(d)) {if(a.paid())paid=paid.add(a.value());else estimated=estimated.add(a.value());}
            rows.add(List.of(d.toString(),p.dayState(d),money(paid),money(estimated),Integer.toString(i+1))); }
        return rows;
    }
    private void editAcquisition(Acquisition old) {
        require(capture!=null && capture.closedAt==null,"Se requiere captura local editable.");
        LocalDate date=old==null?selectedDate.getValue():old.date(); capture.dateValid(date,clock);
        Form f=new Form(old==null?"Adquisición diaria":"Editar adquisición local");
        var description=f.text("Artículo o servicio",old==null?"":old.description());
        var category=f.choice("Aplicabilidad",Arrays.stream(Category.values()).toList());
        category.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Category c) { if(c==null)return ""; return switch(c) {
                case ALIMENTO_HOGAR->"Alimento para el hogar";case BOLSILLO_RESPONSABLE->"Gasto de bolsillo del responsable";
                case BOLSILLO_MENOR->"Gasto de bolsillo de menor de diez años";case MESADA->"Mesada";
                case EXCLUIDO_D->"Producto o servicio excluido de D";case COMIDA_FUERA_D1->"Comida preparada fuera del hogar (D1)";}; }
            public Category fromString(String s){throw new UnsupportedOperationException();}
        }); category.setValue(old==null?Category.ALIMENTO_HOGAR:old.category());
        var quantity=f.text("Cantidad",old==null?"1":old.quantity().toPlainString());
        var unit=f.option("Unidad",UNITS,old==null?"6":old.unit()); var unitOther=f.text("Otra unidad: especificación",old==null?"":old.unitOther());
        var equivalent=f.text("Equivalencia opcional (solo Otra unidad)",old==null||old.equivalent()==null?"":old.equivalent().toPlainString());
        var equivalentUnit=f.choice("Unidad de equivalencia (G o CC)",List.of("","G","CC")); equivalentUnit.setValue(old==null?"":old.equivalentUnit());
        var form=f.option("Forma de adquisición",FORMS,old==null?"1":old.form()); var formOther=f.text("Otra forma: especificación",old==null?"":old.formOther());
        List<Option> optionalPlaces=new ArrayList<>(); optionalPlaces.add(new Option("","No aplica")); optionalPlaces.addAll(PLACES);
        var place=f.option("Lugar de compra",optionalPlaces,old==null?"6":old.place()); var placeOther=f.text("Otro lugar: especificación",old==null?"":old.placeOther());
        List<Option> optionalFreq=new ArrayList<>(); optionalFreq.add(new Option("","No aplica")); optionalFreq.addAll(FREQUENCIES);
        var frequency=f.option("Frecuencia de compra",optionalFreq,old==null?"3":old.frequency());
        var value=f.text("Valor total pagado o estimado (sin miles)",old==null?"":old.value().toPlainString());
        var expenseClass=f.option("Clasificación",CLASSES,old==null?"1":old.expenseClass());
        var beneficiary=f.text("Persona (mesada o gasto de bolsillo)",old==null?"":old.beneficiary()); var age=f.text("Edad (solo menor de diez años)",old==null?"":old.age());
        form.valueProperty().addListener((obs,prev,n)-> {
            boolean purchase=n!=null && n.code().equals("1"); place.setDisable(!purchase); frequency.setDisable(!purchase); placeOther.setDisable(!purchase);
            if(!purchase) {place.setValue(optionalPlaces.get(0)); frequency.setValue(optionalFreq.get(0)); placeOther.clear();}
        });
        f.show(()-> {
            var a=new Acquisition(old==null?id():old.id(),date,description.getText(),decimal(quantity.getText()),unit.getValue().code(),unitOther.getText(),
                equivalent.getText().isBlank()?null:decimal(equivalent.getText()),equivalentUnit.getValue(),form.getValue().code(),formOther.getText(),
                place.getValue().code(),placeOther.getText(),frequency.getValue().code(),decimal(value.getText()),expenseClass.getValue().code(),category.getValue(),
                beneficiary.getText(),age.getText(),actor.id(),old==null?clock.instant():old.registeredAt());
            boolean repeated=capture.acquisitions.stream().anyMatch(x->!x.id().equals(a.id()) && x.date().equals(a.date()) && x.description().equalsIgnoreCase(a.description()));
            if(repeated && !confirm("Ya existe un artículo con ese nombre en la fecha. ¿Confirma que este es otro renglón válido?"))return;
            localChange(p->{if(old!=null)p.remove(old.id());p.add(a,clock);});
        });
    }
    private Node supervisor() {
        return panel(new Label("Antes de recibir, consulte programación y novedades; los gastos locales no se presentan como verificados."),
            button("Registrar acción de acompañamiento",()-> {
                String p=periodId(); Form f=new Form("Acción de supervisión"); var description=f.text("Acción","");var reason=f.text("Motivo","");
                var responsible=f.user("Responsable",null); var deadline=f.text("Plazo (2026-10-11 09:00)",LocalDate.now(ZONE).plusDays(1)+" 09:00");
                f.show(()->mutate("ACTION",p,description.getText(),reason.getText(),responsible.getValue().id(),parseTime(deadline.getText()).toString()));
            }),button("Registrar seguimiento",()-> {
                String p=periodId(); Form f=new Form("Seguimiento de una acción"); var action=f.rowChoice("Acción",centralInfo.stream().filter(r->r.get(0).equals("Acción")).toList(),2);
                var state=f.choice("Estado",List.of("ABIERTA","EN_SEGUIMIENTO","ATENDIDA"));state.setValue("EN_SEGUIMIENTO");var text=f.area("Seguimiento","");
                f.show(()->mutate("FOLLOW",p,action.getValue().get(1),state.getValue(),text.getText()));
            }),button("Registrar novedad comunicada",()-> {
                String p=periodId();Form f=new Form("Novedad reportada");var type=f.choice("Tipo",List.of("FINALIZACION_REPORTADA","OTRA"));type.setValue("OTRA");
                var text=f.area("Novedad","");var origin=f.text("Origen de la comunicación","");f.show(()->mutate("REPORT",p,type.getValue(),text.getText(),origin.getText()));
            }));
    }
    private Node reviewer() {
        return panel(new Label("Registre hallazgos y aclaraciones. Las adquisiciones recibidas no se editan."),
            button("Observar adquisición seleccionada",()-> {
                var a=Objects.requireNonNull(acquisitions.getSelectionModel().getSelectedItem(),"Seleccione una adquisición en Información recibida.");
                observation("ADQUISICION",a.id());
            }),button("Observar fecha seleccionada",()-> {
                var d=Objects.requireNonNull(days.getSelectionModel().getSelectedItem(),"Seleccione una fecha en Información recibida.");observation("DIA",d.get(0));
            }),button("Registrar aclaración",()-> {
                String p=periodId(); Form f=new Form("Aclaración de hallazgo"); var observation=f.rowChoice("Hallazgo",observations(),4);
                var text=f.area("Explicación recibida","");var origin=f.text("Origen de la explicación","");
                f.show(()->mutate("CLARIFY",p,observation.getValue().get(1),text.getText(),origin.getText()));
            }),button("Evaluar hallazgo",()-> {
                String p=periodId();Form f=new Form("Evaluación del hallazgo");var observation=f.rowChoice("Hallazgo",observations(),4);
                var state=f.choice("Estado",List.of("PENDIENTE_ACLARACION","ACLARADA","OBSERVACION_PERSISTE"));state.setValue("OBSERVACION_PERSISTE");
                var text=f.area("Evaluación","");f.show(()->mutate("EVALUATE",p,observation.getValue().get(1),state.getValue(),text.getText()));
            }),button("Confirmar resultado de revisión",()-> {
                String p=periodId();Form f=new Form("Resultado de la revisión de D");var scope=f.text("Alcance revisado","Primera tabla D, catorce fechas");
                var result=f.choice("Resultado",List.of("PENDIENTE_ACLARACION","REVISADO_CON_OBSERVACIONES","REVISADO_SIN_OBSERVACIONES"));
                result.setValue("REVISADO_SIN_OBSERVACIONES");f.show(()->mutate("REVIEW",p,scope.getText(),result.getValue()));
            }));
    }
    private List<List<String>> observations() { return centralInfo.stream().filter(r->r.get(0).equals("Observación")).toList(); }
    private void observation(String type,String target) {
        String p=periodId();Form f=new Form("Observación sin edición del dato");var text=f.area("Hallazgo y aclaración requerida","");
        f.show(()->mutate("OBSERVE",p,type,target,text.getText()));
    }
    private void refresh() {
        if(actor==null)return;
        run(()-> {
            try { var list=api.call("PERIODS"); var refs=api.call("USERS"); return List.of(list,refs); }
            catch(Exception e) {
                if(actor.role()!=Role.ENCUESTADOR)throw e;
                List<List<String>> rows=new ArrayList<>(); synchronized(local) { for(var p:local.all()) if(p.author.equals(actor.id()))
                    rows.add(List.of(p.id,p.household,p.start.toString(),actor.name(),p.receivedAt==null?"":p.receivedAt.toString(),p.device,"")); }
                return List.of(rows,List.<List<String>>of());
            }
        },result->{String old=periods.getValue()==null?"":periods.getValue().get(0); users=result.get(1).stream().map(r->new User(r.get(0),r.get(1),Role.valueOf(r.get(2)))).toList();
            periods.setItems(FXCollections.observableArrayList(result.get(0))); periods.setValue(result.get(0).stream().filter(r->r.get(0).equals(old)).findFirst().orElse(result.get(0).isEmpty()?null:result.get(0).get(0)));});
    }
    private String periodId() { require(periods.getValue()!=null,"Seleccione un hogar.");return periods.getValue().get(0); }
    private void select() {
        if(actor==null || periods.getValue()==null)return;
        var row=periods.getValue();String p=row.get(0); centralInfo=List.of();details.clear(); acquisitions.getItems().clear();days.getItems().clear(); capture=null;
        if(actor.role()==Role.ENCUESTADOR) {
            try { synchronized(local) {capture=local.load(p);} if(capture!=null){selectedDate.setValue(capture.start);displayLocal();}
                else transmission.setText("Prepare captura local mientras tenga conexión."); } catch(Exception e){error(e);} return;
        }
        String state=row.get(4).isEmpty()?"Información aún no recibida":"Información recibida · "+dateTime.format(Instant.parse(row.get(4)));
        if(row.get(4).isEmpty() && !row.get(2).isEmpty() && LocalDate.now(ZONE).isAfter(LocalDate.parse(row.get(2)).plusDays(13)))
            state="Plazo cumplido — finalización no confirmada";
        transmission.setText(state+(row.get(6).isEmpty()?"":" · "+row.get(6)));
        run(()-> {
            var info=api.call("INFO",p); List<List<String>> data=List.of();
            if(!row.get(4).isEmpty() && (actor.role()==Role.SUPERVISOR || actor.role()==Role.CRITICO))data=api.call("DATA",p);
            return List.of(info,data);
        },result->{
            if(periods.getValue()==null || !periods.getValue().get(0).equals(p))return;
            centralInfo=result.get(0); details.setText(centralInfo.stream().map(r->String.join(" · ",r)).reduce("",(x,y)->x+y+"\n\n"));
            if(row.get(4).isEmpty() && centralInfo.stream().anyMatch(r->r.get(0).equals("Novedad reportada") && r.get(1).equals("FINALIZACION_REPORTADA")))
                transmission.setText("Período finalizado — recepción pendiente (reportado; consulte origen en Historial).");
            if(!row.get(4).isEmpty() && !row.get(2).isEmpty()) {
                var received=new LocalPeriod(p,row.get(1),"consulta",device,LocalDate.parse(row.get(2)));
                for(var r:result.get(1)) if(r.get(0).equals("A"))received.acquisitions.add(Acquisition.from(r.subList(1,r.size())));
                    else received.absences.put(LocalDate.parse(r.get(1)),Instant.parse(r.get(2)));
                days.setItems(FXCollections.observableArrayList(dayRows(received))); acquisitions.setItems(FXCollections.observableArrayList(received.acquisitions));
            }
        });
    }
    private void mutate(String command,String... args) {run(()->api.call(command,args),r->{notice(r.get(0).get(0));refresh();});}
    /** Un único trabajador serializa acceso local y reintentos; el recibo perdido se recupera por identidad y huella. */
    private void sendPending() {
        if(actor==null || actor.role()!=Role.ENCUESTADOR)return;
        try {
            List<LocalPeriod> list; synchronized(local){list=local.all();}
            for(var p:list) if(p.author.equals(actor.id()) && p.closedAt!=null && p.receivedAt==null) {
                var r=api.callRows("RECEIVE",LocalStore.rows(p));Instant receipt=Instant.parse(r.get(0).get(0));
                synchronized(local) {var current=local.load(p.id);current.receivedAt=receipt;local.save(current);}
                Platform.runLater(()->{if(capture!=null && capture.id.equals(p.id)) {try{synchronized(local){capture=local.load(p.id);}displayLocal();}catch(Exception e){error(e);}}});
            }
        } catch(Exception e) {Platform.runLater(()->{if(capture!=null && capture.closedAt!=null && capture.receivedAt==null)
            transmission.setText("Pendiente de envío · "+Objects.toString(e.getMessage(),"Sin confirmación central"));});}
    }
    private <T> void run(Callable<T> work,Consumer<T> done) {
        root.setDisable(true);jobs.execute(()->{try{T result=work.call();Platform.runLater(()->{root.setDisable(false);try{done.accept(result);}catch(Exception e){error(e);}});}
            catch(Exception e){Platform.runLater(()->{root.setDisable(false);error(e);});}});
    }
    private Instant parseTime(String s) {return LocalDateTime.parse(s.trim(),DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")).atZone(ZONE).toInstant();}
    private boolean confirm(String message) {return new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.OK,ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private void notice(String text) {new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK).showAndWait();}
    private void error(Exception e) {new Alert(Alert.AlertType.ERROR,Objects.toString(e.getMessage(),"No se confirmó la operación."),ButtonType.OK).showAndWait();}
    /** Formularios de confirmación: cancelar no invoca ninguna operación. */
    private final class Form {
        final Dialog<ButtonType> dialog=new Dialog<>();final GridPane grid=new GridPane();int row;
        Form(String title) {dialog.setTitle(title);grid.setHgap(12);grid.setVgap(8);grid.setPadding(new Insets(16));
            var scroll=new ScrollPane(grid);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(730);scroll.setPrefViewportHeight(520);
            dialog.getDialogPane().setContent(scroll);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);}
        void node(String label,Node node) {grid.add(new Label(label),0,row);grid.add(node,1,row++);}
        TextField text(String label,String value) {var f=new TextField(value);f.setPrefWidth(370);node(label,f);return f;}
        TextArea area(String label,String value) {var f=new TextArea(value);f.setPrefRowCount(3);f.setWrapText(true);node(label,f);return f;}
        DatePicker date(String label,LocalDate value) {var f=new DatePicker(value);node(label,f);return f;}
        <T> ComboBox<T> choice(String label,List<T> values) {
            var f=new ComboBox<T>(FXCollections.observableArrayList(values));f.setMaxWidth(410);
            if(!values.isEmpty() && values.get(0) instanceof String) f.setConverter(new javafx.util.StringConverter<>() {
                public String toString(T value) { if(value==null)return "";return readable(value.toString()); }
                public T fromString(String value) { throw new UnsupportedOperationException(); }
            });
            node(label,f);return f;
        }
        ComboBox<Option> option(String label,List<Option> options,String selected) {var f=choice(label,options);f.setValue(options.stream().filter(x->x.code().equals(selected)).findFirst().orElse(null));return f;}
        ComboBox<User> user(String label,Role role) {var f=choice(label,users.stream().filter(u->role==null||u.role()==role).toList());return f;}
        ComboBox<List<String>> rowChoice(String label,List<List<String>> values,int textIndex) {
            var f=choice(label,values);f.setConverter(new javafx.util.StringConverter<>(){
                public String toString(List<String> r){return r==null?"":r.get(textIndex);}
                public List<String> fromString(String s){throw new UnsupportedOperationException();}});return f;
        }
        void show(Runnable work) {if(dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK) {try{work.run();}catch(Exception e){error(e);show(work);}}}
    }
    private static String readable(String value) {
        return switch(value) {
            case "FINALIZACION_REPORTADA"->"Finalización reportada";case "OTRA"->"Otra";
            case "ABIERTA"->"Abierta";case "EN_SEGUIMIENTO"->"En seguimiento";case "ATENDIDA"->"Atendida";
            case "PENDIENTE_ACLARACION"->"Pendiente de aclaración";case "ACLARADA"->"Aclarada";
            case "OBSERVACION_PERSISTE"->"Observación persiste";case "REVISADO_CON_OBSERVACIONES"->"Revisado con observaciones";
            case "REVISADO_SIN_OBSERVACIONES"->"Revisado sin observaciones";default->value;
        };
    }
    @Override public void stop() throws Exception {if(ticker!=null)ticker.stop();jobs.shutdownNow();jobs.awaitTermination(3,TimeUnit.SECONDS);if(local!=null)local.close();}
    public static void main(String[] args) {launch(args);}
}
