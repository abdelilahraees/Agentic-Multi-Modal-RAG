package org.example.ragbackend.web;

import org.example.ragbackend.agent.ChatService;
import org.example.ragbackend.agent.ChatService.ChatReply;
import org.example.ragbackend.agent.ChatService.SourceRef;
import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.rag.DocumentCatalog;
import org.example.ragbackend.rag.DocumentCatalog.DocumentSummary;
import org.example.ragbackend.rag.RagIngestionService;
import org.example.ragbackend.rag.RagIngestionService.IngestionResult;
import org.example.ragbackend.rag.RagIngestionService.UnsupportedDocumentTypeException;
import org.example.ragbackend.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ChatController.class, IngestionController.class, TransactionController.class})
class ControllersTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatService chatService;
    @MockitoBean
    RagIngestionService ingestionService;
    @MockitoBean
    DocumentCatalog documentCatalog;
    @MockitoBean
    TransactionService transactionService;

    @Test
    void chatReturnsReplyWithSources() throws Exception {
        when(chatService.chat("c1", "demo", "Bonjour"))
                .thenReturn(new ChatReply("c1", "Salut !", List.of(new SourceRef("doc.pdf", "extrait", 0.9)),
                        List.of("searchDocuments"), null));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"Bonjour\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Salut !"))
                .andExpect(jsonPath("$.sources[0].source").value("doc.pdf"))
                .andExpect(jsonPath("$.toolsUsed[0]").value("searchDocuments"));
    }

    @Test
    void chatValidatesPayload() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void chatAcceptsImage() throws Exception {
        var image = new MockMultipartFile("image", "ticket.jpg", "image/jpeg", new byte[]{1, 2});
        when(chatService.chat(eq("c1"), eq("alice"), eq("Ajoute ça"), any(), eq("image/jpeg")))
                .thenReturn(new ChatReply("c1", "Fait", List.of(), List.of("createTransaction"), "Ticket"));

        mvc.perform(multipart("/api/chat").file(image)
                        .param("conversationId", "c1").param("userId", "alice").param("message", "Ajoute ça"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageDescription").value("Ticket"));
    }

    @Test
    void chatRejectsNonImageAttachment() throws Exception {
        var file = new MockMultipartFile("image", "a.txt", "text/plain", new byte[]{1});

        mvc.perform(multipart("/api/chat").file(file).param("conversationId", "c1").param("message", "x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aiProviderFailureGives502WithoutStackTrace() throws Exception {
        when(chatService.chat("c1", "demo", "Bonjour"))
                .thenThrow(new RuntimeException(new java.io.IOException("Tunnel failed, got: 403")));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"c1\",\"message\":\"Bonjour\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("OPENAI_API_KEY")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void resetConversation() throws Exception {
        mvc.perform(delete("/api/chat/c1")).andExpect(status().isNoContent());
        verify(chatService).reset("c1");
    }

    @Test
    void uploadDocument() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        when(ingestionService.ingest(any(org.springframework.web.multipart.MultipartFile.class), eq("anonymous")))
                .thenReturn(new IngestionResult("id-1", "notes.txt", 1, "text/plain", "TEXT"));

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value("id-1"));
    }

    @Test
    void uploadUnsupportedDocumentGives415() throws Exception {
        var file = new MockMultipartFile("file", "a.zip", "application/zip", new byte[]{1});
        when(ingestionService.ingest(any(org.springframework.web.multipart.MultipartFile.class), anyString()))
                .thenThrow(new UnsupportedDocumentTypeException("non supporté"));

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("non supporté"));
    }

    @Test
    void listAndDeleteDocuments() throws Exception {
        when(documentCatalog.list()).thenReturn(List.of(
                new DocumentSummary("id-1", "a.pdf", "demo", "application/pdf", "pdf", "2026-01-01T00:00:00Z", 3)));

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].segments").value(3));

        mvc.perform(delete("/api/documents/id-1")).andExpect(status().isNoContent());
        verify(ingestionService).delete("id-1");
    }

    @Test
    void listAndCreateTransactions() throws Exception {
        var t = new Transaction(1L, "demo", "Loyer", new BigDecimal("-950.00"), "EUR", OffsetDateTime.now());
        when(transactionService.listRecent("demo", 20)).thenReturn(List.of(t));
        when(transactionService.create("demo", "Loyer", new BigDecimal("-950.00"), "EUR")).thenReturn(t);

        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Loyer"));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"demo\",\"label\":\"Loyer\",\"amount\":-950.00,\"currency\":\"EUR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }
}
