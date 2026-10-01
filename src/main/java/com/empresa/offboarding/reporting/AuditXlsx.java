package com.empresa.offboarding.reporting;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntFunction;

public final class AuditXlsx {
    private AuditXlsx(){}

    public static byte[] generate(
        AuditExplorer.Filter filter,String zone,long total,String snapshot,
        IntFunction<List<AuditExplorer.EventRow>> fetch
    ) throws Exception {
        SXSSFWorkbook workbook=new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);

        try {
            Sheet sheet=workbook.createSheet("Audit log");

            Font bold=workbook.createFont();bold.setBold(true);
            Font white=workbook.createFont();white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());

            CellStyle title=workbook.createCellStyle();title.setFont(bold);
            CellStyle header=workbook.createCellStyle();header.setFont(white);
            header.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle body=workbook.createCellStyle();
            body.setVerticalAlignment(VerticalAlignment.TOP);
            body.setWrapText(true);
            body.setDataFormat(workbook.createDataFormat().getFormat("@"));

            text(sheet.createRow(0),0,"TCL | Offboarding audit log",title);
            sheet.addMergedRegion(new CellRangeAddress(0,0,0,13));
            text(sheet.createRow(1),0,
                "Dates: "+Objects.toString(filter.from(),"All")+" to "+
                Objects.toString(filter.to(),"All")+" | Zone: "+zone,null);
            text(sheet.createRow(2),0,
                "Search: "+filter.field()+" | "+filter.q()+
                " | Matching events: "+total+" | Snapshot: "+snapshot,null);
            text(sheet.createRow(3),0,
                "Exported: "+ZonedDateTime.now(ZoneId.of(zone))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),null);

            String[] headings={
                "Event ID","Date and time","Activity","Actor","Case",
                "Employee number","Employee name","Asset ID",
                "System","Task","Original event code","Entity","Entity ID","Original details"
            };

            Row heading=sheet.createRow(4);
            for(int i=0;i<headings.length;i++){
                text(heading,i,headings[i],header);
                sheet.setColumnWidth(i,(i==13?70:i==6||i==9?40:24)*256);
            }

            int offset=0,index=5;
            while(offset<total) {
                List<AuditExplorer.EventRow> page=fetch.apply(offset);
                if(page.isEmpty())
                    throw new IllegalStateException("The report changed during export. Refresh and retry.");

                for(var event:page) {
                    String[] values={
                        event.id(),event.timestamp(),event.action(),event.actor(),
                        event.caseNumber(),event.employeeNumber(),event.employeeName(),
                        event.assets(),event.system(),event.task(),event.code(),
                        event.entityType(),event.entityId(),event.details()
                    };
                    Row row=sheet.createRow(index++);
                    for(int i=0;i<values.length;i++)text(row,i,values[i],body);
                }
                offset+=page.size();
            }

            sheet.createFreezePane(0,5);
            sheet.setAutoFilter(new CellRangeAddress(4,Math.max(4,index-1),0,13));

            try(ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                workbook.write(output);
                return output.toByteArray();
            }
        } finally {
            try { workbook.close(); }
            finally { workbook.dispose(); }
        }
    }

    private static void text(Row row,int column,String value,CellStyle style) {
        Cell cell=row.createCell(column,CellType.STRING);
        cell.setCellValue(Objects.toString(value,""));
        if(style!=null)cell.setCellStyle(style);
    }
}