package com.abhinav.agentic_ai_chatbot.tool;

import com.abhinav.agentic_ai_chatbot.dto.ToolResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RagTool implements CommandLineRunner {

    private final SimpleVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;

    public RagTool(
            SimpleVectorStore vectorStore,
            @Qualifier("googleGenAiTextEmbedding")
            EmbeddingModel embeddingModel,
            ChatClient.Builder chatClientBuilder) {

        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClientBuilder.build();
    }

    public void loadDocuments() {

        try {

            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();

            Resource[] resources =
                    resolver.getResources(
                            "classpath:/documents/*.txt"
                    );

            List<Document> documents =
                    new ArrayList<>();

            for (Resource resource : resources) {

                String content =
                        new String(
                                resource.getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8
                        );

                Document document =
                        new Document(content);

                document.getMetadata().put(
                        "source",
                        resource.getFilename()
                );

                documents.add(document);

                System.out.println(
                        "Document loaded: "
                                + resource.getFilename()
                );
            }

            if (documents.isEmpty()) {

                throw new RuntimeException(
                        "No RAG documents found in classpath:/documents/"
                );
            }

            TokenTextSplitter splitter =
                    TokenTextSplitter.builder()
                            .withChunkSize(300)
                            .withMinChunkSizeChars(100)
                            .withMinChunkLengthToEmbed(5)
                            .withMaxNumChunks(10000)
                            .withKeepSeparator(true)
                            .build();

            List<Document> chunks =
                    new ArrayList<>();

            for (Document document : documents) {

                List<Document> documentChunks =
                        splitter.apply(
                                List.of(document)
                        );

                String source =
                        document.getMetadata()
                                .get("source")
                                .toString();

                for (Document chunk : documentChunks) {

                    chunk.getMetadata().put(
                            "source",
                            source
                    );
                }

                chunks.addAll(documentChunks);
            }

            System.out.println(
                    "Total document chunks created: "
                            + chunks.size()
            );

            vectorStore.add(chunks);

            System.out.println(
                    "RAG vector store initialized successfully."
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to load RAG documents",
                    e
            );
        }
    }

    public ToolResult search(String query) {

        try {

            List<Document> results =
                    vectorStore.similaritySearch(query)
                            .stream()
                            .filter(document -> {

                                Object distance =
                                        document.getMetadata()
                                                .get("distance");

                                if (distance == null) {
                                    return true;
                                }

                                double similarityDistance =
                                        Double.parseDouble(
                                                distance.toString()
                                        );

                                return similarityDistance <= 0.35;
                            })
                            .limit(2)
                            .toList();

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

                System.out.println(
                        "RAG retrieved document: "
                                + document.getMetadata()
                                .get("source")
                );

                System.out.println(
                        "RAG document metadata: "
                                + document.getMetadata()
                );

                context
                        .append(document.getText())
                        .append("\n\n");

                Object sourceMetadata =
                        document.getMetadata()
                                .get("source");

                if (sourceMetadata != null) {

                    sources.add(
                            sourceMetadata.toString()
                    );
                }
            }

            String prompt = """
                    You are an enterprise company-policy question-answering system.

                    Your ONLY source of truth is the COMPANY DOCUMENT CONTEXT provided below.

                    STRICT RULES:

                    1. Answer the user's question using ONLY information explicitly present
                       in the company document context.

                    2. Do NOT use your general knowledge.

                    3. Do NOT invent policies, rules, limits, approvals, or procedures.

                    4. If the documents contain conflicting information,
                       clearly mention the conflict.

                    5. Do NOT tell the user to contact HR, their manager,
                       or another department unless the documents explicitly say so.

                    6. If the documents do not provide enough information, say:
                       "The company documents do not provide enough information
                       to answer that question."

                    7. Keep the answer concise but complete.

                    8. Do not leave the answer unfinished.

                    USER QUESTION:
                    %s

                    COMPANY DOCUMENT CONTEXT:
                    %s

                    Answer using ONLY the company document context.
                    """.formatted(
                    query,
                    context
            );

            String answer =
                    chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();

            return new ToolResult(
                    "RAG",
                    true,
                    answer,
                    new ArrayList<>(sources)
            );

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