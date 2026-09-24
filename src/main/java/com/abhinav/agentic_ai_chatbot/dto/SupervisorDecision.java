package com.abhinav.agentic_ai_chatbot.dto;

import java.util.List;
import java.util.Map;

public class SupervisorDecision {

    private String intent;

    private String entity;

    private Long employeeId;

    private String employeeName;

    private String department;

    private String location;

    private String role;

    private List<String> requestedFields;

    private List<String> requiredTools;

    /*
     * The LLM may return toolQueries in different formats.
     *
     * Example 1:
     *
     * "toolQueries": {
     *     "DATABASE": {
     *         "entity": "EMPLOYEE",
     *         "filters": {
     *             "id": 101
     *         },
     *         "fields": [
     *             "role"
     *         ]
     *     }
     * }
     *
     * Example 2:
     *
     * "toolQueries": {
     *     "DATABASE": "SELECT role FROM employees WHERE id = 101"
     * }
     *
     * Object allows Jackson to safely accept both formats.
     *
     * IMPORTANT:
     * The SQL string returned by the LLM is never executed directly.
     * Actual database access is controlled by DatabaseTool.
     */
    private Map<String, Object> toolQueries;

    public SupervisorDecision() {
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getRequestedFields() {
        return requestedFields;
    }

    public void setRequestedFields(List<String> requestedFields) {
        this.requestedFields = requestedFields;
    }

    public List<String> getRequiredTools() {
        return requiredTools;
    }

    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools;
    }

    public Map<String, Object> getToolQueries() {
        return toolQueries;
    }

    public void setToolQueries(Map<String, Object> toolQueries) {
        this.toolQueries = toolQueries;
    }

    @Override
    public String toString() {
        return "SupervisorDecision{" +
                "intent='" + intent + '\'' +
                ", entity='" + entity + '\'' +
                ", employeeId=" + employeeId +
                ", employeeName='" + employeeName + '\'' +
                ", department='" + department + '\'' +
                ", location='" + location + '\'' +
                ", role='" + role + '\'' +
                ", requestedFields=" + requestedFields +
                ", requiredTools=" + requiredTools +
                ", toolQueries=" + toolQueries +
                '}';
    }
}