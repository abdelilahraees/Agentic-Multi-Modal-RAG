package org.example.ragbackend.rag;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Pipeline d'ingestion multimodal :
 *   1. Détection du type MIME → sélection du parser (PDF, texte, image via GPT-4o vision)
 *   2. Chunking en TextSegments (tokens-based via DocumentSplitters)
 *   3. Enrichissement des métadonnées (source, mime)
 *   4. Embedding batch + insertion pgvector
 *
 * Point d'attention images : ici on décrit l'image avec le modèle de vision
 * en amont, puis on indexe la description textuelle. C'est le pattern
 * recommandé pour du RAG multimodal simple. Pour un vrai search cross-modal
 * (CLIP), il faudra un second embedding store avec un modèle image dédié.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final AiProperties props;
    // Injecter ici un ImageDescriptionService (basé sur GPT-4o vision) pour l'ingestion images.
    // private final ImageDescriptionService imageDescriptionService;

    public IngestionResult ingest(MultipartFile file, String ownerId) throws IOException {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        log.info("Ingesting file '{}' ({} bytes, {})", filename, file.getSize(), contentType);

        Document document = parseByMimeType(file, contentType);

        // Enrichissement des métadonnées : permet aux tools de citer la source
        document.metadata().put("source", filename == null ? "unknown" : filename);
        document.metadata().put("owner", ownerId);
        document.metadata().put("mime", contentType);

        DocumentSplitter splitter = DocumentSplitters.recursive(
                props.getRag().getChunkSize(),
                props.getRag().getChunkOverlap());

        List<TextSegment> segments = splitter.split(document);
        log.info("Split into {} segments", segments.size());

        // Embedding en batch : Langchain4j regroupe automatiquement les appels
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        return new IngestionResult(filename, segments.size(), contentType);
    }

    /** Sélectionne le parser adapté au MIME type. */
    private Document parseByMimeType(MultipartFile file, String contentType) throws IOException {
        DocumentParser parser;
        if (contentType.equals("application/pdf") || endsWithIgnoreCase(file.getOriginalFilename(), ".pdf")) {
            parser = new ApachePdfBoxDocumentParser();
        } else if (contentType.startsWith("image/")) {
            // TODO: brancher un ImageDescriptionService qui envoie l'image à GPT-4o vision
            //   et renvoie une description textuelle indexable.
            //   String description = imageDescriptionService.describe(file);
            //   return Document.from(description, Metadata.from(Map.of("kind", "image-description")));
            throw new UnsupportedOperationException(
                    "Ingestion d'images non branchée : ajouter un ImageDescriptionService (GPT-4o vision).");
        } else {
            parser = new TextDocumentParser();
        }

        try (InputStream in = file.getInputStream()) {
            return parser.parse(in);
        }
    }

    private boolean endsWithIgnoreCase(String s, String suffix) {
        return s != null && s.toLowerCase().endsWith(suffix);
    }

    public record IngestionResult(String filename, int segments, String mimeType) {}
}
