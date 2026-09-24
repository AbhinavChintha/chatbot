package com.abhinav.agentic_ai_chatbot.tool;

import com.abhinav.agentic_ai_chatbot.dto.ToolResult;
import com.abhinav.agentic_ai_chatbot.dto.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class WebSearchTool {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    @Value("${SERPER_API_KEY}")
    private String serperApiKey;

    public WebSearchTool(
            ChatClient.Builder chatClientBuilder) {

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.chatClient = chatClientBuilder.build();
    }

    public ToolResult search(String query) {

        try {

            String jsonBody = """
                    {
                        "q": "%s"
                    }
                    """.formatted(
                    query.replace("\"", "\\\"")
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://google.serper.dev/search"
                                    )
                            )
                            .header(
                                    "X-API-KEY",
                                    serperApiKey
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(jsonBody)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() != 200) {

                return new ToolResult(
                        "WEB_SEARCH",
                        false,
                        "Web search failed. HTTP status: "
                                + response.statusCode(),
                        new ArrayList<>()
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            JsonNode organicResults =
                    root.path("organic");

            if (!organicResults.isArray()
                    || organicResults.isEmpty()) {

                return new ToolResult(
                        "WEB_SEARCH",
                        false,
                        "No web search results were found.",
                        new ArrayList<>()
                );
            }

            StringBuilder searchContext =
                    new StringBuilder();

            List<WebSearchResult> results =
                    new ArrayList<>();

            int resultCount = 0;

            for (JsonNode result : organicResults) {

                if (resultCount >= 5) {
                    break;
                }

                String title =
                        result.path("title").asText();

                String link =
                        result.path("link").asText();

                String snippet =
                        result.path("snippet").asText();

                results.add(
                        new WebSearchResult(
                                title,
                                link,
                                snippet
                        )
                );

                searchContext
                        .append("SOURCE ")
                        .append(resultCount + 1)
                        .append("\n");

                searchContext
                        .append("Title: ")
                        .append(title)
                        .append("\n");

                searchContext
                        .append("URL: ")
                        .append(link)
                        .append("\n");

                searchContext
                        .append("Snippet: ")
                        .append(snippet)
                        .append("\n\n");

                resultCount++;
            }

            /*
             * Check relevance BEFORE asking the LLM to generate
             * the final answer.
             *
             * This prevents unrelated search results from being
             * treated as valid information.
             */
            boolean relevant =
                    hasRelevantResult(
                            query,
                            results
                    );

            if (!relevant) {

                System.out.println(
                        "Web search completed, but no relevant result "
                                + "was found for query: "
                                + query
                );

                return new ToolResult(
                        "WEB_SEARCH",
                        false,
                        "No relevant web search results were found for: "
                                + query,
                        new ArrayList<>()
                );
            }

            /*
             * Only relevant search results reach the LLM.
             */
            String prompt = """
                    You are an enterprise web-search answer generator.

                    Answer the user's question using ONLY the supplied
                    web search results.

                    STRICT RULES:

                    1. Use only facts supported by the supplied search results.

                    2. Prefer official, primary, and authoritative sources
                       when available.

                    3. Prefer the most recent information when the question
                       asks for current information.

                    4. If sources contain different information, briefly
                       explain the difference.

                    5. Distinguish different concepts when necessary.
                       For example, latest release and latest LTS release
                       may be different.

                    6. Do not invent facts, dates, versions, measurements,
                       or explanations.

                    7. If the supplied results cannot answer the question,
                       clearly say that there is not enough information.

                    8. Keep the answer concise but complete.

                    9. Do not leave the answer unfinished.

                    10. Do not include source URLs in the answer.
                        URLs are returned separately in the API response.

                    11. Do not repeat the entire search results.

                    12. For current information such as weather, clearly
                        mention the relevant observation time or date when
                        it is available in the search results.

                    USER QUESTION:
                    %s

                    SEARCH RESULTS:
                    %s

                    Produce a concise, complete answer using ONLY
                    the supplied search results.
                    """.formatted(
                    query,
                    searchContext
            );

            String answer =
                    chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();

            return new ToolResult(
                    "WEB_SEARCH",
                    true,
                    answer,
                    results
            );

        } catch (Exception e) {

            return new ToolResult(
                    "WEB_SEARCH",
                    false,
                    "Web search failed: "
                            + e.getMessage(),
                    new ArrayList<>()
            );
        }
    }

    /**
     * Performs a lightweight relevance check.
     *
     * The check intentionally avoids asking another LLM to determine
     * whether search results are relevant.
     *
     * At least one meaningful query term must appear in the title,
     * URL, or snippet of the returned results.
     */
    private boolean hasRelevantResult(
            String query,
            List<WebSearchResult> results) {

        if (query == null
                || query.isBlank()
                || results == null
                || results.isEmpty()) {

            return false;
        }

        String normalizedQuery =
                query.toLowerCase();

        String[] queryTokens =
                normalizedQuery
                        .replaceAll("[^a-z0-9_-]", " ")
                        .split("\\s+");

        /*
         * First look for distinctive identifiers containing numbers.
         *
         * Examples:
         * XYZ123ABC999
         * ABC123
         * employee101
         *
         * If the user supplied such an identifier, it must appear
         * in at least one search result.
         */
        List<String> identifierTokens =
                Arrays.stream(queryTokens)
                        .filter(token ->
                                token.length() >= 5
                                        && token.matches(".*\\d.*")
                        )
                        .toList();

        if (!identifierTokens.isEmpty()) {

            for (String token : identifierTokens) {

                boolean found =
                        results.stream()
                                .anyMatch(result ->
                                        containsIgnoreCase(
                                                result.getTitle(),
                                                token
                                        )
                                                || containsIgnoreCase(
                                                result.getUrl(),
                                                token
                                        )
                                                || containsIgnoreCase(
                                                result.getSnippet(),
                                                token
                                        )
                                );

                if (found) {
                    return true;
                }
            }

            return false;
        }

        /*
         * Normal natural-language query.
         *
         * Ignore common generic words so that words such as
         * "information", "latest", and "details" do not make an
         * unrelated search result appear relevant.
         */
        List<String> stopWords = List.of(
                "what",
                "what's",
                "whats",
                "is",
                "are",
                "the",
                "a",
                "an",
                "of",
                "and",
                "or",
                "for",
                "to",
                "about",
                "information",
                "details",
                "latest",
                "current",
                "provide",
                "please",
                "tell",
                "me",
                "give",
                "show",
                "price",
                "stock"
        );

        List<String> meaningfulTokens =
                Arrays.stream(queryTokens)
                        .filter(token ->
                                token.length() >= 4
                                        && !stopWords.contains(token)
                        )
                        .toList();

        if (meaningfulTokens.isEmpty()) {
            return true;
        }

        for (String token : meaningfulTokens) {

            boolean found =
                    results.stream()
                            .anyMatch(result ->
                                    containsIgnoreCase(
                                            result.getTitle(),
                                            token
                                    )
                                            || containsIgnoreCase(
                                            result.getUrl(),
                                            token
                                    )
                                            || containsIgnoreCase(
                                            result.getSnippet(),
                                            token
                                    )
                            );

            if (found) {
                return true;
            }
        }

        return false;
    }

    private boolean containsIgnoreCase(
            String value,
            String searchTerm) {

        return value != null
                && searchTerm != null
                && value.toLowerCase()
                .contains(searchTerm.toLowerCase());
    }
}