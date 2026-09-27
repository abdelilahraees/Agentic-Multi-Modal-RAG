package org.example.ragbackend.agent;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ragbackend.rag.ImageDescriptionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Façade entre la couche web et l'agent : gère l'identité de l'utilisateur,
 * les images jointes (décrites par le modèle de vision puis injectées dans
 * la question) et la mise en forme des sources / tools utilisés.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    static final String CHAT_IMAGE_PROMPT = """
            L'utilisateur joint cette image à sa question. Décris précisément tout ce qui
            est utile pour y répondre : contenu, texte visible (retranscris-le), chiffres,
            montants, dates, tableaux ou graphiques. Réponds en texte brut, sans préambule.
            """;
    private static final int SNIPPET_LENGTH = 280;

    private final Assistant assistant;
    private final ImageDescriptionService imageDescriptionService;
    private final ChatMemoryStore chatMemoryStore;

    public ChatReply chat(String conversationId, String userId, String message) {
        return chat(conversationId, userId, message, null, null);
    }

    public ChatReply chat(String conversationId, String userId, String message,
                          byte[] image, String imageMimeType) {

        String prompt = message;
        String imageDescription = null;
        if (image != null && image.length > 0) {
            imageDescription = imageDescriptionService.describe(image, imageMimeType, CHAT_IMAGE_PROMPT);
            prompt = """
                    %s

                    [Image jointe par l'utilisateur — description automatique]
                    %s
                    """.formatted(message, imageDescription);
        }

        String finalPrompt = prompt;
        Result<String> result = CurrentUser.runAs(userId, () -> assistant.chat(conversationId, finalPrompt));

        List<SourceRef> sources = result.sources() == null ? List.of()
                : result.sources().stream().map(ChatService::toSourceRef).toList();
        List<String> tools = result.toolExecutions() == null ? List.of()
                : result.toolExecutions().stream().map(t -> t.request().name()).distinct().toList();

        return new ChatReply(conversationId, result.content(), sources, tools, imageDescription);
    }

    public void reset(String conversationId) {
        chatMemoryStore.deleteMessages(conversationId);
    }

    private static SourceRef toSourceRef(Content content) {
        var segment = content.textSegment();
        String source = segment.metadata().getString("source");
        Object score = content.metadata() == null ? null : content.metadata().get(ContentMetadata.SCORE);
        String text = segment.text();
        String snippet = text.length() > SNIPPET_LENGTH ? text.substring(0, SNIPPET_LENGTH) + "…" : text;
        return new SourceRef(
                source != null ? source : "inconnu",
                snippet,
                score instanceof Number n ? n.doubleValue() : null);
    }

    public record ChatReply(String conversationId, String reply, List<SourceRef> sources,
                            List<String> toolsUsed, String imageDescription) {}

    public record SourceRef(String source, String snippet, Double score) {}
}
