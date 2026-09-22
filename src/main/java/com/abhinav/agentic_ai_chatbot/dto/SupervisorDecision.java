package com.abhinav.agentic_ai_chatbot.dto;

public class SupervisorDecision {

    private String intent;
    private String entity;
    private Long employeeId;

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
}