package org.example.ragbackend.web;

import lombok.RequiredArgsConstructor;
import org.example.ragbackend.rag.DocumentCatalog;
import org.example.ragbackend.rag.DocumentCatalog.DocumentSummary;
import org.example.ragbackend.rag.RagIngestionService;
import org.example.ragbackend.rag.RagIngestionService.IngestionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class IngestionController {

    private final RagIngestionService ingestionService;
    private final DocumentCatalog documentCatalog;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IngestionResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ownerId", defaultValue = "anonymous") String ownerId) throws IOException {

        return ingestionService.ingest(file, ownerId);
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return documentCatalog.list();
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        ingestionService.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}
