package com.abhinav.agentic_ai_chatbot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final GenerationPolicy generationPolicy;

    public ChatService(ChatClient.Builder chatClientBuilder, GenerationPolicy generationPolicy) {
        this.chatClient = chatClientBuilder.build();
        this.generationPolicy = generationPolicy;
    }

    public String chat(String message) {

        return generate(AnswerPrompt.build(AnswerPrompt.Task.GENERAL, message, null), false);
    }

    public String synthesize(String question, String... evidenceSections) {
        return generate(AnswerPrompt.synthesis(question, evidenceSections), true);
    }

    private String generate(AnswerPrompt prompt, boolean synthesis) {
        return prompt.format(chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .options(synthesis ? generationPolicy.synthesis() : generationPolicy.answer())
                .call()
                .content());
    }
}