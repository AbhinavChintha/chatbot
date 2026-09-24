package com.abhinav.agentic_ai_chatbot.dto;

import java.util.List;
import java.util.Map;

public class ToolQuery {

    private String entity;
    private Map<String, Object> filters;
    private List<String> fields;

    public ToolQuery() {
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}