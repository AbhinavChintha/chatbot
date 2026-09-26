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
        System.out.println("WEB SEARCH QUERY: " + query);
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

            boolean relevant =
                    hasRelevantResult(
                            query,
                            results
                    );

            if (!relevant) {

                System.out.println(
                        "Web search completed, but no sufficiently "
                                + "relevant result was found for query: "
                                + query
                );

                return new ToolResult(
                        "WEB_SEARCH",
                        false,
                        "No sufficiently relevant web search results "
                                + "were found for: "
                                + query,
                        new ArrayList<>()
                );
            }

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

            if (answer == null
                    || answer.trim().isEmpty()) {

                return new ToolResult(
                        "WEB_SEARCH",
                        false,
                        "Web search could not generate an answer.",
                        new ArrayList<>()
                );
            }

            return new ToolResult(
                    "WEB_SEARCH",
                    true,
                    answer.trim(),
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
                normalize(query);

        String[] queryTokens =
                normalizedQuery.split("\\s+");

        /*
         * Detect identifier-like queries.
         *
         * Examples:
         *
         * XYZ123ABC999
         * GPT-5
         * ISO9001
         * ABC123
         *
         * These require stronger validation because a search engine can
         * return unrelated pages that merely contain the same character
         * sequence.
         */
        List<String> identifierTokens =
                Arrays.stream(queryTokens)
                        .filter(token ->
                                token.length() >= 5
                                        && token.matches(".*\\d.*")
                        )
                        .toList();

        if (!identifierTokens.isEmpty()) {

            return hasMeaningfulIdentifierEvidence(
                    identifierTokens,
                    results
            );
        }

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
                                token.length() >= 2
                                        && !stopWords.contains(token)
                        )
                        .toList();

        if (meaningfulTokens.isEmpty()) {
            return true;
        }

        /*
         * Multi-word subject.
         *
         * Example:
         *
         * Spring AI
         *
         * First try the complete phrase.
         * If that is not found, require all meaningful tokens
         * to appear in the SAME result.
         */
        if (meaningfulTokens.size() >= 2) {

            String meaningfulPhrase =
                    String.join(
                            " ",
                            meaningfulTokens
                    );

            boolean exactPhraseFound =
                    results.stream()
                            .anyMatch(result ->
                                    containsIgnoreCase(
                                            result.getTitle(),
                                            meaningfulPhrase
                                    )
                                            || containsIgnoreCase(
                                            result.getUrl(),
                                            meaningfulPhrase
                                    )
                                            || containsIgnoreCase(
                                            result.getSnippet(),
                                            meaningfulPhrase
                                    )
                            );

            if (exactPhraseFound) {
                return true;
            }

            for (WebSearchResult result : results) {

                String searchableText =
                        normalize(
                                safeValue(result.getTitle())
                                        + " "
                                        + safeValue(result.getUrl())
                                        + " "
                                        + safeValue(result.getSnippet())
                        );

                boolean allTokensFound =
                        meaningfulTokens.stream()
                                .allMatch(
                                        searchableText::contains
                                );

                if (allTokensFound) {
                    return true;
                }
            }

            return false;
        }

        /*
         * Single meaningful word.
         */
        String singleToken =
                meaningfulTokens.get(0);

        return results.stream()
                .anyMatch(result ->
                        containsIgnoreCase(
                                result.getTitle(),
                                singleToken
                        )
                                || containsIgnoreCase(
                                result.getUrl(),
                                singleToken
                        )
                                || containsIgnoreCase(
                                result.getSnippet(),
                                singleToken
                        )
                );
    }

    /**
     * Performs stronger validation for identifier-like queries.
     *
     * Merely finding the identifier in a page is not sufficient.
     *
     * Example:
     *
     * Query:
     * XYZ123ABC999
     *
     * Result:
     * "xyz123abc999 xyz123abc999"
     *
     * This is only a textual occurrence and does not establish
     * what the identifier represents.
     *
     * Therefore, the result must also contain contextual language
     * indicating that the identifier is being defined, identified,
     * described, referenced as an entity, or otherwise explained.
     */
    private boolean hasMeaningfulIdentifierEvidence(
            List<String> identifierTokens,
            List<WebSearchResult> results) {

        List<String> contextualTerms = List.of(
                "is",
                "means",
                "refers",
                "reference",
                "identifier",
                "code",
                "product",
                "model",
                "version",
                "project",
                "organization",
                "company",
                "software",
                "technology",
                "device",
                "event",
                "case",
                "account",
                "number",
                "serial",
                "known",
                "called",
                "named",
                "defined",
                "description",
                "described",
                "represents",
                "stands for",
                "associated with"
        );

        for (WebSearchResult result : results) {

            String searchableText =
                    normalize(
                            safeValue(result.getTitle())
                                    + " "
                                    + safeValue(result.getUrl())
                                    + " "
                                    + safeValue(result.getSnippet())
                    );

            /*
             * First verify that the identifier actually occurs.
             */
            boolean identifierFound =
                    identifierTokens.stream()
                            .allMatch(
                                    searchableText::contains
                            );

            if (!identifierFound) {
                continue;
            }

            /*
             * Then look for meaningful contextual evidence.
             */
            boolean contextualEvidenceFound =
                    contextualTerms.stream()
                            .anyMatch(
                                    searchableText::contains
                            );

            if (contextualEvidenceFound) {

                System.out.println(
                        "Web identifier relevance accepted. "
                                + "Identifier and contextual evidence found in: "
                                + result.getTitle()
                );

                return true;
            }
        }

        System.out.println(
                "Web identifier relevance rejected. "
                        + "Identifier was found, but meaningful contextual "
                        + "evidence was not found."
        );

        return false;
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value
                .toLowerCase()
                .replaceAll("[^a-z0-9_-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safeValue(String value) {

        return value == null
                ? ""
                : value;
    }

    private boolean containsIgnoreCase(
            String value,
            String searchTerm) {

        if (value == null
                || searchTerm == null) {

            return false;
        }

        return normalize(value)
                .contains(
                        normalize(searchTerm)
                );
    }
}