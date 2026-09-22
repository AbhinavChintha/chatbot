package com.abhinav.agentic_ai_chatbot.controller;

import com.abhinav.agentic_ai_chatbot.agent.SupervisorAgent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final SupervisorAgent supervisorAgent;

    public ChatController(SupervisorAgent supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    @PostMapping
    public String chat(@RequestParam String message) {
        return supervisorAgent.process(message);
    }
}