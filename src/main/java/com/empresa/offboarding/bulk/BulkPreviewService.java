package com.empresa.offboarding.bulk;

import com.empresa.offboarding.dto.CreateOffboardingRequest;
import com.empresa.offboarding.dto.PremployeeDTO;
import com.empresa.offboarding.enums.AccessType;
import com.empresa.offboarding.enums.AppRole;
import com.empresa.offboarding.enums.RiskLevel;
import com.empresa.offboarding.repository.AppUserRepository;
import com.empresa.offboarding.service.PremployeeService;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class BulkPreviewService {

    private final PremployeeService employees;
    private final AppUserRepository users;
    private final Validator validator;
    private final BulkExcelReader reader;
    private final ZoneId zone;

    public BulkPreviewService(
        PremployeeService employees,
        AppUserRepository users,
        Validator validator,
        @Value("${offboarding.bulk.zone:}") String configuredZone
    ) {
        this.employees = employees;
        this.users = users;
        this.validator = validator;
        this.reader = new BulkExcelReader(validator);
        this.zone = configuredZone == null || configuredZone.isBlank()
            ? ZoneId.systemDefault()
            : ZoneId.of(configuredZone.trim());
    }

    public record Owner(Long id, String username) {}

    public record EmployeeData(
        String code,
        int number,
        String name,
        String department,
        String area,
        String manager,
        String position,
        String shift
    ) {}

    public record PreviewRow(
        int excelRow,
        Map<String, String> excelValues,
        EmployeeData employee,
        CreateOffboardingRequest form,
        List<String> issues,
        List<String> warnings
    ) {
        public PreviewRow {
            excelValues = Collections.unmodifiableMap(
                new LinkedHashMap<>(excelValues)
            );
            issues = List.copyOf(issues);
            warnings = List.copyOf(warnings);
        }
    }

    public record Preview(
        UUID previewId,
        Owner owner,
        Instant preparedAt,
        String timeZone,
        List<PreviewRow> rows,
        boolean registrationEnabled
    ) {
        public Preview {
            rows = List.copyOf(rows);
        }
    }

    public String zoneId() {
        return zone.getId();
    }

    public Owner owner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        boolean sessionAllowed = authentication.getAuthorities().stream()
            .anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) ||
                "ROLE_RECURSOS_HUMANOS".equals(a.getAuthority())
            );

        if (!sessionAllowed) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "No tienes permiso para importar bajas."
            );
        }

        var user = users.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Vuelve a iniciar sesion."
            ));

        if (!user.isEnabled() ||
            user.getRoles() == null ||
            !(user.getRoles().contains(AppRole.ADMIN) ||
              user.getRoles().contains(AppRole.RECURSOS_HUMANOS))) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "La cuenta no esta habilitada para importar bajas."
            );
        }

        return new Owner(user.getId(), user.getUsername());
    }

    public Preview prepare(byte[] bytes, Authentication authentication) {
        Owner owner = owner(authentication);
        Instant capturedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        List<BulkExcelReader.ParsedRow> parsed =
            reader.read(bytes, zone, capturedAt);

        // Evitar respuestas excesivamente grandes con textos repetidos.
        long characters = parsed.stream()
            .flatMap(row -> row.values().values().stream())
            .mapToLong(String::length).sum();

        if (characters > 2_000_000) {
            throw new IllegalArgumentException(
                "Demasiado contenido para una vista previa. Divide el archivo."
            );
        }

        List<PreviewRow> rows = new ArrayList<>();
        Map<Integer, Optional<PremployeeDTO>> cache = new HashMap<>();

        try {
            for (var parsedRow : parsed) {
                Map<String, String> values = parsedRow.values();
                List<String> issues = new ArrayList<>(parsedRow.errors());
                List<String> warnings = new ArrayList<>();

                EmployeeData employee = null;
                CreateOffboardingRequest form = null;

                Integer number = employeeNumber(
                    values.get("EMPLOYEE NUMBER")
                );

                if (number != null) {
                    Optional<PremployeeDTO> found = cache.computeIfAbsent(
                        number, employees::findByEmployeeNum
                    );

                    if (found.isEmpty()) {
                        issues.add(
                            "employeeIdentifier: empleado no encontrado."
                        );
                    } else {
                        PremployeeDTO dto = found.get();

                        employee = new EmployeeData(
                            clean(dto.getEmployeeCode()),
                            dto.getEmployeeNum(),
                            clean(dto.getEmployeeName()),
                            clean(dto.getDeptName()),
                            clean(dto.getAreaName()),
                            clean(dto.getSuperName()),
                            clean(dto.getRollName()),
                            clean(dto.getTurnName())
                        );

                        if (dto.getEmployeeNum() != number) {
                            warnings.add(
                                "El directorio devolvio otro numero interno. " +
                                "Verifica el codigo y la identidad antes de confirmar."
                            );
                        }

                        // No convertir celdas invalidas en un formulario valido.
                        if (parsedRow.errors().isEmpty()) {
                            form = form(values, employee);

                            validator.validate(form).stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .sorted()
                                .forEach(issues::add);

                            if (form.terminationType() == null) {
                                issues.add(
                                    "terminationType: selecciona el tipo de baja " +
                                    "para esta persona o para el lote."
                                );
                            }
                        }
                    }
                }

                rows.add(new PreviewRow(
                    parsedRow.excelRow(), values, employee,
                    form, issues, warnings
                ));
            }
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar el directorio de empleados. " +
                "No se registraron bajas.",
                ex
            );
        }

        // Comprobar otra vez la cuenta antes de devolver los datos.
        Owner current = owner(authentication);
        if (!Objects.equals(owner.id(), current.id())) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "La sesion cambio. Inicia sesion nuevamente."
            );
        }

        return new Preview(
            UUID.randomUUID(), owner, capturedAt,
            zone.getId(), rows, false
        );
    }

    private CreateOffboardingRequest form(
        Map<String, String> values,
        EmployeeData employee
    ) {
        Set<AccessType> accesses = EnumSet.noneOf(AccessType.class);

        for (String value : values.get("ACCESSES").split(";")) {
            if (!value.isBlank()) {
                accesses.add(AccessType.valueOf(value));
            }
        }

        return new CreateOffboardingRequest(
            employee.name(),
            values.get("EMPLOYEE NUMBER"),
            clean(values.get("CORPORATE EMAIL")),
            employee.department(),
            null,
            employee.area(),
            employee.manager(),
            clean(values.get("OFFBOARDING TYPE")),
            OffsetDateTime.parse(values.get("EFFECTIVE DATE")),
            RiskLevel.NORMAL,
            yes(values, "CONFIDENTIAL"),
            yes(values, "COMPUTER ASSIGNED"),
            clean(values.get("COMPUTER DETAILS")),
            yes(values, "PHONE ASSIGNED"),
            clean(values.get("PHONE DETAILS")),
            yes(values, "FINGERPRINT"),
            yes(values, "FACE ID"),
            false,
            false,
            null,
            Set.copyOf(accesses),
            clean(values.get("OTHER ACCESSES")),
            clean(values.get("OBSERVATIONS"))
        );
    }

    private static Integer employeeNumber(String value) {
        if (value == null || !value.matches("[0-9]{1,60}")) return null;

        try {
            int number = new BigInteger(value).intValueExact();
            return number > 0 ? number : null;
        } catch (ArithmeticException ex) {
            return null;
        }
    }

    private static boolean yes(Map<String, String> values, String key) {
        return "SI".equals(values.get(key));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}