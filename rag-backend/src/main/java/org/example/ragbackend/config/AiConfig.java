package org.example.ragbackend.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Centralise tous les beans liés à la couche IA :
 *   - Modèle de chat (GPT-4o par défaut, multimodal : texte + vision)
 *   - Modèle d'embeddings (text-embedding-3-small par défaut)
 *   - EmbeddingStore branché sur pgvector (réutilise le DataSource Spring)
 *   - ContentRetriever prêt à être injecté dans l'agent
 *
 * Toute la configuration provient de application.yml (préfixe app.ai).
 * Pour basculer sur LLaMA 3 local, il suffit de remplacer les beans
 * OpenAiChatModel/OpenAiEmbeddingModel par OllamaChatModel/OllamaEmbeddingModel.
 */
@Configuration
@EnableConfigurationProperties(AiConfig.AiProperties.class)
public class AiConfig {

    // -------------------- Modèle de chat (GPT-4o multimodal) --------------------
    @Bean
    public ChatModel chatModel(AiProperties props) {
        return OpenAiChatModel.builder()
                .apiKey(props.getOpenai().getApiKey())
                .modelName(props.getOpenai().getChatModel())
                .timeout(Duration.ofSeconds(props.getOpenai().getTimeoutSeconds()))
                .temperature(0.2)
                .logRequests(props.getOpenai().isLogRequests())
                .logResponses(props.getOpenai().isLogRequests())
                .build();
    }

    // -------------------- Modèle d'embeddings --------------------
    @Bean
    public EmbeddingModel embeddingModel(AiProperties props) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(props.getOpenai().getApiKey())
                .modelName(props.getOpenai().getEmbeddingModel())
                .timeout(Duration.ofSeconds(props.getOpenai().getTimeoutSeconds()))
                .build();
    }

    // -------------------- Vector store pgvector --------------------
    // Réutilise le DataSource (Hikari) de Spring : une seule configuration de
    // connexion, compatible avec docker-compose / Testcontainers.
    // La table est créée automatiquement si elle n'existe pas.
    // Dimension : 1536 pour text-embedding-3-small, 3072 pour text-embedding-3-large.
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource, AiProperties props) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(props.getRag().getTable())
                .dimension(props.embeddingDimension())
                .createTable(true)
                .build();
    }

    // -------------------- ContentRetriever (utilisé par l'agent + RAG tool) --------------------
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store,
                                             EmbeddingModel embeddingModel,
                                             AiProperties props) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(props.getRag().getMaxResults())
                .minScore(props.getRag().getMinScore())
                .build();
    }

    // -------------------- Propriétés typées (bindées sur app.ai.*) --------------------
    @Data
    @ConfigurationProperties(prefix = "app.ai")
    public static class AiProperties {
        private OpenAi openai = new OpenAi();
        private Rag rag = new Rag();
        private Memory memory = new Memory();

        public int embeddingDimension() {
            if (rag.getDimension() != null) return rag.getDimension();
            return openai.getEmbeddingModel().contains("large") ? 3072 : 1536;
        }

        @Data
        public static class OpenAi {
            private String apiKey;
            private String chatModel = "gpt-4o";
            private String embeddingModel = "text-embedding-3-small";
            private int timeoutSeconds = 60;
            private boolean logRequests = false;
        }

        @Data
        public static class Rag {
            private String table = "embeddings";
            /** Force la dimension des vecteurs ; déduite du modèle si absente. */
            private Integer dimension;
            private int maxResults = 5;
            private double minScore = 0.6;
            private int chunkSize = 500;
            private int chunkOverlap = 50;
        }

        @Data
        public static class Memory {
            private int maxMessages = 20;
        }
    }
}
