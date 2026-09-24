package com.abhinav.agentic_ai_chatbot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public SimpleVectorStore vectorStore(
            @Qualifier("googleGenAiTextEmbedding")
            EmbeddingModel embeddingModel) {

        return SimpleVectorStore.builder(embeddingModel).build();
    }
}