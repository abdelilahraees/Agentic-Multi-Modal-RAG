package org.example.ragbackend.web;

import lombok.RequiredArgsConstructor;
import org.example.ragbackend.rag.RagIngestionService;
import org.example.ragbackend.rag.RagIngestionService.IngestionResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IngestionController {

    private final RagIngestionService ingestionService;

    @PostMapping(consumes = "multipart/form-data")
    public IngestionResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ownerId", defaultValue = "anonymous") String ownerId) throws IOException {

        return ingestionService.ingest(file, ownerId);
    }
}
