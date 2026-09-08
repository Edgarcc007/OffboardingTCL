package com.empresa.offboarding.dto;

public class PremployeeDTO {
    private String employeeCode;
    private int employeeNum;
    private String employeeName;
    private String deptCode;
    private String areaCode;
    private String superCode;

    public String getEmployeeCode()  { return employeeCode; }
    public void setEmployeeCode(String v) { this.employeeCode = v; }

    public int getEmployeeNum()      { return employeeNum; }
    public void setEmployeeNum(int v) { this.employeeNum = v; }

    public String getEmployeeName()  { return employeeName; }
    public void setEmployeeName(String v) { this.employeeName = v; }

    public String getDeptCode()      { return deptCode; }
    public void setDeptCode(String v) { this.deptCode = v; }

    public String getAreaCode()      { return areaCode; }
    public void setAreaCode(String v) { this.areaCode = v; }

    public String getSuperCode()     { return superCode; }
    public void setSuperCode(String v) { this.superCode = v; }
}