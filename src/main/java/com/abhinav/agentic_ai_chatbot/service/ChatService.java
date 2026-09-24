package com.abhinav.agentic_ai_chatbot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String chat(String message) {

        String prompt = """
                Answer the user's question clearly and completely.

                Rules:
                1. Be concise, but do not cut the answer off.
                2. Prefer a complete short explanation over a long explanation.
                3. If the question is complex, explain the important points in a compact way.
                4. Avoid unnecessary introductions, repetition, and conclusions.
                5. Do not continue with unfinished headings or incomplete sentences.

                USER QUESTION:
                %s
                """.formatted(message);

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}