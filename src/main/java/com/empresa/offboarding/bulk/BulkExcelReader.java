package com.empresa.offboarding.bulk;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigInteger;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * Lectura y validacion del archivo.
 * No consulta empleados, no guarda bajas y no envia notificaciones.
 */
public final class BulkExcelReader {

    public static final List<String> HEADERS = List.of(
        "EMPLOYEE NUMBER", "OFFBOARDING TYPE", "EFFECTIVE DATE",
        "CORPORATE EMAIL", "FACE ID", "FINGERPRINT",
        "COMPUTER ASSIGNED", "COMPUTER DETAILS",
        "PHONE ASSIGNED", "PHONE DETAILS", "CONFIDENTIAL",
        "ACCESSES", "OTHER ACCESSES", "OBSERVATIONS"
    );

    private static final Set<String> TYPES = Set.of(
        "RENUNCIA", "DESPIDO", "FIN_DE_CONTRATO",
        "JUBILACION", "MUTUO_ACUERDO", "FALLECIMIENTO"
    );

    private static final Set<String> ACCESSES = Set.of(
        "WINDOWS", "OFFICE", "SMES", "CMP", "VPN", "OTHER"
    );

    private static final DateTimeFormatter LOCAL =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm[:ss]", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final Validator validator;

    public BulkExcelReader(Validator validator) {
        this.validator = Objects.requireNonNull(validator);
    }

    public record ParsedRow(
        int excelRow,
        Map<String, String> values,
        List<String> errors
    ) {
        public ParsedRow {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            errors = List.copyOf(errors);
        }
    }

    public record EmailValue(
        @Email @Size(max = 150) String value
    ) {}

    private record Draft(
        int number,
        Map<String, String> values,
        List<String> errors,
        String canonicalId
    ) {}

