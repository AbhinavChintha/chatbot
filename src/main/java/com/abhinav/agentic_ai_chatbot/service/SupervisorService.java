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

import java.util.ArrayList;
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

        if (decision == null) {

            return new ChatResponse(
                    "Unable to determine the request type.",
                    "Supervisor Agent",
                    "UNKNOWN",
                    false,
                    null
            );
        }

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

        /*
         * Validate requested fields before querying the database.
         *
         * These are the only employee fields currently supported.
         */
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

        ToolResult result;

        /*
         * Priority:
         *
         * 1. Employee ID
         * 2. Employee name
         * 3. Department
         * 4. Location
         * 5. Role
         */
        if (decision.getEmployeeId() != null) {

            result =
                    databaseTool.getEmployeeDetails(
                            decision.getEmployeeId()
                    );

        } else if (decision.getEmployeeName() != null
                && !decision.getEmployeeName().isBlank()) {

            result =
                    databaseTool.getEmployeeDetailsByName(
                            decision.getEmployeeName()
                    );

        } else if (decision.getDepartment() != null
                && !decision.getDepartment().isBlank()) {

            result =
                    databaseTool.getEmployeesByDepartment(
                            decision.getDepartment()
                    );

        } else if (decision.getLocation() != null
                && !decision.getLocation().isBlank()) {

            result =
                    databaseTool.getEmployeesByLocation(
                            decision.getLocation()
                    );

        } else if (decision.getRole() != null
                && !decision.getRole().isBlank()) {

            result =
                    databaseTool.getEmployeesByRole(
                            decision.getRole()
                    );

        } else {

            return new ChatResponse(
                    "No valid employee search criteria were provided.",
                    "Employee Database",
                    "DATABASE",
                    false,
                    null
            );
        }

        if (result == null) {

            return new ChatResponse(
                    "The database tool did not return a result.",
                    "Employee Database",
                    "DATABASE",
                    false,
                    null
            );
        }

        /*
         * Handle database failure.
         */
        if (!result.isSuccess()) {

            return new ChatResponse(
                    result.getAnswer(),
                    "Employee Database",
                    "DATABASE",
                    false,
                    result.getData()
            );
        }

        /*
         * Single employee response.
         */
        if (result.getData() instanceof Employee employee) {

            String answer =
                    buildEmployeeAnswer(
                            employee,
                            requestedFields
                    );

            return new ChatResponse(
                    answer,
                    "Employee Database",
                    "DATABASE",
                    true,
                    employee
            );
        }

        /*
         * Multiple employees response.
         */
        if (result.getData() instanceof List<?> list) {

            List<Employee> employees =
                    new ArrayList<>();

            for (Object item : list) {

                if (item instanceof Employee employee) {
                    employees.add(employee);
                }
            }

            return new ChatResponse(
                    formatEmployees(employees),
                    "Employee Database",
                    "DATABASE",
                    true,
                    employees
            );
        }

        return new ChatResponse(
                result.getAnswer(),
                "Employee Database",
                "DATABASE",
                true,
                result.getData()
        );
    }

    // =========================================================
    // RAG
    // =========================================================

    private ChatResponse executeRag(
            SupervisorDecision decision,
            String originalMessage) {

        String query =
                getToolQuery(
                        decision.getToolQueries(),
                        "RAG",
                        originalMessage
                );

        System.out.println(
                "RAG tool query: "
                        + query
        );

        ToolResult result =
                ragTool.search(query);

        if (result == null) {

            return new ChatResponse(
                    "RAG tool did not return a result.",
                    "Company Documents",
                    "RAG",
                    false,
                    null
            );
        }

        List<String> sources =
                extractSources(result.getData());

        RagData ragData =
                new RagData(sources);

        String source =
                sources.isEmpty()
                        ? "Company Documents"
                        : "Company Documents, "
                        + String.join(
                        ", ",
                        sources
                );

        return new ChatResponse(
                result.getAnswer(),
                source,
                "RAG",
                result.isSuccess(),
                ragData
        );
    }

    // =========================================================
    // WEB SEARCH
    // =========================================================

    private ChatResponse executeWebSearch(
            SupervisorDecision decision,
            String originalMessage) {

        String query =
                getToolQuery(
                        decision.getToolQueries(),
                        "WEB_SEARCH",
                        originalMessage
                );

        System.out.println(
                "WEB_SEARCH tool query: "
                        + query
        );

        ToolResult result =
                webSearchTool.search(query);

        if (result == null) {

            return new ChatResponse(
                    "Web search did not return a result.",
                    "Web Search",
                    "WEB_SEARCH",
                    false,
                    null
            );
        }

        WebSearchData webSearchData =
                new WebSearchData(
                        extractWebResults(
                                result.getData()
                        )
                );

        return new ChatResponse(
                result.getAnswer(),
                "Web Search",
                "WEB_SEARCH",
                result.isSuccess(),
                webSearchData
        );
    }

    // =========================================================
    // GENERAL
    // =========================================================

    private ChatResponse executeGeneral(
            String originalMessage) {

        String answer =
                chatService.chat(
                        originalMessage
                );

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

        /*
         * DATABASE + RAG + WEB SEARCH
         *
         * This must be checked first because it contains
         * all three tools.
         */
        if (requiredTools.contains("DATABASE")
                && requiredTools.contains("RAG")
                && requiredTools.contains("WEB_SEARCH")) {

            return executeDatabaseRagWeb(
                    decision,
                    originalMessage
            );
        }

        /*
         * DATABASE + RAG
         */
        if (requiredTools.contains("DATABASE")
                && requiredTools.contains("RAG")) {

            return executeDatabaseRag(
                    decision,
                    originalMessage
            );
        }

        /*
         * DATABASE + WEB SEARCH
         */
        if (requiredTools.contains("DATABASE")
                && requiredTools.contains("WEB_SEARCH")) {

            return executeDatabaseWeb(
                    decision,
                    originalMessage
            );
        }

        /*
         * RAG + WEB SEARCH
         */
        if (requiredTools.contains("RAG")
                && requiredTools.contains("WEB_SEARCH")) {

            return executeRagWeb(
                    decision,
                    originalMessage
            );
        }

        /*
         * Fallback.
         */
        return new ChatResponse(
                "The requested combination of tools is not supported.",
                "Supervisor Agent",
                "MULTI_TOOL",
                false,
                null
        );
    }

    // =========================================================
    // DATABASE + RAG
    // =========================================================

    private ChatResponse executeDatabaseRag(
            SupervisorDecision decision,
            String originalMessage) {

        ToolResult databaseResult =
                executeDatabaseTool(decision);

        String databaseContext;

        Employee employee =
                extractEmployee(
                        databaseResult
                );

        List<Employee> employees =
                extractEmployees(
                        databaseResult
                );

        boolean databaseSuccess =
                databaseResult != null
                        && databaseResult.isSuccess();

        if (employee != null) {

            databaseContext =
                    buildEmployeeContext(
                            employee
                    );

        } else if (!employees.isEmpty()) {

            databaseContext =
                    buildEmployeesContext(
                            employees
                    );

        } else {

            databaseContext =
                    "No matching employee information was found in the database.";
        }

        String ragQuery =
                getToolQuery(
                        decision.getToolQueries(),
                        "RAG",
                        originalMessage
                );

        System.out.println(
                "RAG tool query: "
                        + ragQuery
        );

        ToolResult ragResult =
                ragTool.search(ragQuery);

        boolean ragSuccess =
                ragResult != null
                        && ragResult.isSuccess();

        String ragAnswer =
                ragResult == null
                        ? "RAG tool did not return a result."
                        : ragResult.getAnswer();

        String context = """
                Employee Database Information:

                %s

                Company Document Information:

                %s

                Original User Question:

                %s

                Answer the user's question using the information above.

                Do not invent information.

                If employee information was not found,
                clearly state that.

                If company documents do not provide enough
                information, clearly state that.
                """.formatted(
                databaseContext,
                ragAnswer,
                originalMessage
        );

        String finalAnswer =
                chatService.chat(context);

        List<String> sources =
                extractSources(
                        ragResult == null
                                ? null
                                : ragResult.getData()
                );

        String source =
                sources.isEmpty()
                        ? "Employee Database, Company Documents"
                        : "Employee Database, Company Documents, "
                        + String.join(
                        ", ",
                        sources
                );

        Map<String, Object> multiToolData =
                new LinkedHashMap<>();

        multiToolData.put(
                "database",
                employee != null
                        ? employee
                        : employees
        );

        multiToolData.put(
                "rag",
                new RagData(sources)
        );

        boolean overallSuccess =
                databaseSuccess
                        && ragSuccess;

        return new ChatResponse(
                finalAnswer,
                source,
                "MULTI_TOOL",
                overallSuccess,
                multiToolData
        );
    }

    // =========================================================
    // DATABASE + WEB SEARCH
    // =========================================================

    private ChatResponse executeDatabaseWeb(
            SupervisorDecision decision,
            String originalMessage) {

        ToolResult databaseResult =
                executeDatabaseTool(decision);

        Employee employee =
                extractEmployee(
                        databaseResult
                );

        List<Employee> employees =
                extractEmployees(
                        databaseResult
                );

        boolean databaseSuccess =
                databaseResult != null
                        && databaseResult.isSuccess();

        String databaseContext;

        if (employee != null) {

            databaseContext =
                    buildEmployeeContext(
                            employee
                    );

        } else if (!employees.isEmpty()) {

            databaseContext =
                    buildEmployeesContext(
                            employees
                    );

        } else {

            databaseContext =
                    "No matching employee information was found in the database.";
        }

        String webQuery =
                getToolQuery(
                        decision.getToolQueries(),
                        "WEB_SEARCH",
                        originalMessage
                );

        System.out.println(
                "WEB_SEARCH tool query: "
                        + webQuery
        );

        ToolResult webResult =
                webSearchTool.search(webQuery);

        boolean webSuccess =
                webResult != null
                        && webResult.isSuccess();

        String webAnswer =
                webResult == null
                        ? "Web search did not return a result."
                        : webResult.getAnswer();

        String context = """
                Employee Database Information:

                %s

                Current External Information:

                %s

                Original User Question:

                %s

                Answer the user's question using the
                employee information and current external
                information above.

                Do not invent information.

                If the employee was not found in the database,
                clearly state that.

                If the web search does not provide enough
                information, clearly state that.
                """.formatted(
                databaseContext,
                webAnswer,
                originalMessage
        );

        String finalAnswer =
                chatService.chat(context);

        WebSearchData webSearchData =
                new WebSearchData(
                        extractWebResults(
                                webResult == null
                                        ? null
                                        : webResult.getData()
                        )
                );

        Map<String, Object> multiToolData =
                new LinkedHashMap<>();

        multiToolData.put(
                "database",
                employee != null
                        ? employee
                        : employees
        );

        multiToolData.put(
                "webSearch",
                webSearchData
        );

        boolean overallSuccess =
                databaseSuccess
                        && webSuccess;

        return new ChatResponse(
                finalAnswer,
                "Employee Database, Web Search",
                "MULTI_TOOL",
                overallSuccess,
                multiToolData
        );
    }

    // =========================================================
    // RAG + WEB SEARCH
    // =========================================================

    private ChatResponse executeRagWeb(
            SupervisorDecision decision,
            String originalMessage) {

        String ragQuery =
                getToolQuery(
                        decision.getToolQueries(),
                        "RAG",
                        originalMessage
                );

        String webQuery =
                getToolQuery(
                        decision.getToolQueries(),
                        "WEB_SEARCH",
                        originalMessage
                );

        System.out.println(
                "RAG tool query: "
                        + ragQuery
        );

        System.out.println(
                "WEB_SEARCH tool query: "
                        + webQuery
        );

        ToolResult ragResult =
                ragTool.search(ragQuery);

        ToolResult webResult =
                webSearchTool.search(webQuery);

        boolean ragSuccess =
                ragResult != null
                        && ragResult.isSuccess();

        boolean webSuccess =
                webResult != null
                        && webResult.isSuccess();

        String ragAnswer =
                ragResult == null
                        ? "RAG tool did not return a result."
                        : ragResult.getAnswer();

        String webAnswer =
                webResult == null
                        ? "Web search did not return a result."
                        : webResult.getAnswer();

        String context = """
                Company Document Information:

                %s

                Current External Information:

                %s

                Original User Question:

                %s

                Answer the user's question using the
                information provided above.

                Do not invent information.

                If the company documents do not provide
                enough information, clearly state that.

                If the web search does not provide enough
                information, clearly state that.
                """.formatted(
                ragAnswer,
                webAnswer,
                originalMessage
        );

        String finalAnswer =
                chatService.chat(context);

        List<String> ragSources =
                extractSources(
                        ragResult == null
                                ? null
                                : ragResult.getData()
                );

        WebSearchData webSearchData =
                new WebSearchData(
                        extractWebResults(
                                webResult == null
                                        ? null
                                        : webResult.getData()
                        )
                );

        Map<String, Object> multiToolData =
                new LinkedHashMap<>();

        multiToolData.put(
                "rag",
                new RagData(ragSources)
        );

        multiToolData.put(
                "webSearch",
                webSearchData
        );

        boolean overallSuccess =
                ragSuccess
                        && webSuccess;

        return new ChatResponse(
                finalAnswer,
                "Company Documents, Web Search",
                "MULTI_TOOL",
                overallSuccess,
                multiToolData
        );
    }

    // =========================================================
    // DATABASE + RAG + WEB SEARCH
    // =========================================================

    private ChatResponse executeDatabaseRagWeb(
            SupervisorDecision decision,
            String originalMessage) {

        ToolResult databaseResult =
                executeDatabaseTool(decision);

        Employee employee =
                extractEmployee(
                        databaseResult
                );

        List<Employee> employees =
                extractEmployees(
                        databaseResult
                );

        boolean databaseSuccess =
                databaseResult != null
                        && databaseResult.isSuccess();

        String databaseContext;

        if (employee != null) {

            databaseContext =
                    buildEmployeeContext(
                            employee
                    );

        } else if (!employees.isEmpty()) {

            databaseContext =
                    buildEmployeesContext(
                            employees
                    );

        } else {

            databaseContext =
                    "No matching employee information was found in the database.";
        }

        String ragQuery =
                getToolQuery(
                        decision.getToolQueries(),
                        "RAG",
                        originalMessage
                );

        String webQuery =
                getToolQuery(
                        decision.getToolQueries(),
                        "WEB_SEARCH",
                        originalMessage
                );

        System.out.println(
                "RAG tool query: "
                        + ragQuery
        );

        System.out.println(
                "WEB_SEARCH tool query: "
                        + webQuery
        );

        ToolResult ragResult =
                ragTool.search(ragQuery);

        ToolResult webResult =
                webSearchTool.search(webQuery);

        boolean ragSuccess =
                ragResult != null
                        && ragResult.isSuccess();

        boolean webSuccess =
                webResult != null
                        && webResult.isSuccess();

        String ragAnswer =
                ragResult == null
                        ? "RAG tool did not return a result."
                        : ragResult.getAnswer();

        String webAnswer =
                webResult == null
                        ? "Web search did not return a result."
                        : webResult.getAnswer();

        String context = """
                Employee Database Information:

                %s

                Company Document Information:

                %s

                Current External Information:

                %s

                Original User Question:

                %s

                Answer the user's question using the
                information provided above.

                Do not invent information.

                If employee information was not found,
                clearly state that.

                If company documents do not provide enough
                information, clearly state that.

                If web search does not provide enough
                information, clearly state that.
                """.formatted(
                databaseContext,
                ragAnswer,
                webAnswer,
                originalMessage
        );

        String finalAnswer =
                chatService.chat(context);

        List<String> ragSources =
                extractSources(
                        ragResult == null
                                ? null
                                : ragResult.getData()
                );

        WebSearchData webSearchData =
                new WebSearchData(
                        extractWebResults(
                                webResult == null
                                        ? null
                                        : webResult.getData()
                        )
                );

        Map<String, Object> multiToolData =
                new LinkedHashMap<>();

        multiToolData.put(
                "database",
                employee != null
                        ? employee
                        : employees
        );

        multiToolData.put(
                "rag",
                new RagData(ragSources)
        );

        multiToolData.put(
                "webSearch",
                webSearchData
        );

        boolean overallSuccess =
                databaseSuccess
                        && ragSuccess
                        && webSuccess;

        return new ChatResponse(
                finalAnswer,
                "Employee Database, Company Documents, Web Search",
                "MULTI_TOOL",
                overallSuccess,
                multiToolData
        );
    }

    // =========================================================
    // DATABASE TOOL EXECUTION
    // =========================================================

    private ToolResult executeDatabaseTool(
            SupervisorDecision decision) {

        List<String> requestedFields =
                decision.getRequestedFields();

        /*
         * Security validation:
         * never allow the LLM to retrieve fields that are
         * not part of the Employee database.
         */
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

                return new ToolResult(
                        "DATABASE",
                        false,
                        "The requested information ("
                                + String.join(
                                ", ",
                                unsupportedFields
                        )
                                + ") is not available in the employee database.",
                        null
                );
            }
        }

        /*
         * IMPORTANT:
         *
         * We intentionally DO NOT execute the SQL contained
         * inside toolQueries.
         *
         * The LLM may return:
         *
         * "DATABASE": "SELECT role FROM employees WHERE id = 101"
         *
         * but that SQL is treated only as metadata.
         *
         * Actual database access is performed through the
         * controlled DatabaseTool methods below.
         */

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
                "No valid employee search criteria were provided.",
                null
        );
    }

    // =========================================================
    // TOOL QUERY HELPER
    // =========================================================

    private String getToolQuery(
            Map<String, Object> toolQueries,
            String toolName,
            String originalMessage) {

        if (toolQueries == null
                || toolQueries.isEmpty()) {

            return originalMessage;
        }

        Object rawToolQuery =
                toolQueries.get(toolName);

        if (rawToolQuery == null) {

            return originalMessage;
        }

        /*
         * CASE 1:
         *
         * The LLM returned a plain string.
         *
         * Example:
         *
         * "RAG": "annual leave entitlement"
         *
         * or:
         *
         * "WEB_SEARCH": "latest Java news"
         *
         * For DATABASE, the string may contain SQL.
         * We NEVER execute that SQL.
         */
        if (rawToolQuery instanceof String stringQuery) {

            if (stringQuery.isBlank()) {
                return originalMessage;
            }

            /*
             * DATABASE SQL is never executed.
             *
             * Return the original user message instead.
             */
            if ("DATABASE".equalsIgnoreCase(toolName)) {

                return originalMessage;
            }

            return stringQuery.trim();
        }

        /*
         * CASE 2:
         *
         * The LLM returned a structured JSON object.
         *
         * Example:
         *
         * {
         *     "entity": "EMPLOYEE",
         *     "filters": {
         *         "id": 101
         *     },
         *     "fields": ["role"]
         * }
         *
         * Jackson represents this as a Map.
         */
        if (rawToolQuery instanceof Map<?, ?> map) {

            StringBuilder query =
                    new StringBuilder();

            Object entity =
                    map.get("entity");

            if (entity != null
                    && !String.valueOf(entity).isBlank()) {

                query.append(
                        String.valueOf(entity)
                );
            }

            Object filters =
                    map.get("filters");

            if (filters instanceof Map<?, ?> filterMap) {

                for (Map.Entry<?, ?> entry
                        : filterMap.entrySet()) {

                    if (!query.isEmpty()) {
                        query.append(" ");
                    }

                    query.append(
                            String.valueOf(
                                    entry.getKey()
                            )
                    );

                    query.append(" ");

                    query.append(
                            String.valueOf(
                                    entry.getValue()
                            )
                    );
                }
            }

            Object fields =
                    map.get("fields");

            if (fields instanceof List<?> fieldList
                    && !fieldList.isEmpty()) {

                if (!query.isEmpty()) {
                    query.append(" ");
                }

                boolean firstField = true;

                for (Object field : fieldList) {

                    if (field == null) {
                        continue;
                    }

                    if (!firstField) {
                        query.append(" ");
                    }

                    query.append(
                            String.valueOf(field)
                    );

                    firstField = false;
                }
            }

            String finalQuery =
                    query.toString().trim();

            if (!finalQuery.isBlank()) {

                return finalQuery;
            }
        }

        /*
         * CASE 3:
         *
         * Unexpected structure.
         *
         * Safely fall back to the original user message.
         */
        return originalMessage;
    }

    // =========================================================
    // EMPLOYEE EXTRACTION
    // =========================================================

    private Employee extractEmployee(
            ToolResult result) {

        if (result == null
                || result.getData() == null) {

            return null;
        }

        if (result.getData() instanceof Employee employee) {
            return employee;
        }

        return null;
    }

    // =========================================================
    // EMPLOYEE LIST EXTRACTION
    // =========================================================

    private List<Employee> extractEmployees(
            ToolResult result) {

        if (result == null
                || result.getData() == null) {

            return List.of();
        }

        if (!(result.getData() instanceof List<?> list)) {

            return List.of();
        }

        List<Employee> employees =
                new ArrayList<>();

        for (Object item : list) {

            if (item instanceof Employee employee) {
                employees.add(employee);
            }
        }

        return employees;
    }

    // =========================================================
    // EMPLOYEE ANSWER
    // =========================================================

    private String buildEmployeeAnswer(
            Employee employee,
            List<String> requestedFields) {

        /*
         * If requestedFields is empty/null, return the normal
         * complete employee details.
         */
        if (requestedFields == null
                || requestedFields.isEmpty()) {

            return buildEmployeeContext(
                    employee
            );
        }

        StringBuilder answer =
                new StringBuilder();

        answer.append(
                "Employee Details:\n\n"
        );

        boolean addedField =
                false;

        for (String field : requestedFields) {

            if (field == null) {
                continue;
            }

            switch (
                    field.trim().toLowerCase()
            ) {

                case "id":
                case "employeeid":

                    answer.append(
                            "ID: "
                    ).append(
                            employee.getId()
                    ).append("\n");

                    addedField = true;
                    break;

                case "name":
                case "employeename":

                    answer.append(
                            "Name: "
                    ).append(
                            employee.getName()
                    ).append("\n");

                    addedField = true;
                    break;

                case "department":

                    answer.append(
                            "Department: "
                    ).append(
                            employee.getDepartment()
                    ).append("\n");

                    addedField = true;
                    break;

                case "role":

                    answer.append(
                            "Role: "
                    ).append(
                            employee.getRole()
                    ).append("\n");

                    addedField = true;
                    break;

                case "email":

                    answer.append(
                            "Email: "
                    ).append(
                            employee.getEmail()
                    ).append("\n");

                    addedField = true;
                    break;

                case "location":

                    answer.append(
                            "Location: "
                    ).append(
                            employee.getLocation()
                    ).append("\n");

                    addedField = true;
                    break;

                default:
                    break;
            }
        }

        if (!addedField) {

            return buildEmployeeContext(
                    employee
            );
        }

        return answer.toString().trim();
    }

    // =========================================================
    // EMPLOYEE CONTEXT
    // =========================================================

    private String buildEmployeeContext(
            Employee employee) {

        return """
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

    // =========================================================
    // EMPLOYEE LIST CONTEXT
    // =========================================================

    private String buildEmployeesContext(
            List<Employee> employees) {

        if (employees == null
                || employees.isEmpty()) {

            return "No employee information was found.";
        }

        StringBuilder context =
                new StringBuilder();

        for (Employee employee : employees) {

            context.append(
                    "ID: "
            ).append(
                    employee.getId()
            ).append("\n");

            context.append(
                    "Name: "
            ).append(
                    employee.getName()
            ).append("\n");

            context.append(
                    "Department: "
            ).append(
                    employee.getDepartment()
            ).append("\n");

            context.append(
                    "Role: "
            ).append(
                    employee.getRole()
            ).append("\n");

            context.append(
                    "Email: "
            ).append(
                    employee.getEmail()
            ).append("\n");

            context.append(
                    "Location: "
            ).append(
                    employee.getLocation()
            ).append("\n\n");
        }

        return context.toString().trim();
    }

    // =========================================================
    // EMPLOYEE LIST FORMAT
    // =========================================================

    private String formatEmployees(
            List<Employee> employees) {

        if (employees == null
                || employees.isEmpty()) {

            return "No employees were found.";
        }

        StringBuilder answer =
                new StringBuilder(
                        "Employees:\n\n"
                );

        for (Employee employee : employees) {

            answer.append(
                    "ID: "
            ).append(
                    employee.getId()
            ).append("\n");

            answer.append(
                    "Name: "
            ).append(
                    employee.getName()
            ).append("\n");

            answer.append(
                    "Department: "
            ).append(
                    employee.getDepartment()
            ).append("\n");

            answer.append(
                    "Role: "
            ).append(
                    employee.getRole()
            ).append("\n");

            answer.append(
                    "Email: "
            ).append(
                    employee.getEmail()
            ).append("\n");

            answer.append(
                    "Location: "
            ).append(
                    employee.getLocation()
            ).append("\n");

            answer.append(
                    "-------------------------\n"
            );
        }

        return answer.toString().trim();
    }

    // =========================================================
    // RAG SOURCES
    // =========================================================

    private List<String> extractSources(
            Object data) {

        if (data == null) {
            return List.of();
        }

        if (data instanceof List<?> list) {

            List<String> sources =
                    new ArrayList<>();

            for (Object item : list) {

                if (item != null) {

                    sources.add(
                            String.valueOf(item)
                    );
                }
            }

            return sources;
        }

        if (data instanceof String value) {

            if (value.isBlank()) {
                return List.of();
            }

            return List.of(value);
        }

        return List.of();
    }

    // =========================================================
    // WEB SEARCH RESULTS
    // =========================================================

    private List<WebSearchResult> extractWebResults(
            Object data) {

        if (data == null) {
            return List.of();
        }

        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        List<WebSearchResult> results =
                new ArrayList<>();

        for (Object item : list) {

            if (item instanceof WebSearchResult result) {

                results.add(result);
            }
        }

        return results;
    }
}