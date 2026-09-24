package com.abhinav.agentic_ai_chatbot.dto;

public class WebSearchResult {

    private String title;
    private String url;
    private String snippet;

    public WebSearchResult() {
    }

    public WebSearchResult(
            String title,
            String url,
            String snippet) {

        this.title = title;
        this.url = url;
        this.snippet = snippet;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}