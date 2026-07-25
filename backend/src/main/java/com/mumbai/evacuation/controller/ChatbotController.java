package com.mumbai.evacuation.controller;

import com.mumbai.evacuation.dto.ChatRequest;
import com.mumbai.evacuation.dto.ChatResponse;
import com.mumbai.evacuation.service.EmergencyChatbotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Emergency AI Safety Chatbot.
 *
 * Endpoint: POST /api/chat
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatbotController {

    @Autowired
    private EmergencyChatbotService chatbotService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = chatbotService.processChatQuery(request);
        return ResponseEntity.ok(response);
    }
}
