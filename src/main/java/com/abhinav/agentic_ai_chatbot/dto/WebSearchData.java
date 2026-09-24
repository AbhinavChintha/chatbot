package com.abhinav.agentic_ai_chatbot.dto;

import java.util.List;

public class WebSearchData {

    private List<WebSearchResult> results;

    public WebSearchData() {
    }

    public WebSearchData(List<WebSearchResult> results) {
        this.results = results;
    }

    public List<WebSearchResult> getResults() {
        return results;
    }

    public void setResults(List<WebSearchResult> results) {
        this.results = results;
    }
}