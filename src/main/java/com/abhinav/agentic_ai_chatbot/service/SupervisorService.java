package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import com.abhinav.agentic_ai_chatbot.dto.RagData;
import com.abhinav.agentic_ai_chatbot.dto.SupervisorDecision;
import com.abhinav.agentic_ai_chatbot.dto.ToolResult;
import com.abhinav.agentic_ai_chatbot.dto.WebSearchData;
import com.abhinav.agentic_ai_chatbot.dto.WebSearchResult;
import com.abhinav.agentic_ai_chatbot.entity.Employee;
import com.abhinav.agentic_ai_chatbot.tool.DatabaseTool;
import com.abhinav.agentic_ai_chatbot.tool.RagTool;
import com.abhinav.agentic_ai_chatbot.tool.WebSearchTool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupervisorService {

    private final DatabaseTool databaseTool;
    private final RagTool ragTool;
    private final WebSearchTool webSearchTool;
    private final ChatService chatService;

    public SupervisorService(
            DatabaseTool databaseTool,
            RagTool ragTool,
            WebSearchTool webSearchTool,
            ChatService chatService) {

        this.databaseTool = databaseTool;
        this.ragTool = ragTool;
        this.webSearchTool = webSearchTool;
        this.chatService = chatService;
    }

    public ChatResponse execute(
            SupervisorDecision decision,
            String originalMessage) {

        String intent = decision.getIntent();

        if (intent == null || intent.isBlank()) {

            return new ChatResponse(
                    "Unable to determine the request type.",
                    "Supervisor Agent",
                    "UNKNOWN",
                    false,
                    null
            );
        }

        switch (intent.toUpperCase()) {

            case "DATABASE":
                return executeDatabase(decision);

            case "RAG":
                return executeRag(
                        decision,
                        originalMessage
                );

            case "WEB_SEARCH":
                return executeWebSearch(
                        decision,
                        originalMessage
                );

            case "GENERAL":
                return executeGeneral(
                        originalMessage
                );

            case "MULTI_TOOL":
                return executeMultiTool(
                        decision,
                        originalMessage
                );

            default:
                return new ChatResponse(
                        "Unable to determine how to process the request.",
                        "Supervisor Agent",
                        "UNKNOWN",
                        false,
                        null
                );
        }
    }

    // =========================================================
    // DATABASE
    // =========================================================

    private ChatResponse executeDatabase(
            SupervisorDecision decision) {

        List<String> requestedFields =
                decision.getRequestedFields();

        if (requestedFields != null
                && !requestedFields.isEmpty()) {

            List<String> supportedFields = List.of(
                    "id",
                    "employeeid",
                    "name",
                    "employeename",
                    "department",
                    "role",
                    "email",
                    "location"
            );

            List<String> unsupportedFields =
                    requestedFields.stream()
                            .filter(field ->
                                    field != null
                                            && !supportedFields.contains(
                                            field.trim().toLowerCase()
                                    )
                            )
                            .toList();

            if (!unsupportedFields.isEmpty()) {

                String fields =
                        String.join(
                                ", ",
                                unsupportedFields
                        );

                return new ChatResponse(
                        "The requested information ("
                                + fields
                                + ") is not available in the employee database.",
                        "Employee Database",
                        "DATABASE",
                        false,
                        null
                );
            }
        }

        ToolResult databaseResult;

        if (decision.getEmployeeId() != null) {

            databaseResult =
                    databaseTool.getEmployeeDetails(
                            decision.getEmployeeId()
                    );

        } else if (decision.getEmployeeName() != null
                && !decision.getEmployeeName().isBlank()) {

            databaseResult =
                    databaseTool.getEmployeeDetailsByName(
                            decision.getEmployeeName()
                    );

        } else if (decision.getDepartment() != null
                && !decision.getDepartment().isBlank()) {

            databaseResult =
                    databaseTool.getEmployeesByDepartment(
                            decision.getDepartment()
                    );

        } else if (decision.getLocation() != null
                && !decision.getLocation().isBlank()) {

            databaseResult =
                    databaseTool.getEmployeesByLocation(
                            decision.getLocation()
                    );

        } else if (decision.getRole() != null
                && !decision.getRole().isBlank()) {

            databaseResult =
                    databaseTool.getEmployeesByRole(
                            decision.getRole()
                    );

        } else {

            return new ChatResponse(
                    "No matching employee information was found in the database.",
                    "Employee Database",
                    "DATABASE",
                    false,
                    null
            );
        }

        Object data = databaseResult.getData();

        if (data instanceof Employee employee) {

            String answer = """
                    Employee Details:

                    ID: %s
                    Name: %s
                    Department: %s
                    Role: %s
                    Email: %s
                    Location: %s
                    """.formatted(
                    employee.getId(),
                    employee.getName(),
                    employee.getDepartment(),
                    employee.getRole(),
                    employee.getEmail(),
                    employee.getLocation()
            );

            return new ChatResponse(
                    answer,
                    "Employee Database",
                    "DATABASE",
                    databaseResult.isSuccess(),
                    employee
            );
        }

        if (data instanceof List<?> list) {

            List<Employee> employees =
                    extractEmployees(list);

            return new ChatResponse(
                    formatEmployees(employees),
                    "Employee Database",
                    "DATABASE",
                    databaseResult.isSuccess(),
                    employees
            );
        }

        return new ChatResponse(
                databaseResult.getAnswer(),
                "Employee Database",
                "DATABASE",
                databaseResult.isSuccess(),
                data
        );
    }

    // =========================================================
    // RAG
    // =========================================================

    private ChatResponse executeRag(
            SupervisorDecision decision,
            String originalMessage) {

        ToolResult ragResult =
                ragTool.search(originalMessage);

        List<String> sources =
                extractSources(
                        ragResult.getData()
                );

        RagData ragData =
                new RagData(sources);

        String source =
                sources.isEmpty()
                        ? "Company Documents"
                        : String.join(", ", sources);

        return new ChatResponse(
                ragResult.getAnswer(),
                source,
                "RAG",
                ragResult.isSuccess(),
                ragData
        );
    }

    // =========================================================
    // WEB SEARCH
    // =========================================================

    private ChatResponse executeWebSearch(
            SupervisorDecision decision,
            String originalMessage) {

        ToolResult webResult =
                webSearchTool.search(originalMessage);

        WebSearchData webSearchData =
                new WebSearchData(
                        extractWebResults(
                                webResult.getData()
                        )
                );

        return new ChatResponse(
                webResult.getAnswer(),
                "Web Search",
                "WEB_SEARCH",
                webResult.isSuccess(),
                webSearchData
        );
    }

    // =========================================================
    // GENERAL
    // =========================================================

    private ChatResponse executeGeneral(
            String originalMessage) {

        String answer =
                chatService.chat(originalMessage);

        return new ChatResponse(
                answer,
                "LLM",
                "GENERAL",
                true,
                null
        );
    }

    // =========================================================
    // MULTI TOOL
    // =========================================================

    private ChatResponse executeMultiTool(
            SupervisorDecision decision,
            String originalMessage) {

        List<String> requiredTools =
                decision.getRequiredTools();

        Map<String, String> toolQueries =
                decision.getToolQueries();

        if (requiredTools == null
                || requiredTools.isEmpty()) {

            return new ChatResponse(
                    "The supervisor did not specify the required tools.",
                    "Supervisor Agent",
                    "MULTI_TOOL",
                    false,
                    null
            );
        }

        // =====================================================
        // DATABASE + RAG + WEB SEARCH
        // =====================================================

        if (requiredTools.contains("DATABASE")
                && requiredTools.contains("RAG")
                && requiredTools.contains("WEB_SEARCH")) {

            ToolResult databaseResult =
                    getDatabaseResult(decision);

            ToolResult ragResult =
                    ragTool.search(
                            getToolQuery(
                                    toolQueries,
                                    "RAG",
                                    originalMessage
                            )
                    );

            ToolResult webResult =
                    webSearchTool.search(
                            getToolQuery(
                                    toolQueries,
                                    "WEB_SEARCH",
                                    originalMessage
                            )
                    );

            Employee employee =
                    extractEmployee(
                            databaseResult.getData()
                    );

            String databaseContext =
                    buildDatabaseContext(
                            databaseResult
                    );

            String context = """
                    %s

                    Company document information:

                    %s

                    Current external information:

                    %s

                    Original user question:

                    %s

                    Answer the user's question using the
                    information provided above.

                    Do not invent information.

                    If the database does not contain a matching employee,
                    clearly state that.

                    If the company documents do not contain the requested
                    information, clearly state that.

                    If the web search does not provide enough information,
                    clearly state that.
                    """.formatted(
                    databaseContext,
                    ragResult.getAnswer(),
                    webResult.getAnswer(),
                    originalMessage
            );

            String finalAnswer =
                    chatService.chat(context);

            Map<String, Object> multiToolData =
                    new LinkedHashMap<>();

            multiToolData.put(
                    "database",
                    employee
            );

            multiToolData.put(
                    "rag",
                    new RagData(
                            extractSources(
                                    ragResult.getData()
                            )
                    )
            );

            multiToolData.put(
                    "webSearch",
                    new WebSearchData(
                            extractWebResults(
                                    webResult.getData()
                            )
                    )
            );

            boolean overallSuccess =
                    databaseResult.isSuccess()
                            && ragResult.isSuccess()
                            && webResult.isSuccess();

            return new ChatResponse(
                    finalAnswer,
                    "Employee Database, Company Documents, Web Search",
                    "MULTI_TOOL",
                    overallSuccess,
                    multiToolData
            );
        }

        // =====================================================
        // DATABASE + RAG
        // =====================================================

        if (requiredTools.contains("DATABASE")
                && requiredTools.contains("RAG")) {

            ToolResult databaseResult =
                    getDatabaseResult(decision);

            ToolResult ragResult =
                    ragTool.search(
                            getToolQuery(
                                    toolQueries,
                                    "RAG",
                                    originalMessage
                            )
                    );

            String databaseContext =
                    buildDatabaseContext(
                            databaseResult
                    );

            String context = """
                    %s

                    Company document information:

                    %s

                    Original user question:

                    %s

                    Answer the user's question using the employee
                    information and company document information above.

                    Do not invent information.

                    If the employee was not found in the database,
                    clearly state that.

                    If the company documents do not contain the requested
                    information, clearly state that.
                    """.formatted(
                    databaseContext,
                    ragResult.getAnswer(),
                    originalMessage
            );

            String finalAnswer =
                    chatService.chat(context);

            Map<String, Object> multiToolData =
                    new LinkedHashMap<>();

            multiToolData.put(
                    "database",
                    extractEmployee(
                            databaseResult.getData()
                    )
            );

            multiToolData.put(
                    "rag",
                    new RagData(
                            extractSources(
                                    ragResult.getData()
                            )
                    )
            );

            boolean overallSuccess =
                    databaseResult.isSuccess()
                            && ragResult.isSuccess();

            return new ChatResponse(
                    finalAnswer,
                    "Employee Database, Company Documents",
                    "MULTI_TOOL",
                    overallSuccess,
                    multiToolData
            );
        }

        // =====================================================
        // DATABASE + WEB SEARCH
        // =====================================================

        if (requiredTools.contains("DATABASE")
                && requiredTools.contains("WEB_SEARCH")) {

            ToolResult databaseResult =
                    getDatabaseResult(decision);

            ToolResult webResult =
                    webSearchTool.search(
                            getToolQuery(
                                    toolQueries,
                                    "WEB_SEARCH",
                                    originalMessage
                            )
                    );

            String databaseContext =
                    buildDatabaseContext(
                            databaseResult
                    );

            String context = """
                    %s

                    Current external information:

                    %s

                    Original user question:

                    %s

                    Answer the user's question using the
                    employee information and current external
                    information above.

                    Do not invent information.

                    If the employee was not found in the database,
                    clearly state that.

                    If the web search does not provide enough information,
                    clearly state that.
                    """.formatted(
                    databaseContext,
                    webResult.getAnswer(),
                    originalMessage
            );

            String finalAnswer =
                    chatService.chat(context);

            Map<String, Object> multiToolData =
                    new LinkedHashMap<>();

            multiToolData.put(
                    "database",
                    extractEmployee(
                            databaseResult.getData()
                    )
            );

            multiToolData.put(
                    "webSearch",
                    new WebSearchData(
                            extractWebResults(
                                    webResult.getData()
                            )
                    )
            );

            boolean overallSuccess =
                    databaseResult.isSuccess()
                            && webResult.isSuccess();

            return new ChatResponse(
                    finalAnswer,
                    "Employee Database, Web Search",
                    "MULTI_TOOL",
                    overallSuccess,
                    multiToolData
            );
        }

        // =====================================================
        // RAG + WEB SEARCH
        // =====================================================

        if (requiredTools.contains("RAG")
                && requiredTools.contains("WEB_SEARCH")) {

            ToolResult ragResult =
                    ragTool.search(
                            getToolQuery(
                                    toolQueries,
                                    "RAG",
                                    originalMessage
                            )
                    );

            ToolResult webResult =
                    webSearchTool.search(
                            getToolQuery(
                                    toolQueries,
                                    "WEB_SEARCH",
                                    originalMessage
                            )
                    );

            String context = """
                    Company document information:

                    %s

                    Current external information:

                    %s

                    Original user question:

                    %s

                    Answer the user's question using the
                    information provided above.

                    Do not invent information.

                    If the company documents do not contain the requested
                    information, clearly state that.

                    If the web search does not provide enough information,
                    clearly state that.
                    """.formatted(
                    ragResult.getAnswer(),
                    webResult.getAnswer(),
                    originalMessage
            );

            String finalAnswer =
                    chatService.chat(context);

            Map<String, Object> multiToolData =
                    new LinkedHashMap<>();

            multiToolData.put(
                    "rag",
                    new RagData(
                            extractSources(
                                    ragResult.getData()
                            )
                    )
            );

            multiToolData.put(
                    "webSearch",
                    new WebSearchData(
                            extractWebResults(
                                    webResult.getData()
                            )
                    )
            );

            boolean overallSuccess =
                    ragResult.isSuccess()
                            && webResult.isSuccess();

            return new ChatResponse(
                    finalAnswer,
                    "Company Documents, Web Search",
                    "MULTI_TOOL",
                    overallSuccess,
                    multiToolData
            );
        }

        return new ChatResponse(
                "The requested combination of tools is not currently supported.",
                "Supervisor Agent",
                "MULTI_TOOL",
                false,
                null
        );
    }

    // =========================================================
    // DATABASE RESULT
    // =========================================================

    private ToolResult getDatabaseResult(
            SupervisorDecision decision) {

        if (decision.getEmployeeId() != null) {

            return databaseTool.getEmployeeDetails(
                    decision.getEmployeeId()
            );
        }

        if (decision.getEmployeeName() != null
                && !decision.getEmployeeName().isBlank()) {

            return databaseTool.getEmployeeDetailsByName(
                    decision.getEmployeeName()
            );
        }

        if (decision.getDepartment() != null
                && !decision.getDepartment().isBlank()) {

            return databaseTool.getEmployeesByDepartment(
                    decision.getDepartment()
            );
        }

        if (decision.getLocation() != null
                && !decision.getLocation().isBlank()) {

            return databaseTool.getEmployeesByLocation(
                    decision.getLocation()
            );
        }

        if (decision.getRole() != null
                && !decision.getRole().isBlank()) {

            return databaseTool.getEmployeesByRole(
                    decision.getRole()
            );
        }

        return new ToolResult(
                "DATABASE",
                false,
                "No database lookup criteria were provided.",
                null
        );
    }

    // =========================================================
    // DATABASE CONTEXT
    // =========================================================

    private String buildDatabaseContext(
            ToolResult databaseResult) {

        Employee employee =
                extractEmployee(
                        databaseResult.getData()
                );

        if (employee != null) {

            return """
                    Employee information:

                    ID: %s
                    Name: %s
                    Department: %s
                    Role: %s
                    Email: %s
                    Location: %s
                    """.formatted(
                    employee.getId(),
                    employee.getName(),
                    employee.getDepartment(),
                    employee.getRole(),
                    employee.getEmail(),
                    employee.getLocation()
            );
        }

        List<Employee> employees =
                extractEmployees(
                        databaseResult.getData()
                );

        if (!employees.isEmpty()) {
            return formatEmployees(employees);
        }

        return """
                Employee information:

                No matching employee information was found in the database.
                """;
    }

    // =========================================================
    // EMPLOYEE EXTRACTION
    // =========================================================

    private Employee extractEmployee(
            Object data) {

        if (data instanceof Employee employee) {
            return employee;
        }

        return null;
    }

    // =========================================================
    // EMPLOYEE LIST EXTRACTION
    // =========================================================

    private List<Employee> extractEmployees(
            Object data) {

        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(item ->
                        item instanceof Employee
                )
                .map(item ->
                        (Employee) item
                )
                .toList();
    }

    // =========================================================
    // RAG SOURCE EXTRACTION
    // =========================================================

    private List<String> extractSources(
            Object data) {

        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(item ->
                        item instanceof String
                )
                .map(item ->
                        (String) item
                )
                .toList();
    }

    // =========================================================
    // WEB SEARCH RESULT EXTRACTION
    // =========================================================

    private List<WebSearchResult> extractWebResults(
            Object data) {

        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(item ->
                        item instanceof WebSearchResult
                )
                .map(item ->
                        (WebSearchResult) item
                )
                .toList();
    }

    // =========================================================
    // TOOL QUERY HELPER
    // =========================================================

    private String getToolQuery(
            Map<String, String> toolQueries,
            String toolName,
            String originalMessage) {

        if (toolQueries != null
                && toolQueries.get(toolName) != null
                && !toolQueries.get(toolName).isBlank()) {

            return toolQueries.get(toolName);
        }

        return originalMessage;
    }

    // =========================================================
    // FORMAT EMPLOYEES
    // =========================================================

    private String formatEmployees(
            List<Employee> employees) {

        if (employees == null
                || employees.isEmpty()) {

            return "No matching employees were found.";
        }

        StringBuilder result =
                new StringBuilder(
                        "Matching Employees:\n\n"
                );

        for (Employee employee : employees) {

            result.append(
                    """
                    ID: %s
                    Name: %s
                    Department: %s
                    Role: %s
                    Email: %s
                    Location: %s

                    """.formatted(
                            employee.getId(),
                            employee.getName(),
                            employee.getDepartment(),
                            employee.getRole(),
                            employee.getEmail(),
                            employee.getLocation()
                    )
            );
        }

        return result.toString();
    }
}