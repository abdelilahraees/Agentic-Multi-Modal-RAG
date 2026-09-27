package org.example.ragbackend;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.example.ragbackend.agent.ChatService;
import org.example.ragbackend.agent.CurrentUser;
import org.example.ragbackend.rag.DocumentCatalog;
import org.example.ragbackend.rag.RagIngestionService;
import org.example.ragbackend.tools.RagRetrievalTool;
import org.example.ragbackend.tools.TransactionTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration de bout en bout sur un vrai PostgreSQL + pgvector (Testcontainers) :
 * migrations Flyway, JPA, ingestion, recherche vectorielle, catalogue et tools.
 * Le modèle d'embeddings OpenAI est remplacé par un modèle déterministe local
 * (sac de mots haché) pour ne dépendre d'aucune clé API. Ignoré sans Docker.
 */
@SpringBootTest(properties = "app.ai.rag.dimension=64")
@Testcontainers(disabledWithoutDocker = true)
class RagBackendApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    RagIngestionService ingestionService;
    @Autowired
    DocumentCatalog catalog;
    @Autowired
    RagRetrievalTool ragTool;
    @Autowired
    TransactionTool transactionTool;
    @Autowired
    ChatService chatService;

    @Test
    void ingestSearchListAndDeleteDocuments() {
        var doc = ingestionService.ingest(
                "La politique de sécurité impose une rotation des mots de passe tous les 90 jours."
                        .getBytes(StandardCharsets.UTF_8), "securite.txt", "text/plain", "demo");
        ingestionService.ingest("Recette de la tarte aux pommes : farine, beurre, sucre, pommes."
                .getBytes(StandardCharsets.UTF_8), "recette.md", "text/markdown", "demo");

        assertThat(catalog.list()).extracting(DocumentCatalog.DocumentSummary::source)
                .contains("securite.txt", "recette.md");

        String hits = ragTool.searchDocuments("rotation des mots de passe", 1);
        assertThat(hits).contains("[Source: securite.txt").contains("90 jours");

        ingestionService.delete(doc.documentId());
        assertThat(catalog.list()).extracting(DocumentCatalog.DocumentSummary::source)
                .doesNotContain("securite.txt");
    }

    @Test
    void transactionToolUsesSeededDataAndPersists() {
        String before = CurrentUser.runAs("demo", () -> transactionTool.listRecentTransactions(10));
        assertThat(before).contains("Salaire").contains("Loyer");

        String created = CurrentUser.runAs("it-user",
                () -> transactionTool.createTransaction("Café", new BigDecimal("-3.20"), "eur"));
        assertThat(created).contains("-3.20 EUR");
        assertThat(CurrentUser.runAs("it-user", transactionTool::getBalance)).isEqualTo("-3.20 EUR");
        assertThat(CurrentUser.runAs("it-user", () -> transactionTool.listRecentTransactions(10)))
                .contains("Café").doesNotContain("Salaire");
    }

    @Test
    void agentCallsToolForCurrentUserAndReturnsSources() {
        ingestionService.ingest("Le plafond de remboursement des notes de frais est de 150 euros."
                .getBytes(StandardCharsets.UTF_8), "frais.txt", "text/plain", "demo");

        var reply = chatService.chat("conv-it", "demo", "Quel est mon solde et le plafond des notes de frais ?");

        assertThat(reply.toolsUsed()).containsExactly("getBalance");
        assertThat(reply.reply()).startsWith("Solde calculé : ").contains("EUR");
        assertThat(reply.sources()).extracting(ChatService.SourceRef::source).contains("frais.txt");
    }

    @TestConfiguration
    static class FakeAiModels {
        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            return new HashingEmbeddingModel(64);
        }

        /**
         * Modèle de chat scénarisé : demande d'abord l'exécution du tool getBalance,
         * puis répond en recopiant le résultat du tool. Valide le câblage AiServices
         * (mémoire, retriever, tools) sans appeler OpenAI.
         */
        @Bean
        @Primary
        ChatModel fakeChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    List<ChatMessage> messages = request.messages();
                    ChatMessage last = messages.getLast();
                    if (last instanceof ToolExecutionResultMessage toolResult) {
                        return ChatResponse.builder()
                                .aiMessage(AiMessage.from("Solde calculé : " + toolResult.text())).build();
                    }
                    assertThat(request.toolSpecifications()).extracting(t -> t.name())
                            .contains("getBalance", "listRecentTransactions", "createTransaction", "searchDocuments");
                    return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-1").name("getBalance").arguments("{}").build())).build();
                }
            };
        }
    }

    /** Embedding déterministe : histogramme normalisé des mots hachés. */
    static class HashingEmbeddingModel implements EmbeddingModel {
        private final int dim;

        HashingEmbeddingModel(int dim) {
            this.dim = dim;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream().map(s -> embedText(s.text())).toList());
        }

        private Embedding embedText(String text) {
            float[] v = new float[dim];
            for (String w : text.toLowerCase(Locale.ROOT).split("\\P{L}+")) {
                if (w.length() > 2) v[Math.floorMod(w.hashCode(), dim)] += 1f;
            }
            double norm = 0;
            for (float f : v) norm += f * f;
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < dim; i++) v[i] /= (float) norm;
            return Embedding.from(v);
        }
    }
}
