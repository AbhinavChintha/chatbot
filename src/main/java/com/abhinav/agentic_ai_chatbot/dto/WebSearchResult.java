package com.abhinav.agentic_ai_chatbot.dto;

public class WebSearchResult {

    private String title;
    private String url;
    private String snippet;
    private String date;

    public WebSearchResult() {
    }

    public WebSearchResult(
            String title,
            String url,
            String snippet) {

        this(title, url, snippet, null);
    }

    public WebSearchResult(
            String title,
            String url,
            String snippet,
            String date) {

        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.date = date;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}