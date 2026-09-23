package org.example.ragbackend.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.example.ragbackend.agent.Assistant;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // TODO: restreindre à l'URL du front en prod
@RequiredArgsConstructor
public class ChatController {

    private final Assistant assistant;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String reply = assistant.chat(request.conversationId(), request.message());
        return new ChatResponse(request.conversationId(), reply);
    }

    public record ChatRequest(@NotBlank String conversationId, @NotBlank String message) {}
    public record ChatResponse(String conversationId, String reply) {}
}
