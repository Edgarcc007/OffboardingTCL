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
        "SELECT employee_code, employee_num, full_name, employee_name, " +
        "dept_code, area_code, super_code " +
        "FROM dbo.premployee";

    private final RowMapper<PremployeeDTO> rowMapper = (rs, rowNum) -> {
        PremployeeDTO dto = new PremployeeDTO();
        dto.setEmployeeCode(rs.getString("employee_code"));
        dto.setEmployeeNum(rs.getInt("employee_num"));

        String fullName = rs.getString("full_name");
        String empName  = rs.getString("employee_name");
        dto.setEmployeeName(fullName != null && !fullName.isBlank() ? fullName.trim() : empName);

        dto.setDeptCode(rs.getString("dept_code"));
        dto.setAreaCode(rs.getString("area_code"));
        dto.setSuperCode(rs.getString("super_code"));
        return dto;
    };

    public Optional<PremployeeDTO> findByEmployeeNum(int employeeNum) {
        List<PremployeeDTO> results = jdbcTemplate.query(
            BASE_QUERY + " WHERE employee_num = ?", rowMapper, employeeNum);
        if (!results.isEmpty()) return results.stream().findFirst();

        String padded = String.format("%06d", employeeNum);
        results = jdbcTemplate.query(
            BASE_QUERY + " WHERE employee_code = ?", rowMapper, padded);
        return results.stream().findFirst();
    }

    public List<PremployeeDTO> searchByName(String name) {
        return jdbcTemplate.query(
            BASE_QUERY + " WHERE full_name LIKE ?", rowMapper, "%" + name + "%");
    }
}