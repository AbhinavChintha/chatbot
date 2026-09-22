package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.dto.SupervisorDecision;
import com.abhinav.agentic_ai_chatbot.tool.DatabaseTool;
import org.springframework.stereotype.Service;

@Service
public class SupervisorService {

    private final DatabaseTool databaseTool;
    private final ChatService chatService;

    public SupervisorService(
            DatabaseTool databaseTool,
            ChatService chatService) {

        this.databaseTool = databaseTool;
        this.chatService = chatService;
    }

    public String execute(SupervisorDecision decision, String originalMessage) {

        if ("DATABASE".equalsIgnoreCase(decision.getIntent())) {

            if ("EMPLOYEE".equalsIgnoreCase(decision.getEntity())) {

                if (decision.getEmployeeId() == null) {
                    return "Please provide an employee ID.";
                }

                return databaseTool.getEmployeeDetails(
                        decision.getEmployeeId()
                );
            }

            return "The requested database entity is not supported yet.";
        }

        if ("GENERAL".equalsIgnoreCase(decision.getIntent())) {
            return chatService.chat(originalMessage);
        }

        if ("RAG".equalsIgnoreCase(decision.getIntent())) {
            return "RAG capability is not connected yet.";
        }

        if ("WEB_SEARCH".equalsIgnoreCase(decision.getIntent())) {
            return "Web search capability is not connected yet.";
        }

        return "I could not determine how to handle your request.";
    }
}