package org.example.ragbackend.rag;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ragbackend.config.AiConfig.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Pipeline d'ingestion multimodal :
 *   1. Détection du type → sélection du parser (PDF, texte) ou description d'image (GPT-4o vision)
 *   2. Chunking en TextSegments (DocumentSplitters.recursive)
 *   3. Enrichissement des métadonnées (documentId, source, owner, mime, kind, ingestedAt)
 *   4. Embedding batch + insertion pgvector
 *
 * Images : on décrit l'image avec le modèle de vision en amont, puis on indexe
 * la description textuelle. C'est le pattern recommandé pour du RAG multimodal
 * simple. Pour un vrai search cross-modal (CLIP), il faudrait un second
 * embedding store avec un modèle image dédié.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestionService {

    static final Set<String> TEXT_EXTENSIONS = Set.of(".txt", ".md", ".csv", ".json", ".xml", ".html", ".log");

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ImageDescriptionService imageDescriptionService;
    private final AiProperties props;

    public IngestionResult ingest(MultipartFile file, String ownerId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        String filename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        return ingest(file.getBytes(), filename, contentType, ownerId);
    }

    public IngestionResult ingest(byte[] content, String filename, String contentType, String ownerId) {
        log.info("Ingesting file '{}' ({} bytes, {})", filename, content.length, contentType);

        DocumentKind kind = detectKind(filename, contentType);
        Document document = switch (kind) {
            case PDF -> parse(new ApachePdfBoxDocumentParser(), content, filename);
            case TEXT -> parse(new TextDocumentParser(), content, filename);
            case IMAGE -> Document.from(imageDescriptionService.describe(content, contentType));
        };

        String documentId = UUID.randomUUID().toString();
        // Enrichissement des métadonnées : permet aux tools de citer la source
        // et à l'API de lister / supprimer un document.
        document.metadata()
                .put("documentId", documentId)
                .put("source", filename)
                .put("owner", ownerId)
                .put("mime", contentType)
                .put("kind", kind.name().toLowerCase(Locale.ROOT))
                .put("ingestedAt", Instant.now().toString());

        DocumentSplitter splitter = DocumentSplitters.recursive(
                props.getRag().getChunkSize(),
                props.getRag().getChunkOverlap());

        List<TextSegment> segments = splitter.split(document);
        log.info("Split '{}' into {} segments", filename, segments.size());

        // Embedding en batch : Langchain4j regroupe automatiquement les appels
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        return new IngestionResult(documentId, filename, segments.size(), contentType, kind.name());
    }

    /** Supprime tous les segments d'un document. */
    public void delete(String documentId) {
        embeddingStore.removeAll(metadataKey("documentId").isEqualTo(documentId));
    }

    static DocumentKind detectKind(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);

        if (type.equals("application/pdf") || name.endsWith(".pdf")) {
            return DocumentKind.PDF;
        }
        if (type.startsWith("image/")) {
            return DocumentKind.IMAGE;
        }
        if (type.startsWith("text/") || type.equals("application/json") || type.equals("application/xml")
                || TEXT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
            return DocumentKind.TEXT;
        }
        throw new UnsupportedDocumentTypeException(
                "Type de fichier non supporté : '%s' (%s). Formats acceptés : PDF, texte, images."
                        .formatted(filename, contentType));
    }

    private Document parse(DocumentParser parser, byte[] content, String filename) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            return parser.parse(in);
        } catch (BlankDocumentException e) {
            throw new IllegalArgumentException(
                    "Aucun texte exploitable dans '%s' (PDF scanné ? envoyez plutôt les pages en image)."
                            .formatted(filename));
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible de '%s'".formatted(filename), e);
        }
    }

    public enum DocumentKind { PDF, TEXT, IMAGE }

    public record IngestionResult(String documentId, String filename, int segments, String mimeType, String kind) {}

    public static class UnsupportedDocumentTypeException extends RuntimeException {
        public UnsupportedDocumentTypeException(String message) {
            super(message);
        }
    }
}
