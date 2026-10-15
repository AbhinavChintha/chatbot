
package com.abhinav.agentic_ai_chatbot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class AiConfig {

    @Bean(name = "vectorStoreJdbcTemplate")
    public JdbcTemplate vectorStoreJdbcTemplate(
            @Value("${app.vectorstore.postgres.url}") String url,
            @Value("${app.vectorstore.postgres.username}") String username,
            @Value("${app.vectorstore.postgres.password}") String password) {

        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();

        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(
            @Qualifier("googleGenAiTextEmbedding")
            EmbeddingModel geminiEmbeddingModel) {
        return geminiEmbeddingModel;
    }
}
