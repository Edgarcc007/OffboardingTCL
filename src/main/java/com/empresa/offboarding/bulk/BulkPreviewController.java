package com.empresa.offboarding.bulk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/api/bulk")
public class BulkPreviewController {

    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final Semaphore slots = new Semaphore(2);
    private final ObjectMapper json = new ObjectMapper();
    private final BulkPreviewService service;

    public BulkPreviewController(BulkPreviewService service) {
        this.service = service;
    }

    public record ReviewInput(List<ReviewRow> rows) {}
    public record ReviewRow(int excelRow, Map<String, String> values) {}

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(
        Authentication authentication, CsrfToken csrf
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of(
                "owner", service.owner(authentication),
                "timeZone", service.zoneId(),
                "csrfHeader", csrf.getHeaderName(),
                "csrfToken", csrf.getToken(),
                "registrationEnabled", false
            ));
    }

    @PostMapping(
        value = "/preview",
        consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ResponseEntity<BulkPreviewService.Preview> preview(
        HttpServletRequest request,
        Authentication authentication,
        @RequestHeader(value = "X-File-Name", defaultValue = "bajas.xlsx")
        String fileName
    ) throws IOException {
        service.owner(authentication);

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Selecciona un archivo .xlsx.");
        }

        reserve();
        try {
            return response(service.prepare(readBytes(request), authentication));
        } finally {
            slots.release();
        }
    }

    @PostMapping(value = "/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BulkPreviewService.Preview> review(
        HttpServletRequest request, Authentication authentication
    ) throws IOException {
        service.owner(authentication);
        reserve();

        try {
            ReviewInput input;
            try {
                input = json.readValue(readBytes(request), ReviewInput.class);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException(
                    "La solicitud de revision tiene un formato incorrecto."
                );
            }

            if (input == null || input.rows() == null ||
                input.rows().isEmpty() || input.rows().size() > 1000) {
                throw new IllegalArgumentException(
                    "Selecciona entre 1 y 1000 filas para revisar."
                );
            }

            /*
             * Reutiliza exactamente el lector ya probado.
             * El libro temporal existe solo en memoria.
             * No se aceptan nombres/departamentos enviados por el navegador:
             * se consultan nuevamente en el directorio.
             */
            byte[] workbook;
            try (XSSFWorkbook book = new XSSFWorkbook();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Sheet sheet = book.createSheet("BAJAS");
                header(sheet, null);
                Set<Integer> positions = new HashSet<>();

                for (ReviewRow row : input.rows()) {
                    if (row == null || row.values() == null ||
                        row.excelRow() < 2 || row.excelRow() > 1001 ||
                        !positions.add(row.excelRow())) {
                        throw new IllegalArgumentException(
                            "La revision contiene filas invalidas o repetidas."
                        );
                    }

                    if (!BulkExcelReader.HEADERS.containsAll(
                        row.values().keySet()
                    )) {
                        throw new IllegalArgumentException(
                            "La revision contiene campos no admitidos."
                        );
                    }

                    Row target = sheet.createRow(row.excelRow() - 1);
                    for (int c = 0; c < BulkExcelReader.HEADERS.size(); c++) {
                        String value = row.values().get(
                            BulkExcelReader.HEADERS.get(c)
                        );

                        if (value != null && value.length() > 4001) {
                            throw new IllegalArgumentException(
                                "Una celda supera la longitud admitida."
                            );
                        }

                        target.createCell(c).setCellValue(
                            value == null ? "" : value
                        );
                    }
                }

                book.write(output);
                workbook = output.toByteArray();
            }

            return response(service.prepare(workbook, authentication));
        } finally {
            slots.release();
        }
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(Authentication authentication)
        throws IOException {

        service.owner(authentication);

        try (XSSFWorkbook book = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            CellStyle text = book.createCellStyle();
            text.setDataFormat(book.createDataFormat().getFormat("@"));

            Font white = book.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());

            CellStyle heading = book.createCellStyle();
            heading.setFont(white);
            heading.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            heading.setWrapText(true);

            Sheet capture = book.createSheet("BAJAS");
            header(capture, heading);
            capture.createFreezePane(1, 1);

            for (int c = 0; c < BulkExcelReader.HEADERS.size(); c++) {
                capture.setColumnWidth(c, (c == 13 ? 50 : 25) * 256);
                capture.setDefaultColumnStyle(c, text);
            }

            DataValidationHelper helper = capture.getDataValidationHelper();

            for (int column : new int[]{4, 5, 6, 8, 10}) {
                validation(capture, helper, column, new String[]{"SI", "NO"});
            }

            validation(capture, helper, 1, new String[]{
                "RENUNCIA", "DESPIDO", "FIN_DE_CONTRATO",
                "JUBILACION", "MUTUO_ACUERDO", "FALLECIMIENTO"
            });

            Sheet instructions = book.createSheet("INSTRUCCIONES");
            instructions.setColumnWidth(0, 30 * 256);
            instructions.setColumnWidth(1, 115 * 256);

            String[][] guide = {
                {"CAMPO", "REGLA"},
                {"HOJA A UTILIZAR", "Completa solamente BAJAS. No cambies encabezados ni combines celdas."},
                {"EMPLOYEE NUMBER", "Obligatorio. Numero de empleado; conservar ceros iniciales."},
                {"DATOS LABORALES", "Nombre, departamento, area, jefe, puesto y turno se consultan en la base de datos."},
                {"OFFBOARDING TYPE", "Opcional en Excel. Si esta vacio se elegira en pantalla antes de confirmar."},
                {"EFFECTIVE DATE", "Vacio: fecha/hora actual al preparar el lote. Para otra fecha: yyyy-MM-dd HH:mm, en la zona de la aplicacion, o ISO con desplazamiento."},
                {"CORPORATE EMAIL", "Vacio salvo que se indique un correo. No corresponde a los destinatarios de notificaciones."},
                {"FACE ID / FINGERPRINT", "SI o NO. Vacio significa SI."},
                {"COMPUTER ASSIGNED / PHONE ASSIGNED", "SI o NO. Vacio significa NO."},
                {"COMPUTER DETAILS / PHONE DETAILS", "Maximo 250 caracteres. Requieren que el equipo correspondiente este marcado SI."},
                {"CONFIDENTIAL", "Opcional. SI o NO. Vacio significa NO."},
                {"ACCESSES", "WINDOWS;OFFICE;SMES;CMP;VPN;OTHER. Utiliza punto y coma para separar."},
                {"OTHER ACCESSES", "Maximo 1000 caracteres. Requiere OTHER en ACCESSES."},
                {"OBSERVATIONS", "Comentarios opcionales. Maximo 4000 caracteres."},
                {"EJEMPLOS", "Contiene datos ficticios. Esa hoja no se importa."},
                {"REVISION", "Cargar o revisar el archivo no registra bajas ni envia correos."}
            };

            CellStyle wrapped = book.createCellStyle();
            wrapped.setWrapText(true);

            for (int r = 0; r < guide.length; r++) {
                Row row = instructions.createRow(r);
                row.setHeightInPoints(r == 0 ? 28 : 44);
                for (int c = 0; c < 2; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(guide[r][c]);
                    cell.setCellStyle(r == 0 ? heading : wrapped);
                }
            }

            Sheet examples = book.createSheet("EJEMPLOS");
            header(examples, heading);
            for (int c = 0; c < BulkExcelReader.HEADERS.size(); c++) {
                examples.setColumnWidth(c, 26 * 256);
            }
            Row sample = examples.createRow(1);
            sample.createCell(0).setCellValue("000001");
            sample.createCell(1).setCellValue("RENUNCIA");
            sample.createCell(13).setCellValue(
                "EJEMPLO FICTICIO. No copiar como una baja real."
            );

            book.write(output);

            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"Bajas-TCL.xlsx\"")
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(output.toByteArray());
        }
    }

    private static void header(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(34);
        for (int c = 0; c < BulkExcelReader.HEADERS.size(); c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(BulkExcelReader.HEADERS.get(c));
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static void validation(
        Sheet sheet, DataValidationHelper helper, int column, String[] choices
    ) {
        DataValidation validation = helper.createValidation(
            helper.createExplicitListConstraint(choices),
            new CellRangeAddressList(1, 1000, column, column)
        );
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        sheet.addValidationData(validation);
    }

    private void reserve() {
        if (!slots.tryAcquire()) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Hay otras cargas en proceso. Intenta nuevamente."
            );
        }
    }

    private byte[] readBytes(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            throw new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE, "La solicitud supera 8 MB."
            );
        }
        byte[] bytes;
        try (var input = request.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE, "La solicitud supera 8 MB."
            );
        }
        return bytes;
    }

    private ResponseEntity<BulkPreviewService.Preview> response(
        BulkPreviewService.Preview preview
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preview);
    }
}