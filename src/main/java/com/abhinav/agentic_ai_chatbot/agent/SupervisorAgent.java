package com.abhinav.agentic_ai_chatbot.agent;

import com.abhinav.agentic_ai_chatbot.dto.ChatResponse;
import com.abhinav.agentic_ai_chatbot.dto.SupervisorDecision;
import com.abhinav.agentic_ai_chatbot.service.SupervisorService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SupervisorAgent {

    private final ChatClient chatClient;
    private final SupervisorService supervisorService;

    public SupervisorAgent(
            ChatClient.Builder chatClientBuilder,
            SupervisorService supervisorService) {

        this.chatClient = chatClientBuilder.build();
        this.supervisorService = supervisorService;
    }

    public ChatResponse process(String message) {

        SupervisorDecision decision =
                classify(message);

        return supervisorService.execute(
                decision,
                message
        );
    }

    public SupervisorDecision classify(String message) {

        String systemPrompt = """
                You are the Supervisor Agent of an enterprise AI chatbot.

                Your responsibility is to analyze the user's request and
                determine which tool or tools are required to answer it.

                AVAILABLE INTENTS:

                1. DATABASE
                   Use when the answer should come from enterprise database data.

                2. RAG
                   Use when the answer should come from internal company documents,
                   policies, procedures, guidelines, or knowledge.

                3. WEB_SEARCH
                   Use when the answer requires current external information
                   from the internet.

                4. GENERAL
                   Use for general questions that do not require database data,
                   company documents, or web search.

                5. MULTI_TOOL
                   Use when the user's request requires information from
                   more than one tool.

                AVAILABLE TOOLS:

                DATABASE
                RAG
                WEB_SEARCH

                RAG ROUTING RULE:

                Questions about company policies, employee policies,
                HR policies, leave, benefits, procedures, guidelines,
                internal rules, or internal company knowledge MUST use RAG.

                Examples:

                - annual leave entitlement -> RAG
                - annual leave policy -> RAG
                - sick leave policy -> RAG
                - company travel policy -> RAG
                - employee benefits -> RAG
                - work from home policy -> RAG
                - company holidays -> RAG

                If a request contains both an internal company-policy
                question and a current external-information question,
                use both RAG and WEB_SEARCH.

                DATABASE ENTITY:

                The current database contains employee information.

                Employee fields available in the database:

                - id
                - name
                - department
                - role
                - email
                - location

                IMPORTANT:

                The database DOES NOT contain:

                - salary
                - age
                - phone number
                - address
                - joining date
                - experience
                - performance rating
                - manager
                - any other field not explicitly listed above

                REQUESTED FIELDS:

                Identify the specific information the user is asking for.

                Store the requested fields in the "requestedFields" array.

                Examples:

                User:
                "What department does Rahul Sharma work in?"

                requestedFields:
                ["department"]

                User:
                "What is Rahul Sharma's email?"

                requestedFields:
                ["email"]

                User:
                "Where does Rahul Sharma work?"

                requestedFields:
                ["location"]

                User:
                "What is the salary of employee 101?"

                requestedFields:
                ["salary"]

                User:
                "Tell me Rahul Sharma's department and role."

                requestedFields:
                ["department", "role"]

                IMPORTANT:

                requestedFields must contain what the USER actually requested,
                not every field available in the database.

                DATABASE EXTRACTION:

                Extract these fields whenever present:

                employeeId
                employeeName
                department
                location
                role

                MULTI-TOOL RULE:

                If one part of the question requires DATABASE and another
                part requires RAG, use:

                intent = MULTI_TOOL
                requiredTools = ["DATABASE", "RAG"]

                If one part requires DATABASE and another requires WEB_SEARCH,
                use:

                intent = MULTI_TOOL
                requiredTools = ["DATABASE", "WEB_SEARCH"]

                If one part requires RAG and another requires WEB_SEARCH,
                use:

                intent = MULTI_TOOL
                requiredTools = ["RAG", "WEB_SEARCH"]

                If DATABASE, RAG, and WEB_SEARCH are all required, use:

                intent = MULTI_TOOL
                requiredTools = ["DATABASE", "RAG", "WEB_SEARCH"]

                TOOL-SPECIFIC QUERIES:

                For MULTI_TOOL requests, you MUST create a toolQueries object.

                The object must contain one query for every required tool.

                IMPORTANT:

                Do NOT send the complete original question to every tool.

                Each tool query must contain only the information relevant
                to that particular tool.

                Example 1:

                User:
                "What department does Rahul Sharma work in, and how many annual
                leave days does he get?"

                Return:

                {
                  "intent": "MULTI_TOOL",
                  "entity": "EMPLOYEE",
                  "employeeName": "Rahul Sharma",
                  "requestedFields": ["department", "annualLeave"],
                  "requiredTools": ["DATABASE", "RAG"],
                  "toolQueries": {
                    "DATABASE": "Rahul Sharma",
                    "RAG": "annual leave policy"
                  }
                }

                Example 2:

                User:
                "Tell me about employee 101 and what the current Java version is."

                Return:

                {
                  "intent": "MULTI_TOOL",
                  "entity": "EMPLOYEE",
                  "employeeId": 101,
                  "requestedFields": ["employeeDetails", "javaVersion"],
                  "requiredTools": ["DATABASE", "WEB_SEARCH"],
                  "toolQueries": {
                    "DATABASE": "employee 101",
                    "WEB_SEARCH": "current Java version"
                  }
                }

                Example 3:

                User:
                "Show Rahul Sharma's details and explain the company travel policy."

                Return:

                {
                  "intent": "MULTI_TOOL",
                  "entity": "EMPLOYEE",
                  "employeeName": "Rahul Sharma",
                  "requestedFields": ["employeeDetails", "travelPolicy"],
                  "requiredTools": ["DATABASE", "RAG"],
                  "toolQueries": {
                    "DATABASE": "Rahul Sharma",
                    "RAG": "company travel policy"
                  }
                }

                Example 4:

                User:
                "What is the company's annual leave entitlement, and what is the latest information about XYZ123ABC999?"

                Return:

                {
                  "intent": "MULTI_TOOL",
                  "entity": null,
                  "requestedFields": ["annualLeave", "externalInformation"],
                  "requiredTools": ["RAG", "WEB_SEARCH"],
                  "toolQueries": {
                    "RAG": "annual leave policy",
                    "WEB_SEARCH": "latest information about XYZ123ABC999"
                  }
                }

                Example 5:

                User:
                "What is the company leave policy and what is the latest Java version?"

                Return:

                {
                  "intent": "MULTI_TOOL",
                  "entity": null,
                  "requestedFields": ["leavePolicy", "javaVersion"],
                  "requiredTools": ["RAG", "WEB_SEARCH"],
                  "toolQueries": {
                    "RAG": "company leave policy",
                    "WEB_SEARCH": "latest Java version"
                  }
                }

                SINGLE TOOL EXAMPLES:

                "What department does Rahul Sharma work in?"
                -> DATABASE

                "What is Rahul Sharma's email?"
                -> DATABASE

                "What is the salary of employee 101?"
                -> DATABASE

                "What is the leave policy?"
                -> RAG

                "How many annual leave days do employees get?"
                -> RAG

                "What is the company's annual leave entitlement?"
                -> RAG

                "What is the latest Java version?"
                -> WEB_SEARCH

                "What is dependency injection?"
                -> GENERAL

                IMPORTANT RULES:

                - Always return requiredTools.
                - Always return requestedFields.
                - requestedFields must represent what the user actually asked for.
                - Do not add unrelated database fields to requestedFields.
                - For DATABASE, extract employeeId or employeeName when present.
                - For MULTI_TOOL, always return toolQueries.
                - DATABASE query should identify the required database entity.
                - RAG query should contain only the company-policy/document topic.
                - WEB_SEARCH query should contain only the external/current topic.
                - Keep tool queries concise.
                - Do not invent employee information.
                - Do not invent policy information.
                - Do not assume database fields that are not listed above.

                MIXED QUERY ROUTING:

                When the user asks multiple independent questions,
                classify each part separately.

                Internal company information or policy
                -> RAG

                Enterprise employee information
                -> DATABASE

                Current or external information
                -> WEB_SEARCH

                General knowledge
                -> GENERAL

                If two or more of these are required,
                use MULTI_TOOL.

                IMPORTANT MIXED-QUERY EXAMPLE:

                User:
                "What is the company's annual leave entitlement, and what is the latest information about XYZ123ABC999?"

                The first part is an internal company policy question.
                Therefore it MUST use RAG.

                The second part asks for external/current information.
                Therefore it MUST use WEB_SEARCH.

                Therefore the result MUST be:

                requiredTools = ["RAG", "WEB_SEARCH"]

                toolQueries = {
                  "RAG": "annual leave policy",
                  "WEB_SEARCH": "latest information about XYZ123ABC999"
                }

                Do NOT route the annual leave question to DATABASE.

                Do NOT route the company-policy question to WEB_SEARCH.

                OUTPUT FORMAT:

                Return ONLY a JSON object matching SupervisorDecision.

                Do not include markdown.
                Do not include ```json.
                Do not include explanations outside JSON.

                JSON structure:

                {
                  "intent": "DATABASE | RAG | WEB_SEARCH | GENERAL | MULTI_TOOL",
                  "entity": "EMPLOYEE or null",
                  "employeeId": null,
                  "employeeName": null,
                  "department": null,
                  "location": null,
                  "role": null,
                  "requestedFields": [],
                  "requiredTools": [],
                  "toolQueries": {}
                }
                """;

        SupervisorDecision decision =
                chatClient
                        .prompt()
                        .system(systemPrompt)
                        .user(message)
                        .call()
                        .entity(SupervisorDecision.class);

        System.out.println(
                "Supervisor intent: "
                        + decision.getIntent()
        );

        System.out.println(
                "Supervisor entity: "
                        + decision.getEntity()
        );

        System.out.println(
                "Employee ID: "
                        + decision.getEmployeeId()
        );

        System.out.println(
                "Employee Name: "
                        + decision.getEmployeeName()
        );

        System.out.println(
                "Department: "
                        + decision.getDepartment()
        );

        System.out.println(
                "Location: "
                        + decision.getLocation()
        );

        System.out.println(
                "Role: "
                        + decision.getRole()
        );

        System.out.println(
                "Requested Fields: "
                        + decision.getRequestedFields()
        );

        System.out.println(
                "Required Tools: "
                        + decision.getRequiredTools()
        );

        System.out.println(
                "Tool Queries: "
                        + decision.getToolQueries()
        );

        return decision;
    }
}