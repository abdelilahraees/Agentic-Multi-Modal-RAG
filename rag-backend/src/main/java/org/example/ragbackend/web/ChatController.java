package org.example.ragbackend.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.ragbackend.agent.ChatService;
import org.example.ragbackend.agent.ChatService.ChatReply;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** Question texte simple. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.conversationId(), request.userIdOrDefault(), request.message());
    }

    /** Question accompagnée d'une image (multimodal). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatReply chatWithImage(
            @RequestParam @NotBlank String conversationId,
            @RequestParam(defaultValue = ChatRequest.DEFAULT_USER) String userId,
            @RequestParam @NotBlank String message,
            @RequestParam(required = false) MultipartFile image) throws IOException {

        if (image == null || image.isEmpty()) {
            return chatService.chat(conversationId, userId, message);
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("La pièce jointe doit être une image");
        }
        return chatService.chat(conversationId, userId, message, image.getBytes(), contentType);
    }

    /** Oublie l'historique d'une conversation. */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> reset(@PathVariable String conversationId) {
        chatService.reset(conversationId);
        return ResponseEntity.noContent().build();
    }

    public record ChatRequest(
            @NotBlank @Size(max = 128) String conversationId,
            @Size(max = 64) String userId,
            @NotBlank @Size(max = 8000) String message) {

        static final String DEFAULT_USER = "demo";

        String userIdOrDefault() {
            return userId == null || userId.isBlank() ? DEFAULT_USER : userId;
        }
    }
}
