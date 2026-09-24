package com.empresa.offboarding.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    @Value("${inventory.excel-path}")
    private String excelPath;

    @Value("${inventory.cache-minutes:10}")
    private int cacheMinutes;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private List<Map<String, String>> rows = new ArrayList<>();
    private List<String> headers = new ArrayList<>();
    private Instant lastLoad = Instant.EPOCH;

    @PostConstruct
    public void init() {
        try {
            loadExcel();
        } catch (Exception e) {
            log.warn("Could not load inventory on startup: {}", e.getMessage());
        }
    }

    private void loadExcel() {
        Path path = Paths.get(excelPath);
        if (!Files.exists(path)) {
            log.warn("Inventory file not found: {}", excelPath);
            return;
        }

        log.info("Loading inventory from: {}", excelPath);
        List<Map<String, String>> newRows = new ArrayList<>();
        List<String> newHeaders = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(path.toFile());
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return;

            DataFormatter fmt = new DataFormatter();

            // Read headers from row 0
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return;

            int lastCol = headerRow.getLastCellNum();
            for (int c = 0; c < lastCol; c++) {
                Cell cell = headerRow.getCell(c);
                String val = (cell != null) ? fmt.formatCellValue(cell).trim() : "";
                newHeaders.add(val.isEmpty() ? ("COL_" + c) : val);
            }

            // Read data rows
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> map = new LinkedHashMap<>();
                boolean hasData = false;

                for (int c = 0; c < newHeaders.size(); c++) {
                    Cell cell = row.getCell(c);
                    String val = (cell != null) ? fmt.formatCellValue(cell).trim() : "";
                    map.put(newHeaders.get(c), val);
                    if (!val.isEmpty()) hasData = true;
                }

                if (hasData) newRows.add(map);
            }

            lock.writeLock().lock();
            try {
                this.headers = newHeaders;
                this.rows = newRows;
                this.lastLoad = Instant.now();
            } finally {
                lock.writeLock().unlock();
            }

            log.info("Inventory loaded: {} rows, {} columns", newRows.size(), newHeaders.size());

        } catch (Exception e) {
            log.error("Error loading inventory Excel: {}", e.getMessage(), e);
        }
    }

    private void refreshIfNeeded() {
        if (Instant.now().isAfter(lastLoad.plusSeconds(cacheMinutes * 60L))) {
            try {
                loadExcel();
            } catch (Exception e) {
                log.warn("Error refreshing inventory: {}", e.getMessage());
            }
        }
    }

    /**
     * Search all columns for the query string (case-insensitive).
     * Returns up to maxResults matching rows.
     */
    public List<Map<String, String>> search(String query, int maxResults) {
        refreshIfNeeded();

        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return Collections.emptyList();

        lock.readLock().lock();
        try {
            return rows.stream()
                .filter(row -> row.values().stream()
                    .anyMatch(val -> val.toLowerCase().contains(q)))
                .limit(maxResults)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> getHeaders() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(headers);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getRowCount() {
        lock.readLock().lock();
        try {
            return rows.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
