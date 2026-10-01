package com.empresa.offboarding.bulk;

import com.empresa.offboarding.dto.CreateOffboardingRequest;
import com.empresa.offboarding.entity.TaskTemplate;
import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.repository.AppUserRepository;
import com.empresa.offboarding.repository.TaskTemplateRepository;
import com.empresa.offboarding.service.AuditService;
import com.empresa.offboarding.service.OffboardingService;
import com.empresa.offboarding.service.PremployeeService;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;

@Service
public class BulkWorkflow implements ApplicationRunner {

    public static final String MODULE = "offboarding-r8-admin-validation";

    @PersistenceContext private EntityManager em;

    private final AppUserRepository users;
    private final BulkPreviewService preview;
    private final PremployeeService employees;
    private final TaskTemplateRepository templates;
    private final OffboardingService cases;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private volatile boolean ready;

    public BulkWorkflow(
        AppUserRepository users, BulkPreviewService preview,
        PremployeeService employees, TaskTemplateRepository templates,
        OffboardingService cases, AuditService audit,
        PlatformTransactionManager manager, ObjectMapper mapper
    ) {
        this.users = users;
        this.preview = preview;
        this.employees = employees;
        this.templates = templates;
        this.cases = cases;
        this.audit = audit;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = mapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public record Actor(Long id, String username) {}
    public record Edit(
        int excelRow, Map<String,String> values, boolean included,
        boolean reviewed, String fingerprint, Set<String> personalized
    ) {}
    public record Input(long version, String name, List<Edit> rows) {}
    public record Task(
        String system, String name, boolean critical,
        boolean automatic, OffsetDateTime dueAt
    ) {}
    public record Item(
        int excelRow, Map<String,String> values,
        BulkPreviewService.EmployeeData employee,
        CreateOffboardingRequest form,
        List<String> issues, List<String> warnings, List<Task> tasks,
        boolean included, boolean reviewed, String fingerprint,
        Set<String> personalized
    ) {}
    public record Document(List<Item> rows) {}
    public record Result(
        int excelRow, String state, Long caseId, String caseNumber, String error
    ) {}
    public record Batch(
        String id, Long ownerId, long version, String phase, String name,
        Document document, List<Result> results
    ) {}
    public record Summary(
        String id, String name, String phase, String updatedAt,
        long total, long registered, long failed
    ) {}

    @Override
    public void run(ApplicationArguments args) {
        transaction(() -> {
            /* BULK_DELIVERY_STARTUP_CHECK_R3 */
            q("SELECT batch_id,upload_hash,state,payload,subject,attempt_token FROM bulk_import_delivery WHERE FALSE").getResultList();
            q("SELECT id,owner_id,phase,version,document,last_hash " +
              "FROM bulk_import_batch WHERE FALSE").getResultList();
            q("SELECT batch_id,row_no,payload,state,case_id,case_number,error " +
              "FROM bulk_import_row WHERE FALSE").getResultList();
            q("SELECT month_key,last_value " +
              "FROM offboarding_case_monthly_counter WHERE FALSE").getResultList();
            return null;
        });

        // Consulta de lectura; no crea un caso ni envia correos.
        employees.findByEmployeeNum(0);
        ready = true;
    }

    public boolean isReady() { return ready; }

    public Actor actor(Authentication auth) {
        if (!ready) throw status(503, "El modulo aun no esta disponible.");
        if (auth == null || !auth.isAuthenticated() ||
            !(auth.getPrincipal() instanceof BulkPrincipal principal) ||
            principal.getUserId() == null) {
            throw status(401, "Cierra sesion e inicia nuevamente para usar el importador.");
        }

        boolean allowed = auth.getAuthorities().stream().anyMatch(a ->
            "ROLE_ADMIN".equals(a.getAuthority()) ||
            "ROLE_RECURSOS_HUMANOS".equals(a.getAuthority())
        );
        if (!allowed) throw status(403, "No tienes permiso para importar bajas.");

        var user = users.findById(principal.getUserId())
            .orElseThrow(() -> status(401, "La cuenta ya no esta disponible."));

        if (!user.isEnabled() ||
            !(user.getRoles().contains(AppRole.ADMIN) ||
              user.getRoles().contains(AppRole.RECURSOS_HUMANOS))) {
            throw status(403, "La cuenta no esta habilitada para esta operacion.");
        }
        if (!user.getUsername().equals(principal.getUsername())) {
            throw status(401, "Cambio la cuenta. Inicia sesion nuevamente.");
        }
        return new Actor(user.getId(), user.getUsername());
    }

    public Batch upload(
        BulkPreviewService.Preview prepared, String name, Authentication auth
    ) {
        Actor owner = actor(auth);
        List<Edit> edits = new ArrayList<>();
        for (var r : prepared.rows()) {
            edits.add(new Edit(
                r.excelRow(), r.excelValues(), true, false, null, Set.of()
            ));
        }
        Document doc = assemble(prepared, edits);
        String id = prepared.previewId().toString();
        return persist(id, owner, 0, name, doc, hash(edits));
    }

    public Batch save(UUID id, Input input, Authentication auth) {
        Actor owner = actor(auth);
        validateInput(input);
        String requestHash = hash(input);

        Batch existing = transaction(() -> {
            Object[] raw = rawBatch(id.toString(), owner, false);
            if (!"DRAFT".equals(raw[0])) {
                throw status(409, "El lote confirmado ya no puede editarse.");
            }
            if (((Number)raw[1]).longValue() != input.version()) {
                if (requestHash.equals(raw[4])) {
                    return view(id.toString(), owner, raw);
                }
                throw status(409, "Otro proceso modifico el lote. Abre su ultima version.");
            }
            return null;
        });
        if (existing != null) return existing;

        Document doc = rebuild(input.rows(), auth);
        return persist(
            id.toString(), owner, input.version(), input.name(), doc, requestHash
        );
    }

    public Batch get(UUID id, Authentication auth) {
        Actor owner = actor(auth);
        return transaction(() ->
            view(id.toString(), owner, rawBatch(id.toString(), owner, false))
        );
    }

    public List<Summary> list(int page, Authentication auth) {
        Actor owner = actor(auth);
        if (page < 0 || page > 10000) throw status(400, "Pagina invalida.");

        return transaction(() -> {
            List<Summary> result = new ArrayList<>();
            List<?> rows = q("""
                SELECT b.id,b.name,b.phase,b.updated_at::text,
                    (SELECT COUNT(*) FROM bulk_import_row r WHERE r.batch_id=b.id AND r.state<>'DELETED'),
                    (SELECT COUNT(*) FROM bulk_import_row r
                        WHERE r.batch_id=b.id AND r.state='REGISTERED'),
                    (SELECT COUNT(*) FROM bulk_import_row r
                        WHERE r.batch_id=b.id AND r.state='FAILED')
                FROM bulk_import_batch b WHERE b.owner_id=:owner
                ORDER BY b.updated_at DESC LIMIT 50 OFFSET :offset
                """, "owner", owner.id(), "offset", page * 50).getResultList();

            for (Object value : rows) {
                Object[] r = (Object[]) value;
                result.add(new Summary(
                    (String)r[0], (String)r[1], (String)r[2], (String)r[3],
                    ((Number)r[4]).longValue(),
                    ((Number)r[5]).longValue(),
                    ((Number)r[6]).longValue()
                ));
            }
            return result;
        });
    }

    public Batch confirm(UUID id, long version, Authentication auth) {
        Actor owner = actor(auth);
        return transaction(() -> {
            String key = id.toString();
            Object[] raw = rawBatch(key, owner, true);
            if ("CONFIRMED".equals(raw[0])) return view(key, owner, raw);
            if (((Number)raw[1]).longValue() != version) {
                throw status(409, "El lote cambio. Recarga antes de confirmar.");
            }

            Document doc = decode((String)raw[3], Document.class);
            List<Item> included = doc.rows().stream().filter(Item::included).toList();

            if (included.isEmpty() || included.stream().anyMatch(r ->
                !r.reviewed() || r.form() == null || !r.issues().isEmpty()
            )) {
                throw status(409, "Todas las filas incluidas deben estar validadas y revisadas.");
            }

            for (Item row : included) {
                q("""
                    INSERT INTO bulk_import_row(batch_id,row_no,payload,state)
                    VALUES(:id,:row,:payload,'PENDING')
                    """, "id", key, "row", row.excelRow(),
                    "payload", encode(row)).executeUpdate();
            }

            q("""
                UPDATE bulk_import_batch SET phase='CONFIRMED',
                    version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, "id", key).executeUpdate();

            return view(key, owner, rawBatch(key, owner, false));
        });
    }

    public Result register(UUID id, int rowNumber, Authentication auth) {
        Actor owner = actor(auth);
        String key = id.toString();

        Object[] initial = transaction(() -> rawRow(key, rowNumber, owner, false));
        if ("DELETED".equals(initial[1])) throw status(409,"This row was permanently removed.");
        // R7_DELETED_ROWS
        if ("REGISTERED".equals(initial[1])) return result(rowNumber, initial);

        Item frozen = decode((String)initial[0], Item.class);

        try {
            Edit edit = new Edit(
                frozen.excelRow(), frozen.values(), true, false,
                frozen.fingerprint(), frozen.personalized()
            );
            Item fresh = rebuild(List.of(edit), auth).rows().get(0);

            return transaction(() -> {
                Object[] current = rawRow(key, rowNumber, owner, true);
                if ("DELETED".equals(current[1])) throw status(409,"This row was permanently removed.");
                if ("REGISTERED".equals(current[1])) return result(rowNumber, current);

                int number = new BigInteger(
                    frozen.values().get("EMPLOYEE NUMBER")
                ).intValueExact();

                // Coordina registros masivos del mismo empleado.
                q("SELECT 1 FROM pg_advisory_xact_lock(194725, :employee)",
                    "employee", number).getSingleResult();

                if (!fresh.issues().isEmpty()) {
                    return fail(key, rowNumber, String.join("; ", fresh.issues()));
                }
                if (!Objects.equals(frozen.fingerprint(), fresh.fingerprint())) {
                    return fail(key, rowNumber,
                        "Cambiaron los datos o las tareas. Crea un nuevo lote con los pendientes para revisarlos.");
                }
                if (!activeKeys(List.of(String.valueOf(number))).isEmpty()) {
                    return fail(key, rowNumber, "El empleado ya tiene una baja activa.");
                }

                // Caso, tareas, auditoria y resultado se guardan en la misma transaccion.
                var created = cases.create(frozen.form(), owner.username());

                q("""
                    UPDATE bulk_import_row
                    SET state='REGISTERED',case_id=:caseId,case_number=:number,
                        error=NULL,updated_at=CURRENT_TIMESTAMP
                    WHERE batch_id=:id AND row_no=:row
                    """, "caseId", created.id(), "number", created.caseNumber(),
                    "id", key, "row", rowNumber).executeUpdate();

                audit.record(
                    owner.username(), "BULK_CASE_LINKED", "OffboardingCase",
                    created.id(), "lote=" + key + "; filaExcel=" + rowNumber
                );

                touch(key);
                return new Result(
                    rowNumber, "REGISTERED", created.id(), created.caseNumber(), null
                );
            });
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(BulkWorkflow.class)
                .warn("Fallo en lote {} fila {}", key, rowNumber, ex);

            return transaction(() -> {
                Object[] current = rawRow(key, rowNumber, owner, true);
                if ("DELETED".equals(current[1])) throw status(409,"This row was permanently removed.");
                if ("REGISTERED".equals(current[1])) return result(rowNumber, current);
                return fail(key, rowNumber,
                    "No se completo el registro. Consulta el log y reintenta esta misma fila; no crees otro caso manualmente.");
            });
        }
    }

    public Batch copyPending(UUID id, Authentication auth) {
        Batch old = get(id, auth);
        Set<Integer> completed = new HashSet<>();
        for (Result r : old.results()) {
            if (Set.of("REGISTERED","DELETED").contains(r.state())) completed.add(r.excelRow());
        }
        List<Edit> edits = new ArrayList<>();
        for (Item r : old.document().rows()) {
            if (r.included() && !completed.contains(r.excelRow())) {
                edits.add(new Edit(
                    r.excelRow(), r.values(), true, false, null, r.personalized()
                ));
            }
        }
        if (edits.isEmpty()) throw status(409, "No quedan filas pendientes.");
        Document doc = rebuild(edits, auth);
        return persist(
            UUID.randomUUID().toString(), actor(auth), 0,
            "Pendientes - " + old.name(), doc, hash(edits)
        );
    }

    private Batch persist(
        String id, Actor owner, long version, String name,
        Document doc, String requestHash
    ) {
        String title = name == null || name.isBlank() ? "Importacion" : name.trim();
        if (title.length() > 180) title = title.substring(0, 180);
        final String safeName = title;

        return transaction(() -> {
            if (version == 0) {
                q("""
                    INSERT INTO bulk_import_batch(
                        id,owner_id,owner_username,name,document,last_hash
                    ) VALUES(:id,:owner,:username,:name,:doc,:hash)
                    ON CONFLICT(id) DO NOTHING
                    """, "id", id, "owner", owner.id(), "username", owner.username(),
                    "name", safeName, "doc", encode(doc), "hash", requestHash)
                    .executeUpdate();

                Object[] raw = rawBatch(id, owner, true);
                if (!requestHash.equals(raw[4])) {
                    throw status(409, "El identificador del lote ya esta en uso.");
                }
                return view(id, owner, raw);
            }

            Object[] raw = rawBatch(id, owner, true);
            if (!"DRAFT".equals(raw[0])) throw status(409, "El lote ya fue confirmado.");

            if (((Number)raw[1]).longValue() != version) {
                if (requestHash.equals(raw[4])) return view(id, owner, raw);
                throw status(409, "Otro proceso modifico el lote.");
            }

            q("""
                UPDATE bulk_import_batch SET name=:name,document=:doc,last_hash=:hash,
                    version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, "name", safeName, "doc", encode(doc),
                "hash", requestHash, "id", id).executeUpdate();

            return view(id, owner, rawBatch(id, owner, false));
        });
    }

    private Document rebuild(List<Edit> edits, Authentication auth) {
        validateInput(new Input(0, "", edits));
        List<Edit> included = edits.stream().filter(Edit::included).toList();
        BulkPreviewService.Preview prepared = null;

        if (!included.isEmpty()) {
            try (XSSFWorkbook book = new XSSFWorkbook();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                var sheet = book.createSheet("BAJAS");
                var header = sheet.createRow(0);

                for (int c=0;c<BulkExcelReader.HEADERS.size();c++) {
                    header.createCell(c).setCellValue(BulkExcelReader.HEADERS.get(c));
                }
                for (Edit edit : included) {
                    var row = sheet.createRow(edit.excelRow()-1);
                    for (int c=0;c<BulkExcelReader.HEADERS.size();c++) {
                        String value = edit.values().get(BulkExcelReader.HEADERS.get(c));
                        row.createCell(c).setCellValue(value == null ? "" : value);
                    }
                }
                book.write(output);
                prepared = preview.prepare(output.toByteArray(), auth);
            } catch (java.io.IOException ex) {
                throw new IllegalArgumentException("No se pudieron preparar las filas.");
            }
        }
        return assemble(prepared, edits);
    }

    private Document assemble(BulkPreviewService.Preview prepared, List<Edit> edits) {
        Map<Integer,BulkPreviewService.PreviewRow> parsed = new HashMap<>();
        if (prepared != null) {
            for (var row : prepared.rows()) parsed.put(row.excelRow(), row);
        }

        List<TaskTemplate> catalog = templates.findByActiveTrueOrderByIdAsc();
        Set<String> numbers = new HashSet<>();
        for (var row : parsed.values()) {
            if (row.form() != null) {
                numbers.add(new BigInteger(row.form().employeeIdentifier()).toString());
            }
        }
        Set<String> active = transaction(() -> activeKeys(numbers));
        List<Item> result = new ArrayList<>();

        for (Edit edit : edits) {
            Set<String> personal = edit.personalized() == null
                ? Set.of() : Set.copyOf(edit.personalized());

            if (!edit.included()) {
                result.add(new Item(
                    edit.excelRow(), edit.values(), null, null,
                    List.of(), List.of(), List.of(), false, false, "", personal
                ));
                continue;
            }

            var source = parsed.get(edit.excelRow());
            if (source == null) throw status(400, "Falta una fila en la revision.");

            List<String> issues = new ArrayList<>(source.issues());
            List<Task> plan = new ArrayList<>();

            if (source.form() != null) {
                plan = plan(source.form(), catalog, issues);
                String employee = new BigInteger(
                    source.form().employeeIdentifier()
                ).toString();
                if (active.contains(employee)) {
                    issues.add("El empleado ya tiene una baja activa.");
                }
            }

            String fingerprint = hash(Arrays.asList(
                new TreeMap<>(source.excelValues()), source.employee(), plan
            ));

            result.add(new Item(
                edit.excelRow(), source.excelValues(), source.employee(), source.form(),
                List.copyOf(issues), source.warnings(), List.copyOf(plan), true,
                edit.reviewed() && issues.isEmpty() &&
                    fingerprint.equals(edit.fingerprint()),
                fingerprint, personal
            ));
        }
        return new Document(List.copyOf(result));
    }

    static List<Task> plan(
        CreateOffboardingRequest form, List<TaskTemplate> catalog, List<String> issues
    ) {
        Set<String> codes = new HashSet<>();
        if (form.computerAssigned()) codes.add("COMPUTER");
        if (form.phoneAssigned()) codes.add("PHONE");
        if (form.accesses() != null) {
            form.accesses().stream().map(Enum::name)
                .filter(v -> !"OTHER".equals(v)).forEach(codes::add);
        }

        Set<String> found = new HashSet<>();
        List<Task> tasks = new ArrayList<>();

        for (TaskTemplate t : catalog) {
            if (!codes.contains(t.getSelectionCode())) continue;
            found.add(t.getSelectionCode());
            String reference = "COMPUTER".equals(t.getSelectionCode())
                ? form.computerDetails()
                : "PHONE".equals(t.getSelectionCode()) ? form.phoneDetails() : null;
            String name = reference(t.getTaskName(), reference);
            tasks.add(new Task(
                t.getSystemName(), name, t.isCritical(), t.isAutomatic(),
                form.effectiveAt().plusHours(t.getSlaHours())
            ));
        }

        codes.removeAll(found);
        if (!codes.isEmpty()) issues.add("Faltan plantillas activas: " + new TreeSet<>(codes));

        if (form.accesses() != null &&
            form.accesses().stream().anyMatch(a -> "OTHER".equals(a.name()))) {
            tasks.add(new Task(
                "Otros accesos",
                reference("Revocar accesos adicionales especificados por RH", form.otherAccesses()),
                false, false, form.effectiveAt().plusHours(4)
            ));
        }

        if (form.fingerprintRegistered() || form.faceidRegistered()) {
            String name = form.fingerprintRegistered() && form.faceidRegistered()
                ? "huella y foto en equipos FaceID y relojes checadores"
                : form.fingerprintRegistered()
                    ? "huella en relojes checadores" : "foto en equipos FaceID";
            tasks.add(new Task(
                "Control de accesos", "Dar de baja " + name + ", revocar accesos biometricos",
                true, false, form.effectiveAt().plusHours(4)
            ));
        }
        return tasks;
    }

    private static String reference(String name, String value) {
        if (value == null || value.isBlank()) return name;
        String full = name + " \u2014 " + value.trim();
        return full.length() > 200 ? full.substring(0,200) : full;
    }

    static void validateInput(Input input) {
        if (input == null || input.rows() == null ||
            input.rows().isEmpty() || input.rows().size() > 1000) {
            throw new IllegalArgumentException("El lote debe contener entre 1 y 1000 filas.");
        }
        Set<Integer> positions = new HashSet<>();
        long chars = 0;
        for (Edit row : input.rows()) {
            if (row == null || row.values() == null ||
                row.excelRow() < 2 || row.excelRow() > 1001 ||
                !positions.add(row.excelRow()) ||
                !BulkExcelReader.HEADERS.containsAll(row.values().keySet())) {
                throw new IllegalArgumentException("El lote contiene filas o campos invalidos.");
            }
            if (row.personalized() != null &&
                !BulkExcelReader.HEADERS.containsAll(row.personalized())) {
                throw new IllegalArgumentException("Hay excepciones de campos desconocidos.");
            }
            for (String value : row.values().values()) {
                if (value != null) {
                    if (value.length() > 4001) throw new IllegalArgumentException("Celda demasiado larga.");
                    chars += value.length();
                }
            }
        }
        if (chars > 2_000_000) throw new IllegalArgumentException("Divide el archivo: contiene demasiado texto.");
    }

    private Set<String> activeKeys(Collection<String> keys) {
        if (keys.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        for (Object value : q("""
            SELECT DISTINCT ltrim(btrim(employee_identifier),'0')
            FROM offboarding_case
            WHERE status IN ('PROGRAMADA','EN_PROCESO')
              AND ltrim(btrim(employee_identifier),'0') IN (:keys)
            """, "keys", new ArrayList<>(keys)).getResultList()) {
            result.add(value.toString());
        }
        return result;
    }

    private Object[] rawBatch(String id, Actor owner, boolean lock) {
        List<?> rows = q(
            "SELECT phase,version,name,document,last_hash FROM bulk_import_batch " +
            "WHERE id=:id AND owner_id=:owner" + (lock ? " FOR UPDATE" : ""),
            "id", id, "owner", owner.id()
        ).getResultList();
        if (rows.isEmpty()) throw status(404, "Lote no encontrado.");
        return (Object[])rows.get(0);
    }

    private Object[] rawRow(String id, int row, Actor owner, boolean lock) {
        List<?> rows = q(
            "SELECT r.payload,r.state,r.case_id,r.case_number,r.error " +
            "FROM bulk_import_row r JOIN bulk_import_batch b ON b.id=r.batch_id " +
            "WHERE b.id=:id AND b.owner_id=:owner AND b.phase='CONFIRMED' " +
            "AND r.row_no=:row" + (lock ? " FOR UPDATE OF r" : ""),
            "id", id, "owner", owner.id(), "row", row
        ).getResultList();
        if (rows.isEmpty()) throw status(404, "Fila confirmada no encontrada.");
        return (Object[])rows.get(0);
    }

    private Batch view(String id, Actor owner, Object[] raw) {
        List<Result> results = new ArrayList<>();
        for (Object value : q(
            "SELECT row_no,state,case_id,case_number,error FROM bulk_import_row " +
            "WHERE batch_id=:id AND state<>'DELETED' ORDER BY row_no", "id", id
        ).getResultList()) {
            Object[] r = (Object[])value;
            results.add(new Result(
                ((Number)r[0]).intValue(), (String)r[1],
                r[2] == null ? null : ((Number)r[2]).longValue(),
                (String)r[3], (String)r[4]
            ));
        }
        return new Batch(
            id, owner.id(), ((Number)raw[1]).longValue(), (String)raw[0],
            (String)raw[2], decode((String)raw[3], Document.class), results
        );
    }

    private Result result(int row, Object[] r) {
        return new Result(
            row, (String)r[1],
            r[2] == null ? null : ((Number)r[2]).longValue(),
            (String)r[3], (String)r[4]
        );
    }

    private Result fail(String id, int row, String message) {
        String safe = message.length() > 500 ? message.substring(0,500) : message;
        q("""
            UPDATE bulk_import_row SET state='FAILED',error=:error,
                updated_at=CURRENT_TIMESTAMP
            WHERE batch_id=:id AND row_no=:row AND state NOT IN ('REGISTERED','DELETED')
            """, "error", safe, "id", id, "row", row).executeUpdate();
        touch(id);
        return new Result(row, "FAILED", null, null, safe);
    }

    private void touch(String id) {
        q("UPDATE bulk_import_batch SET updated_at=CURRENT_TIMESTAMP WHERE id=:id",
            "id", id).executeUpdate();
    }

    private Query q(String sql, Object... parameters) {
        Query query = em.createNativeQuery(sql);
        for (int i=0;i<parameters.length;i+=2) {
            query.setParameter((String)parameters[i], parameters[i+1]);
        }
        return query;
    }

    private <T> T transaction(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("No se pudo serializar el lote.", ex); }
    }

    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception ex) { throw new IllegalStateException("No se pudo leer el lote.", ex); }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(encode(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static ResponseStatusException status(int code, String reason) {
        return new ResponseStatusException(HttpStatus.valueOf(code), reason);
    }

    /* BULK_SIMPLE_WORKFLOW_R3 */
    @org.springframework.beans.factory.annotation.Autowired
    private BulkMailTransport bulkMail;

    @org.springframework.beans.factory.annotation.Autowired
    private com.empresa.offboarding.service.NotificationRecipientService bulkRecipients;

    public record SimpleResult(
        Batch batch, boolean registered, String mailState,
        String message, Set<Integer> attention
    ) {}

    private Object[] delivery(String id, Actor owner, boolean lock) {
        List<?> rows=q(
            "SELECT d.state,d.payload,d.upload_hash,d.subject " +
            "FROM bulk_import_delivery d JOIN bulk_import_batch b ON b.id=d.batch_id " +
            "WHERE b.id=:id AND b.owner_id=:owner" +
            (lock ? " FOR UPDATE OF d" : ""),
            "id",id,"owner",owner.id()).getResultList();
        if(rows.isEmpty()) throw status(404,"Lote de Bulk Offboarding no encontrado.");
        return (Object[])rows.get(0);
    }

    public SimpleResult simpleGet(UUID id, Authentication auth) {
        Actor owner=actor(auth);
        return transaction(()->{
            Object[] raw=rawBatch(id.toString(),owner,false);
            Object[] d=delivery(id.toString(),owner,false);
            return new SimpleResult(
                view(id.toString(),owner,raw),d[1]!=null,(String)d[0],"",Set.of());
        });
    }

    public SimpleResult simpleUpload(
        UUID requestId, byte[] bytes, String name, Authentication auth
    ) {
        Actor owner=actor(auth);
        String id=requestId.toString();
        String digest;
        try {
            digest=HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch(Exception ex) { throw new IllegalStateException(ex); }

        boolean existing=transaction(()->!q(
            "SELECT batch_id FROM bulk_import_delivery WHERE batch_id=:id",
            "id",id).getResultList().isEmpty());

        if(existing) {
            transaction(()->{
                Object[] d=delivery(id,owner,false);
                if(!digest.equals(d[2])) throw status(409,"El identificador corresponde a otro archivo.");
                return null;
            });
            return simpleGet(requestId,auth);
        }

        var prepared=preview.prepare(bytes,auth);
        List<Edit> edits=new ArrayList<>();
        for(var row:prepared.rows()) {
            edits.add(new Edit(
                row.excelRow(),row.excelValues(),true,false,null,Set.of()));
        }
        Document doc=assemble(prepared,edits);
        String fileName=name==null?"Bulk Offboarding":name;
        if(fileName.length()>180) fileName=fileName.substring(0,180);
        final String title=fileName;

        transaction(()->{
            int inserted=q("""
                INSERT INTO bulk_import_batch
                    (id,owner_id,owner_username,name,document,last_hash)
                VALUES(:id,:owner,:username,:name,:doc,:hash)
                ON CONFLICT(id) DO NOTHING
                ""","id",id,"owner",owner.id(),"username",owner.username(),
                "name",title,"doc",encode(doc),"hash",digest).executeUpdate();

            rawBatch(id,owner,true);
            if(inserted==1) {
                q("""
                    INSERT INTO bulk_import_delivery(batch_id,upload_hash,state)
                    VALUES(:id,:hash,'NEW')
                    ""","id",id,"hash",digest).executeUpdate();
            }
            Object[] d=delivery(id,owner,true);
            if(!digest.equals(d[2])) throw status(409,"El identificador corresponde a otro archivo.");
            return null;
        });
        return simpleGet(requestId,auth);
    }

    /* R4_SIMPLE_VALIDATE */
    public SimpleResult simpleValidate(UUID id, Input input, Authentication auth) {
        validateInput(input);
        SimpleResult existing=simpleGet(id,auth);
        if(existing.registered()) return existing;
        Actor owner=actor(auth);

        Map<Integer,String> omitted=r4Omissions(id,owner);
        Set<Integer> expected=new HashSet<>();
        for(Item row:existing.batch().document().rows()) expected.add(row.excelRow());

        Set<Integer> received=new HashSet<>();
        List<Edit> edits=new ArrayList<>();
        for(Edit row:input.rows()) {
            received.add(row.excelRow());
            boolean included=!Objects.equals(
                omitted.get(row.excelRow()),r4Identifier(row.values()));
            edits.add(new Edit(row.excelRow(),row.values(),included,false,null,Set.of()));
        }

        if(!expected.equals(received))
            throw status(400,"No se pueden omitir ni agregar filas por fuera del flujo autorizado.");

        save(id,new Input(input.version(),existing.batch().name(),edits),auth);
        return simpleGet(id,auth);
    }
    public SimpleResult simpleRegister(UUID uuid, long version, Authentication auth) {
        Actor owner=actor(auth);
        String id=uuid.toString();
        SimpleResult initial=simpleGet(uuid,auth);
        if(initial.registered()) return initial;

        Batch before=initial.batch();
        if(before.version()!=version) throw status(409,"El lote cambio. Actualiza su estado.");

        Map<Integer,String> omitted=r4Omissions(uuid,owner);
        Map<Integer,Item> old=new HashMap<>();
        List<Edit> edits=new ArrayList<>();

        for(Item row:before.document().rows()) {
            old.put(row.excelRow(),row);
            boolean included=!Objects.equals(
                omitted.get(row.excelRow()),r4Identifier(row.values()));
            edits.add(new Edit(row.excelRow(),row.values(),included,false,null,Set.of()));
        }

        Document fresh=rebuild(edits,auth);
        Set<Integer> changed=new HashSet<>();
        boolean invalid=false;

        for(Item row:fresh.rows()) {
            if(!row.included()) continue;
            if(row.form()==null||!row.issues().isEmpty()) invalid=true;
            if(!Objects.equals(old.get(row.excelRow()).fingerprint(),row.fingerprint()))
                changed.add(row.excelRow());
        }

        if(invalid||!changed.isEmpty()) {
            Batch updated=persist(id,owner,version,before.name(),fresh,hash(edits));
            return new SimpleResult(updated,false,"NEW",
                invalid?"Corrige los datos indicados antes de registrar.":
                    "Se actualizaron datos. Revisa las filas indicadas.",changed);
        }

        transaction(()->{
            Object[] raw=rawBatch(id,owner,true);
            Object[] d=delivery(id,owner,true);
            if(d[1]!=null) return null;
            if(!"DRAFT".equals(raw[0])||((Number)raw[1]).longValue()!=version)
                throw status(409,"El lote cambio. Actualiza su estado.");

            TreeSet<Integer> numbers=new TreeSet<>();
            for(Item row:fresh.rows()) {
                if(row.included())
                    numbers.add(new BigInteger(row.form().employeeIdentifier()).intValueExact());
            }

            for(int number:numbers)
                q("SELECT 1 FROM pg_advisory_xact_lock(194725,:employee)",
                    "employee",number).getSingleResult();

            List<String> keys=numbers.stream().map(String::valueOf).toList();
            if(!keys.isEmpty()&&!activeKeys(keys).isEmpty())
                throw status(409,"Un empleado ya tiene una baja activa. Valida nuevamente.");

            List<Map<String,Object>> summary=new ArrayList<>();
            int skipped=0;

            for(Item row:fresh.rows()) {
                if(!row.included()) { skipped++;continue; }

                var created=cases.createForBulk(row.form(),owner.username());

                q("""
                    INSERT INTO bulk_import_row
                        (batch_id,row_no,payload,state,case_id,case_number)
                    VALUES(:id,:row,:payload,'REGISTERED',:caseId,:number)
                    ""","id",id,"row",row.excelRow(),"payload",encode(row),
                    "caseId",created.id(),"number",created.caseNumber()).executeUpdate();

                audit.record(owner.username(),"BULK_CASE_LINKED","OffboardingCase",
                    created.id(),"lote="+id+"; filaExcel="+row.excelRow());

                Map<String,Object> entry=new LinkedHashMap<>();
                entry.put("Folio",created.caseNumber());
                entry.put("Empleado",created.employeeName());
                entry.put("Numero de empleado",created.employeeIdentifier());
                entry.put("Departamento",created.department());
                entry.put("Area",created.workArea());
                entry.put("Tipo de baja",created.terminationType());
                entry.put("Fecha efectiva",created.effectiveAt().toString());
                entry.put("Cantidad de tareas",created.tasks().size());
                entry.put("Tareas",em.find(
                    com.empresa.offboarding.entity.OffboardingCase.class,created.id()
                ).getTasks().stream()
                    .map(com.empresa.offboarding.entity.OffboardingTask::getTaskName)
                    .collect(java.util.stream.Collectors.joining(" | ")));
                entry.put("Solicitado por",owner.username());
                summary.add(entry);
            }

            q("""
                UPDATE bulk_import_batch SET phase='CONFIRMED',version=version+1,
                    document=:doc,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                ""","id",id,"doc",encode(fresh)).executeUpdate();

            q("""
                UPDATE bulk_import_delivery SET state=:state,payload=:payload,
                    subject=:subject,updated_at=CURRENT_TIMESTAMP WHERE batch_id=:id
                ""","id",id,"state",summary.isEmpty()?"NOT_NEEDED":"PENDING",
                "payload",encode(summary),
                "subject","Bulk Offboarding | "+summary.size()+" registradas | "+
                    skipped+" omitidas | "+id).executeUpdate();

            return null;
        });
        return simpleGet(uuid,auth);
    }
    public SimpleResult simpleSendMail(UUID uuid, Authentication auth) {
        Actor owner=actor(auth);
        String id=uuid.toString();
        String token=UUID.randomUUID().toString();

        Object[] snapshot=transaction(()->{
            Object[] d=delivery(id,owner,true);
            if(d[1]==null || !Set.of(
                "PENDING","DISABLED","NO_RECIPIENTS","CONFIG_ERROR"
            ).contains((String)d[0])) return null;

            q("""
                UPDATE bulk_import_delivery SET state='SENDING',
                    attempt_token=:token,updated_at=CURRENT_TIMESTAMP
                WHERE batch_id=:id
                ""","id",id,"token",token).executeUpdate();
            return d;
        });

        if(snapshot==null) return simpleGet(uuid,auth);

        String outcome;
        try {
            List<Map<String,Object>> rows=json.readValue(
                (String)snapshot[1],
                new com.fasterxml.jackson.core.type.TypeReference<
                    List<Map<String,Object>>>() {});
            outcome=bulkMail.send(
                rows,(String)snapshot[3],
                bulkRecipients.getActiveRecipientsAsString(),id);
        } catch(Exception ex) {
            // No repetir automaticamente: SMTP pudo haber aceptado el mensaje.
            outcome="UNKNOWN";
            org.slf4j.LoggerFactory.getLogger(BulkWorkflow.class)
                .error("Resultado de correo no confirmado para lote {}",id,ex);
        }

        final String finalState=outcome;
        transaction(()->{
            q("""
                UPDATE bulk_import_delivery SET state=:state,
                    updated_at=CURRENT_TIMESTAMP
                WHERE batch_id=:id AND attempt_token=:token AND state='SENDING'
                ""","state",finalState,"id",id,"token",token).executeUpdate();
            return null;
        });
        return simpleGet(uuid,auth);
    }

    /* R4_OMISSION_HELPERS */
    @org.springframework.beans.factory.annotation.Autowired
    private com.empresa.offboarding.service.PremployeeService r4Directory;

    private static String r4Identifier(Map<String,String> values) {
        return Objects.toString(values.get("EMPLOYEE NUMBER"),"").trim();
    }

    private Map<Integer,String> r4Omissions(UUID id,Actor owner) {
        return transaction(()->{
            rawBatch(id.toString(),owner,false);
            Map<Integer,String> result=new HashMap<>();
            for(Object value:q("""
                SELECT row_no,source_identifier FROM bulk_import_omission
                WHERE batch_id=:id
                ""","id",id.toString()).getResultList()) {
                Object[] row=(Object[])value;
                result.put(((Number)row[0]).intValue(),row[1].toString());
            }
            return result;
        });
    }

    public SimpleResult simpleSkipActive(UUID uuid,Input input,Authentication auth) {
        SimpleResult checked=simpleValidate(uuid,input,auth);
        if(checked.registered()) return checked;
        Actor owner=actor(auth);
        Batch batch=checked.batch();
        String id=uuid.toString();

        Map<Integer,String> canonical=new HashMap<>();
        for(Item row:batch.document().rows()) {
            String source=r4Identifier(row.values());
            if(!source.matches("[0-9]+")) continue;
            try {
                int number=new BigInteger(source).intValueExact();
                if(number<=0) continue;
                var employee=r4Directory.findByEmployeeNum(number);
                String key=employee.isPresent()?
                    String.valueOf(employee.get().getEmployeeNum()):String.valueOf(number);
                canonical.put(row.excelRow(),key);
            } catch(ArithmeticException ignored) {}
        }

        int count=transaction(()->{
            Object[] raw=rawBatch(id,owner,true);
            if(!"DRAFT".equals(raw[0])||
                ((Number)raw[1]).longValue()!=batch.version())
                throw status(409,"El lote cambio. Actualiza su estado.");

            Set<String> active=canonical.isEmpty()?Set.of():
                new HashSet<>(activeKeys(new ArrayList<>(canonical.values())));
            int omitted=0;

            for(Item row:batch.document().rows()) {
                String key=canonical.get(row.excelRow());
                if(key==null||!active.contains(key)) continue;

                q("""
                    INSERT INTO bulk_import_omission
                        (batch_id,row_no,source_identifier,employee_identifier)
                    VALUES(:id,:row,:source,:employee)
                    ON CONFLICT(batch_id,row_no) DO UPDATE SET
                        source_identifier=EXCLUDED.source_identifier,
                        employee_identifier=EXCLUDED.employee_identifier
                    ""","id",id,"row",row.excelRow(),
                    "source",r4Identifier(row.values()),"employee",key).executeUpdate();
                omitted++;
            }

            var tree=(com.fasterxml.jackson.databind.node.ObjectNode)
                json.valueToTree(batch.document());
            for(var row:tree.withArray("rows")) {
                int number=row.path("excelRow").asInt();
                if(active.contains(canonical.get(number))) {
                    var object=(com.fasterxml.jackson.databind.node.ObjectNode)row;
                    object.put("included",false);
                    object.putArray("issues");
                }
            }

            q("""
                UPDATE bulk_import_batch SET document=:doc,version=version+1,
                    last_hash=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                ""","id",id,"doc",tree.toString()).executeUpdate();
            return omitted;
        });

        SimpleResult result=simpleGet(uuid,auth);
        return new SimpleResult(result.batch(),false,result.mailState(),
            count==0?"No se encontraron empleados adicionales con baja activa.":
                count+" empleados con baja activa omitidos.",Set.of());
    }
}