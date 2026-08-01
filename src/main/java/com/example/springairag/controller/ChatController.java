package com.example.springairag.controller;
import com.example.springairag.dto.ChatRequest;
import com.example.springairag.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")   // 🔥 MUST MATCH URL
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        System.out.println("🔥 Controller HIT");
        return service.ask(request.getQuery());
    }
}
