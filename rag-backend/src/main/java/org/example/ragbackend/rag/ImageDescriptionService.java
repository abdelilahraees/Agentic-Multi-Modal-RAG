package org.example.ragbackend.rag;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Transforme une image en description textuelle grâce au modèle de vision (GPT-4o).
 *
 * Utilisé à deux endroits :
 *   - à l'ingestion : la description est découpée/embeddée comme n'importe quel texte,
 *     ce qui rend l'image retrouvable par recherche sémantique ;
 *   - dans le chat : une image jointe à une question est décrite puis fournie
 *     comme contexte à l'agent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDescriptionService {

    static final String INDEXING_PROMPT = """
            Décris cette image de façon exhaustive et factuelle pour qu'elle puisse être
            retrouvée par une recherche textuelle : sujet principal, objets, personnes,
            texte visible (retranscris-le intégralement), chiffres, tableaux, graphiques
            (axes, tendances, valeurs clés), schémas et leur signification.
            Réponds en français, en texte brut, sans préambule.
            """;

    private final ChatModel chatModel;

    public String describe(byte[] image, String mimeType) {
        return describe(image, mimeType, INDEXING_PROMPT);
    }

    public String describe(byte[] image, String mimeType, String instruction) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Image vide");
        }
        String base64 = Base64.getEncoder().encodeToString(image);
        UserMessage message = UserMessage.from(
                TextContent.from(instruction),
                ImageContent.from(base64, mimeType, ImageContent.DetailLevel.HIGH));

        log.info("Describing image ({} bytes, {}) with vision model", image.length, mimeType);
        String description = chatModel.chat(
                SystemMessage.from("Tu es un expert en analyse d'images et de documents visuels."),
                message).aiMessage().text();

        if (description == null || description.isBlank()) {
            throw new IllegalStateException("Le modèle de vision n'a renvoyé aucune description");
        }
        return description.trim();
    }
}
