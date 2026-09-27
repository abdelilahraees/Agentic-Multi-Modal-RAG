package org.example.ragbackend.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.example.ragbackend.config.AiConfig.AiProperties;
import org.example.ragbackend.rag.RagIngestionService.DocumentKind;
import org.example.ragbackend.rag.RagIngestionService.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RagIngestionServiceTest {

    EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    @SuppressWarnings("unchecked")
    EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
    ImageDescriptionService imageDescriptionService = mock(ImageDescriptionService.class);
    RagIngestionService service;

    @BeforeEach
    void setUp() {
        service = new RagIngestionService(embeddingModel, store, imageDescriptionService, new AiProperties());
        when(embeddingModel.embedAll(anyList())).thenAnswer(inv -> {
            List<TextSegment> segments = inv.getArgument(0);
            return Response.from(segments.stream().map(s -> Embedding.from(new float[]{1f, 0f})).toList());
        });
    }

    @Test
    void detectsKinds() {
        assertThat(RagIngestionService.detectKind("a.pdf", "application/octet-stream")).isEqualTo(DocumentKind.PDF);
        assertThat(RagIngestionService.detectKind("a.png", "image/png")).isEqualTo(DocumentKind.IMAGE);
        assertThat(RagIngestionService.detectKind("notes.md", "")).isEqualTo(DocumentKind.TEXT);
        assertThat(RagIngestionService.detectKind("x", "text/plain")).isEqualTo(DocumentKind.TEXT);
        assertThatThrownBy(() -> RagIngestionService.detectKind("a.zip", "application/zip"))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestsTextWithMetadata() {
        byte[] content = "La politique de sécurité impose une rotation des mots de passe tous les 90 jours."
                .getBytes(StandardCharsets.UTF_8);

        var result = service.ingest(content, "policy.txt", "text/plain", "alice");

        ArgumentCaptor<List<TextSegment>> segments = ArgumentCaptor.forClass(List.class);
        verify(store).addAll(anyList(), segments.capture());
        TextSegment first = segments.getValue().getFirst();
        assertThat(first.text()).contains("90 jours");
        assertThat(first.metadata().getString("source")).isEqualTo("policy.txt");
        assertThat(first.metadata().getString("owner")).isEqualTo("alice");
        assertThat(first.metadata().getString("kind")).isEqualTo("text");
        assertThat(first.metadata().getString("documentId")).isEqualTo(result.documentId());
        assertThat(result.segments()).isEqualTo(segments.getValue().size());
        verifyNoInteractions(imageDescriptionService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestsImageThroughVisionDescription() {
        byte[] png = {1, 2, 3};
        when(imageDescriptionService.describe(png, "image/png"))
                .thenReturn("Graphique des ventes 2025 : hausse de 12 % au T4.");

        var result = service.ingest(png, "ventes.png", "image/png", "alice");

        ArgumentCaptor<List<TextSegment>> segments = ArgumentCaptor.forClass(List.class);
        verify(store).addAll(anyList(), segments.capture());
        assertThat(segments.getValue().getFirst().text()).contains("hausse de 12 %");
        assertThat(result.kind()).isEqualTo("IMAGE");
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> service.ingest("   ".getBytes(), "empty.txt", "text/plain", "a"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, never()).addAll(anyList(), anyList());
    }

    @Test
    void deleteRemovesByDocumentId() {
        service.delete("doc-1");
        verify(store).removeAll(any(dev.langchain4j.store.embedding.filter.Filter.class));
    }
}
