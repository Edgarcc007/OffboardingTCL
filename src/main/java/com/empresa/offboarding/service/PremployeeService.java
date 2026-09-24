package com.empresa.offboarding.service;

import com.empresa.offboarding.dto.PremployeeDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PremployeeService {

    private final JdbcTemplate jdbcTemplate;

    public PremployeeService(@Qualifier("sqlServerJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String BASE_QUERY =
        "SELECT e.employee_code, e.employee_num, e.full_name, e.employee_name, " +
        "       e.dept_code,  d.dept_desc, " +
        "       e.area_code,  a.area_desc, " +
        "       e.super_code, s.super_name, " +
        "       e.turn_code,  t.turn_desc, " +
        "       e.roll_code,  r.roll_desc " +
        "FROM dbo.premployee e " +
        "LEFT JOIN dbo.prdept  d ON d.comp_id = 1 AND d.dept_code = e.dept_code " +
        "LEFT JOIN dbo.prarea  a ON a.comp_id = 1 AND a.area_code = e.area_code " +
        "LEFT JOIN dbo.easuper s ON s.super_code = e.super_code " +
        "LEFT JOIN dbo.prturnhd t ON t.comp_id = 1 AND t.turn_code = e.turn_code " +
        "LEFT JOIN dbo.prroll   r ON r.comp_id = 1 AND r.roll_code = e.roll_code";

    private final RowMapper<PremployeeDTO> rowMapper = (rs, rowNum) -> {
        PremployeeDTO dto = new PremployeeDTO();
        dto.setEmployeeCode(rs.getString("employee_code"));
        dto.setEmployeeNum(rs.getInt("employee_num"));

        String fullName = rs.getString("full_name");
        String empName  = rs.getString("employee_name");
        dto.setEmployeeName(fullName != null && !fullName.isBlank() ? fullName.trim() : empName);

        dto.setDeptCode(rs.getString("dept_code"));
        dto.setDeptName(rs.getString("dept_desc"));

        dto.setAreaCode(rs.getString("area_code"));
        dto.setAreaName(rs.getString("area_desc"));

        dto.setSuperCode(rs.getString("super_code"));
        dto.setSuperName(rs.getString("super_name"));

        dto.setTurnCode(rs.getString("turn_code"));
        dto.setTurnName(rs.getString("turn_desc"));

        dto.setRollCode(rs.getString("roll_code"));
        dto.setRollName(rs.getString("roll_desc"));

        return dto;
    };

    public Optional<PremployeeDTO> findByEmployeeNum(int employeeNum) {
        List<PremployeeDTO> results = jdbcTemplate.query(
            BASE_QUERY + " WHERE e.employee_num = ?", rowMapper, employeeNum);
        if (!results.isEmpty()) return results.stream().findFirst();

        String padded = String.format("%06d", employeeNum);
        results = jdbcTemplate.query(
            BASE_QUERY + " WHERE e.employee_code = ?", rowMapper, padded);
        return results.stream().findFirst();
    }

    public List<PremployeeDTO> searchByName(String name) {
        return jdbcTemplate.query(
            BASE_QUERY + " WHERE e.full_name LIKE ?", rowMapper, "%" + name + "%");
    }
}