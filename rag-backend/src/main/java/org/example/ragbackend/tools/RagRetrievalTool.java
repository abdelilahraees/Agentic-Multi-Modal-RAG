package org.example.ragbackend.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Tool RAG explicite. L'agent l'invoque quand il estime qu'une recherche
 * documentaire ciblée est nécessaire (ex : "cite le paragraphe exact de la
 * politique de sécurité").
 *
 * Complémentaire du ContentRetriever automatique câblé dans AgentFactory :
 * ici l'agent choisit sciemment la requête et la limite.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetrievalTool {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Tool("""
            Recherche dans la base documentaire vectorielle les passages les plus
            pertinents pour une requête donnée. Utilise ce tool pour retrouver des
            citations, faits ou définitions issus des documents ingérés.
            """)
    public String searchDocuments(
            @P("Question ou concept à rechercher, formulé en langage naturel") String query,
            @P("Nombre maximum de passages à retourner (recommandé : 3 à 5)") int maxResults) {

        log.info("RAG tool called: query='{}', maxResults={}", query, maxResults);

        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.5)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        if (result.matches().isEmpty()) {
            return "Aucun passage pertinent trouvé dans la base documentaire.";
        }

        return result.matches().stream()
                .map(this::formatMatch)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String formatMatch(EmbeddingMatch<TextSegment> match) {
        String source = match.embedded().metadata().getString("source");
        return "[Source: %s | score: %.3f]%n%s".formatted(
                source != null ? source : "inconnu",
                match.score(),
                match.embedded().text());
    }
}
