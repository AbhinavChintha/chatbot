package com.abhinav.agentic_ai_chatbot.tool;

import com.abhinav.agentic_ai_chatbot.service.DatabaseService;
import org.springframework.stereotype.Component;

@Component
public class DatabaseTool {

    private final DatabaseService databaseService;

    public DatabaseTool(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public String getEmployeeDetails(Long employeeId) {

        return databaseService.getEmployeeDetails(employeeId);
    }
}