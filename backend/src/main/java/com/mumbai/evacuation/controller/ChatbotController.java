package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.ChatRequest;
import com.mumbai.evacuation.dto.ChatResponse;
import com.mumbai.evacuation.service.EmergencyChatbotService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** POST /api/chat — Emergency AI safety assistant (rate limited). */
@RestController
@RequestMapping("/api")
public class ChatbotController {

    private final EmergencyChatbotService chatbotService;

    public ChatbotController(EmergencyChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatbotService.processChatQuery(request);
    }
}