    public List<ParsedRow> read(
        byte[] content,
        ZoneId zone,
        Instant capturedAt
    ) {
        Objects.requireNonNull(zone);
        Objects.requireNonNull(capturedAt);

        if (content == null || content.length == 0 ||
            content.length > 8 * 1024 * 1024) {
            throw new IllegalArgumentException(
                "El archivo debe contener datos y no superar 8 MB."
            );
        }

        String defaultDate = capturedAt.truncatedTo(ChronoUnit.SECONDS)
            .atZone(zone).toOffsetDateTime().toString();

        try {
            inspectZip(content);

            try (XSSFWorkbook book =
                     new XSSFWorkbook(new ByteArrayInputStream(content))) {

                if (!"XLSX".equals(book.getWorkbookType().name())) {
                    throw new IllegalArgumentException(
                        "Utiliza un libro XLSX sin macros."
                    );
                }

                Sheet sheet = book.getSheet("BAJAS");
                if (sheet == null || sheet.getRow(0) == null) {
                    throw new IllegalArgumentException(
                        "Falta la hoja BAJAS o sus encabezados en la fila 1."
                    );
                }

                if (sheet.getLastRowNum() > 1000 ||
                    sheet.getNumMergedRegions() > 0) {
                    throw new IllegalArgumentException(
                        "BAJAS admite hasta la fila 1001 y no admite celdas combinadas."
                    );
                }

                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                Map<Integer, String> columns = new LinkedHashMap<>();
                Set<String> names = new HashSet<>();

                org.apache.poi.ss.usermodel.Row header = sheet.getRow(0);
                if (header.getLastCellNum() > 64) {
                    throw new IllegalArgumentException("Demasiadas columnas.");
                }

                for (Cell cell : header) {
                    String name = text(cell, "", formatter)
                        .replace('\u00A0', ' ').strip()
                        .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

                    if (name.isEmpty()) continue;

                    if (!HEADERS.contains(name)) {
                        throw new IllegalArgumentException(
                            "Encabezado no reconocido: " + name +
                            ". Utiliza la nueva plantilla BAJAS."
                        );
                    }

                    if (!names.add(name)) {
                        throw new IllegalArgumentException(
                            "Encabezado repetido: " + name
                        );
                    }

                    columns.put(cell.getColumnIndex(), name);
                }

                if (!names.contains("EMPLOYEE NUMBER")) {
                    throw new IllegalArgumentException(
                        "Falta la columna EMPLOYEE NUMBER."
                    );
                }

                List<Draft> drafts = new ArrayList<>();

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                    if (row == null || !hasData(row)) continue;

                    if (row.getLastCellNum() > 64) {
                        throw new IllegalArgumentException(
                            "Demasiadas columnas en la fila " + (r + 1)
                        );
                    }

                    Map<String, String> values = new LinkedHashMap<>();
                    HEADERS.forEach(key -> values.put(key, ""));
                    List<String> errors = new ArrayList<>();

                    for (Cell cell : row) {
                        String key = columns.get(cell.getColumnIndex());

                        try {
                            String value = text(
                                cell, key == null ? "" : key, formatter
                            ).strip();

                            if (key == null) {
                                if (!value.isEmpty()) {
                                    errors.add(
                                        "Hay datos en una columna sin encabezado."
                                    );
                                }
                                continue;
                            }

                            if (value.length() > 4000) {
                                errors.add(
                                    key + ": texto demasiado largo; no se importara."
                                );
                                value = value.substring(0, 4001);
                            }

                            values.put(key, value);
                        } catch (IllegalArgumentException ex) {
                            errors.add(
                                (key == null ? "Columna sin encabezado" : key) +
                                ": " + ex.getMessage()
                            );
                        }
                    }

                    String canonical = null;
                    String id = values.get("EMPLOYEE NUMBER");

                    if (!id.matches("[0-9]{1,60}")) {
                        errors.add(
                            "EMPLOYEE NUMBER debe contener solamente digitos."
                        );
                    } else {
                        BigInteger numeric = new BigInteger(id);
                        if (numeric.signum() <= 0 ||
                            numeric.compareTo(
                                BigInteger.valueOf(Integer.MAX_VALUE)
                            ) > 0) {
                            errors.add(
                                "EMPLOYEE NUMBER esta fuera del rango admitido."
                            );
                        } else {
                            canonical = numeric.toString();
                        }
                    }

                    normalize(values, errors, zone, defaultDate);
                    drafts.add(new Draft(r + 1, values, errors, canonical));
                }

                if (drafts.isEmpty()) {
                    throw new IllegalArgumentException(
                        "La hoja BAJAS no contiene filas de empleados."
                    );
                }

                Map<String, List<Draft>> grouped = new HashMap<>();
                for (Draft row : drafts) {
                    if (row.canonicalId() != null) {
                        grouped.computeIfAbsent(
                            row.canonicalId(), ignored -> new ArrayList<>()
                        ).add(row);
                    }
                }

                for (List<Draft> repeated : grouped.values()) {
                    if (repeated.size() > 1) {
                        repeated.forEach(row -> row.errors().add(
                            "Numero de empleado repetido dentro del archivo."
                        ));
                    }
                }

                return drafts.stream()
                    .map(row -> new ParsedRow(
                        row.number(), row.values(), row.errors()
                    )).toList();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "No se pudo leer un archivo XLSX valido.", ex
            );
        }
    }

    private void normalize(
        Map<String, String> values,
        List<String> errors,
        ZoneId zone,
        String defaultDate
    ) {
        flag(values, errors, "FACE ID", true);
        flag(values, errors, "FINGERPRINT", true);
        flag(values, errors, "COMPUTER ASSIGNED", false);
        flag(values, errors, "PHONE ASSIGNED", false);
        flag(values, errors, "CONFIDENTIAL", false);

        String date = values.get("EFFECTIVE DATE");
        if (date.isEmpty()) {
            values.put("EFFECTIVE DATE", defaultDate);
        } else {
            try {
                OffsetDateTime effective;
                try {
                    effective = OffsetDateTime.parse(date);
                } catch (DateTimeParseException ignored) {
                    LocalDateTime local = LocalDateTime.parse(date, LOCAL);
                    List<ZoneOffset> offsets =
                        zone.getRules().getValidOffsets(local);

                    if (offsets.size() != 1) {
                        throw new IllegalArgumentException(
                            "Hora ambigua o inexistente en " + zone
                        );
                    }

                    effective = OffsetDateTime.of(local, offsets.get(0));
                }
                values.put("EFFECTIVE DATE", effective.toString());
            } catch (RuntimeException ex) {
                errors.add(
                    "EFFECTIVE DATE invalida. Usa yyyy-MM-dd HH:mm " +
                    "o una fecha ISO con desplazamiento horario."
                );
            }
        }

        String type = values.get("OFFBOARDING TYPE").toUpperCase(Locale.ROOT);
        values.put("OFFBOARDING TYPE", type);
        if (!type.isEmpty() && !TYPES.contains(type)) {
            errors.add("OFFBOARDING TYPE no reconocido.");
        }

        if (!validator.validate(
            new EmailValue(values.get("CORPORATE EMAIL"))
        ).isEmpty()) {
            errors.add("CORPORATE EMAIL tiene un formato o longitud invalidos.");
        }

        Set<String> accesses = new LinkedHashSet<>();
        for (String token : values.get("ACCESSES").split(";")) {
            String code = token.strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) continue;
            accesses.add(code);
            if (!ACCESSES.contains(code)) {
                errors.add("ACCESSES contiene un valor no reconocido: " + code);
            }
        }
        values.put("ACCESSES", String.join(";", accesses));

        checkDetail(values, errors, "COMPUTER ASSIGNED", "COMPUTER DETAILS");
        checkDetail(values, errors, "PHONE ASSIGNED", "PHONE DETAILS");

        if (!values.get("OTHER ACCESSES").isEmpty() &&
            !accesses.contains("OTHER")) {
            errors.add("OTHER ACCESSES requiere incluir OTHER en ACCESSES.");
        }

        for (String key : List.of("COMPUTER DETAILS", "PHONE DETAILS")) {
            if (values.get(key).length() > 250) {
                errors.add(key + " no puede superar 250 caracteres.");
            }
        }

        if (values.get("OTHER ACCESSES").length() > 1000) {
            errors.add("OTHER ACCESSES no puede superar 1000 caracteres.");
        }
    }

    private static void flag(
        Map<String, String> values,
        List<String> errors,
        String key,
        boolean defaultValue
    ) {
        String raw = values.get(key).toUpperCase(Locale.ROOT);

        if (raw.isEmpty()) {
            values.put(key, defaultValue ? "SI" : "NO");
        } else if (Set.of("SI", "SÍ", "YES", "TRUE", "1").contains(raw)) {
            values.put(key, "SI");
        } else if (Set.of("NO", "FALSE", "0").contains(raw)) {
            values.put(key, "NO");
        } else {
            errors.add(key + " debe ser SI, NO o vacio.");
        }
    }

    private static void checkDetail(
        Map<String, String> values,
        List<String> errors,
        String flag,
        String detail
    ) {
        if (!values.get(detail).isEmpty() &&
            !"SI".equals(values.get(flag))) {
            errors.add(detail + " requiere " + flag + "=SI.");
        }
    }

    private static boolean hasData(org.apache.poi.ss.usermodel.Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.BLANK) continue;
            if (cell.getCellType() == CellType.STRING &&
                cell.getStringCellValue().isBlank()) continue;
            return true;
        }
        return false;
    }

    private static String text(Cell cell, String key, DataFormatter formatter) {
        if (cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException(
                "No se admiten formulas; utiliza valores."
            );
        }
        if (cell.getCellType() == CellType.ERROR) {
            throw new IllegalArgumentException("La celda contiene un error.");
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            double number = cell.getNumericCellValue();

            if ("EMPLOYEE NUMBER".equals(key) &&
                (!Double.isFinite(number) || number != Math.rint(number) ||
                 number <= 0 || number > Integer.MAX_VALUE ||
                 DateUtil.isCellDateFormatted(cell))) {
                throw new IllegalArgumentException(
                    "El numero debe ser un entero positivo, no una fecha."
                );
            }

            if ("EFFECTIVE DATE".equals(key)) {
                if (!DateUtil.isCellDateFormatted(cell) || number < 1) {
                    throw new IllegalArgumentException(
                        "Utiliza una fecha de Excel o texto yyyy-MM-dd HH:mm."
                    );
                }
                return cell.getLocalDateTimeCellValue().format(LOCAL);
            }
        }

        return formatter.formatCellValue(cell);
    }

    private static void inspectZip(byte[] content) throws IOException {
        Set<String> names = new HashSet<>();
        long total = 0;
        boolean workbook = false;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip =
                 new ZipInputStream(new ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);

                if (!names.add(name) || names.size() > 2000) {
                    throw new IllegalArgumentException(
                        "El archivo tiene entradas duplicadas o demasiadas partes."
                    );
                }
                if (name.endsWith("vbaproject.bin")) {
                    throw new IllegalArgumentException(
                        "No se admiten libros con macros."
                    );
                }
                if (name.equals("xl/workbook.xml")) workbook = true;

                long part = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    part += read;
                    total += read;
                    if (part > 20L * 1024 * 1024 ||
                        total > 60L * 1024 * 1024) {
                        throw new IllegalArgumentException(
                            "El contenido descomprimido supera los limites."
                        );
                    }
                }
            }
        }

        if (!workbook) {
            throw new IllegalArgumentException(
                "El contenido no corresponde a un libro XLSX."
            );
        }
    }
}