package com.abhinav.agentic_ai_chatbot.dto;

import java.util.List;

public class RagData {

    private List<String> documents;

    public RagData() {
    }

    public RagData(List<String> documents) {
        this.documents = documents;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public void setDocuments(List<String> documents) {
        this.documents = documents;
    }
}