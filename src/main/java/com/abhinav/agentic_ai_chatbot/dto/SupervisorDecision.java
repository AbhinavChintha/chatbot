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

    private Map<String, String> toolQueries;

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

    public Map<String, String> getToolQueries() {
        return toolQueries;
    }

    public void setToolQueries(Map<String, String> toolQueries) {
        this.toolQueries = toolQueries;
    }
}