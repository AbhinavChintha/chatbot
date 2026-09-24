package com.abhinav.agentic_ai_chatbot.dto;

public class ToolResult {

    private String tool;
    private boolean success;
    private String answer;
    private Object data;

    public ToolResult() {
    }

    public ToolResult(
            String tool,
            boolean success,
            String answer,
            Object data) {

        this.tool = tool;
        this.success = success;
        this.answer = answer;
        this.data = data;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}