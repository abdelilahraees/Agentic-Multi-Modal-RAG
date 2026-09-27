package org.example.ragbackend.agent;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.example.ragbackend.config.AiConfig.AiProperties;
import org.example.ragbackend.tools.RagRetrievalTool;
import org.example.ragbackend.tools.TransactionTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assemble l'agent Langchain4j :
 *   - Modèle de chat
 *   - Mémoire conversationnelle par conversation (via memoryId)
 *   - ContentRetriever pour un RAG automatique sur chaque appel
 *   - Tools métier invoquables autonomement par le LLM
 *
 * Note prod : remplacer InMemoryChatMemoryStore par une implémentation
 * persistante (Redis, JDBC) pour survivre aux redémarrages.
 */
@Configuration
public class AgentFactory {

    @Bean
    public ChatMemoryStore chatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store, AiProperties props) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(props.getMemory().getMaxMessages())
                .chatMemoryStore(store)
                .build();
    }

    @Bean
    public Assistant assistant(ChatModel chatModel,
                               ChatMemoryProvider memoryProvider,
                               ContentRetriever contentRetriever,
                               RagRetrievalTool ragTool,
                               TransactionTool transactionTool) {

        return AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryProvider)
                // Le retriever branche un RAG "automatique" sur chaque question.
                // Combiné avec le RagRetrievalTool ci-dessous, l'agent peut aussi
                // décider explicitement de re-chercher dans la base vectorielle.
                .contentRetriever(contentRetriever)
                .tools(ragTool, transactionTool)
                .build();
    }
}
