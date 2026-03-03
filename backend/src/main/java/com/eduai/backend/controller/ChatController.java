package com.eduai.backend.controller;

import com.eduai.backend.model.ChatRequest;
import com.eduai.backend.model.ChatResponse;
import com.eduai.backend.service.RecommendationChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final RecommendationChatService recommendationChatService;

    public ChatController(RecommendationChatService recommendationChatService) {
        this.recommendationChatService = recommendationChatService;
    }

    @PostMapping("/recommend-chat")
    public ChatResponse recommendChat(@RequestBody ChatRequest request) {
        String reply = recommendationChatService.generateReply(request);
        return new ChatResponse(reply);
    }
}
