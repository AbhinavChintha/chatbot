package com.abhinav.agentic_ai_chatbot.tool;

import com.abhinav.agentic_ai_chatbot.dto.ToolResult;
import com.abhinav.agentic_ai_chatbot.dto.WebSearchResult;
import com.abhinav.agentic_ai_chatbot.service.AnswerPrompt;
import com.abhinav.agentic_ai_chatbot.service.GenerationPolicy;
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
    private final GenerationPolicy generationPolicy;

    @Value("${SERPER_API_KEY}")
    private String serperApiKey;

    public WebSearchTool(
            ChatClient.Builder chatClientBuilder, GenerationPolicy generationPolicy) {

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.chatClient = chatClientBuilder.build();
        this.generationPolicy = generationPolicy;
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

                /*
                 * Preserve the optional date supplied by Serper.
                 *
                 * The value may be a relative date such as
                 * "2 days ago". If no date is supplied, leave it null.
                 * Never invent a publication date.
                 */
                String date =
                        result.path("date").asText("");

                if (date.isBlank()) {
                    date = null;
                }

                results.add(
                        new WebSearchResult(
                                title,
                                link,
                                snippet,
                                date
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
                        .append("Search result date: ")
                        .append(
                                date != null
                                        ? date
                                        : "Not provided"
                        )
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

            AnswerPrompt prompt = AnswerPrompt.build(
                    AnswerPrompt.Task.WEB, query, searchContext.toString());

            String answer =
                    chatClient
                            .prompt()
                            .system(prompt.system())
                            .user(prompt.user())
                            .options(generationPolicy.answer())
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
                    prompt.format(answer),
                    results
            );

        } catch (com.abhinav.agentic_ai_chatbot.exception.AnswerFormatException e) {
            return new ToolResult("WEB_SEARCH", false, e.getMessage(), new ArrayList<>());
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
     * Performs a lightweight relevance check without another LLM call.
     *
     * The check is intentionally tolerant of natural-language search
     * results. Search-result wording does not need to reproduce every
     * word from the user's question.
     *
     * Identifier-like queries continue to use the stronger validation
     * implemented in hasMeaningfulIdentifierEvidence().
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

        String normalizedQuery = normalize(query);
        String[] queryTokens = normalizedQuery.split("\\s+");

        /*
         * Identifier-like queries require stronger evidence.
         *
         * Examples:
         * XYZ123ABC999
         * GPT-5
         * ISO9001
         * ABC123
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

        /*
         * Generic natural-language queries.
         *
         * Words such as "latest", "current", "what", "information",
         * "version", "release", etc. describe the request rather than
         * the subject being searched for.
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
                "stock",
                "version",
                "versions",
                "feature",
                "features",
                "release",
                "releases",
                "update",
                "updates",
                "news",
                "status"
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
         * Score each result rather than requiring every query token.
         */
        for (WebSearchResult result : results) {

            String title = safeValue(result.getTitle());
            String url = safeValue(result.getUrl());
            String snippet = safeValue(result.getSnippet());

            String normalizedTitle = normalize(title);
            String normalizedUrl = normalize(url);
            String normalizedSnippet = normalize(snippet);

            int score = 0;
            int tokenMatches = 0;

            for (String token : meaningfulTokens) {

                boolean titleMatch =
                        normalizedTitle.contains(token);

                boolean urlMatch =
                        normalizedUrl.contains(token);

                boolean snippetMatch =
                        normalizedSnippet.contains(token);

                if (titleMatch) {
                    score += 2;
                    tokenMatches++;
                } else if (urlMatch || snippetMatch) {
                    score += 1;
                    tokenMatches++;
                }
            }

            /*
             * Strong signal for a multi-word subject such as
             * "spring boot" or "spring ai".
             */
            if (meaningfulTokens.size() >= 2) {

                String subjectPhrase =
                        meaningfulTokens.get(0)
                                + " "
                                + meaningfulTokens.get(1);

                if (normalizedTitle.contains(subjectPhrase)
                        || normalizedUrl.contains(subjectPhrase)
                        || normalizedSnippet.contains(subjectPhrase)) {

                    score += 2;
                }
            }

            /*
             * Accept a strong title match, multiple token matches,
             * or a strong multi-word subject phrase.
             */
            if (score >= 2
                    && (tokenMatches >= 1
                    || meaningfulTokens.size() >= 2)) {

                System.out.println(
                        "Web relevance accepted. Query: "
                                + query
                                + " | Result: "
                                + title
                                + " | Score: "
                                + score
                );

                return true;
            }
        }

        System.out.println(
                "Web relevance rejected. Query: "
                        + query
        );

        return false;
    }

    /**
     * Performs stronger validation for identifier-like queries.
     *
     * Merely finding the identifier in a page is not sufficient.
     * The result must also contain contextual language explaining
     * what the identifier represents.
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
                .replaceAll("[\\u2018\\u2019']", "")
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
