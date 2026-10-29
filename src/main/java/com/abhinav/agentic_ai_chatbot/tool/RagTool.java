package com.abhinav.agentic_ai_chatbot.tool;

import com.abhinav.agentic_ai_chatbot.dto.ToolResult;
import com.abhinav.agentic_ai_chatbot.service.AnswerPrompt;
import com.abhinav.agentic_ai_chatbot.service.GenerationPolicy;
import com.abhinav.agentic_ai_chatbot.service.PolicyRetriever;
import com.abhinav.agentic_ai_chatbot.service.ChatLatencyMetrics;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RagTool implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final GenerationPolicy generationPolicy;
    private final PolicyRetriever retriever;
    private final ChatLatencyMetrics latencyMetrics;
    private final JdbcTemplate vectorStoreJdbcTemplate;


    public RagTool(
            VectorStore vectorStore,
            @Qualifier("googleGenAiTextEmbedding")
            EmbeddingModel embeddingModel,
            ChatClient.Builder chatClientBuilder,
            GenerationPolicy generationPolicy,
            PolicyRetriever retriever,
            ChatLatencyMetrics latencyMetrics,
            @Qualifier("vectorStoreJdbcTemplate")
            JdbcTemplate vectorStoreJdbcTemplate) {

        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClientBuilder.build();
        this.generationPolicy = generationPolicy;
        this.retriever = retriever;
        this.latencyMetrics = latencyMetrics;
        this.vectorStoreJdbcTemplate = vectorStoreJdbcTemplate;
    }


    public void loadDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();

            Resource[] resources =
                    resolver.getResources("classpath:/documents/*.txt");

            if (resources.length == 0) {
                throw new RuntimeException(
                        "No RAG documents found in classpath:/documents/");
            }

            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(300)
                    .withMinChunkSizeChars(100)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();

            Set<String> currentSources = new LinkedHashSet<>();

            for (Resource resource : resources) {
                String source = resource.getFilename();

                if (source == null) {
                    continue;
                }

                currentSources.add(source);

                String content = new String(
                        resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);

                String contentHash = sha256(content);

                List<String> existingHashes = vectorStoreJdbcTemplate.query(
                        "SELECT content_hash FROM public.rag_document_registry "
                                + "WHERE source_name = ?",
                        (rs, rowNum) -> rs.getString("content_hash"),
                        source);

                if (!existingHashes.isEmpty()
                        && contentHash.equals(existingHashes.get(0))) {
                    System.out.println(
                            "Skipping unchanged RAG document: " + source);
                    continue;
                }

                System.out.println(
                        "Indexing new or changed RAG document: " + source);

                // Remove old vectors for this source before re-indexing.
                vectorStoreJdbcTemplate.update(
                        "DELETE FROM public.vector_store "
                                + "WHERE metadata->>'source' = ?",
                        source);

                Document document = new Document(content);
                document.getMetadata().put("source", source);

                List<Document> chunks = splitter.apply(List.of(document));

                for (Document chunk : chunks) {
                    chunk.getMetadata().put("source", source);
                }

                if (!chunks.isEmpty()) {
                    vectorStore.add(chunks);
                }

                // Record the hash only after indexing completes.
                vectorStoreJdbcTemplate.update(
                        """
                        INSERT INTO public.rag_document_registry
                            (source_name, content_hash, indexed_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (source_name)
                        DO UPDATE SET
                            content_hash = EXCLUDED.content_hash,
                            indexed_at = CURRENT_TIMESTAMP
                        """,
                        source,
                        contentHash);

                System.out.println(
                        "Indexed " + chunks.size() + " chunks for " + source);
            }

            // Remove vectors for policy files that no longer exist.
            List<String> registeredSources = vectorStoreJdbcTemplate.query(
                    "SELECT source_name FROM public.rag_document_registry",
                    (rs, rowNum) -> rs.getString("source_name"));

            for (String registeredSource : registeredSources) {
                if (!currentSources.contains(registeredSource)) {
                    vectorStoreJdbcTemplate.update(
                            "DELETE FROM public.vector_store "
                                    + "WHERE metadata->>'source' = ?",
                            registeredSource);

                    vectorStoreJdbcTemplate.update(
                            "DELETE FROM public.rag_document_registry "
                                    + "WHERE source_name = ?",
                            registeredSource);

                    System.out.println(
                            "Removed deleted RAG document: " + registeredSource);
                }
            }

            System.out.println("RAG vector store initialization completed.");

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load RAG documents", e);
        }
    }

    private String sha256(String content) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(hash);
    }


    public ToolResult search(String query) {

        try {

            System.out.println(
                    "RAG search query: "
                            + query
            );

            List<Document> results =
                    retriever.retrieve(query);

            /*
             * No relevant company documents found.
             */
            if (results.isEmpty()) {

                return new ToolResult(
                        "RAG",
                        false,
                        "The company documents do not provide enough information to answer that question.",
                        new ArrayList<>()
                );
            }

            StringBuilder context =
                    new StringBuilder();

            Set<String> sources =
                    new LinkedHashSet<>();

            for (Document document : results) {

                Object sourceMetadata =
                        document.getMetadata()
                                .get("source");

                System.out.println(
                        "RAG retrieved document: "
                                + sourceMetadata
                );

                System.out.println(
                        "RAG document metadata: "
                                + document.getMetadata()
                );

                context
                        .append(document.getText())
                        .append("\n\n");

                if (sourceMetadata != null) {

                    sources.add(
                            sourceMetadata.toString()
                    );
                }
            }

            AnswerPrompt prompt = AnswerPrompt.build(
                    AnswerPrompt.Task.POLICY, query, context.toString());

            String answer =
                    latencyMetrics.measure("rag_generation", () -> chatClient
                            .prompt()
                            .system(prompt.system())
                            .user(prompt.user())
                            .options(generationPolicy.answer())
                            .call()
                            .content());

            if (answer == null || answer.trim().isEmpty()) {

                return new ToolResult(
                        "RAG",
                        false,
                        "RAG could not generate an answer.",
                        new ArrayList<>(sources)
                );
            }

            return new ToolResult(
                    "RAG",
                    true,
                    prompt.format(answer),
                    new ArrayList<>(sources)
            );

        } catch (com.abhinav.agentic_ai_chatbot.exception.AnswerFormatException e) {
            return new ToolResult("RAG", false, e.getMessage(), new ArrayList<>());
        } catch (Exception e) {

            return new ToolResult(
                    "RAG",
                    false,
                    "RAG processing failed: "
                            + e.getMessage(),
                    new ArrayList<>()
            );
        }
    }

    @Override
    public void run(String... args) {

        loadDocuments();
    }
}