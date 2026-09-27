package org.example.ragbackend.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.example.ragbackend.rag.ImageDescriptionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    Assistant assistant = mock(Assistant.class);
    ImageDescriptionService vision = mock(ImageDescriptionService.class);
    ChatMemoryStore memory = mock(ChatMemoryStore.class);
    ChatService service = new ChatService(assistant, vision, memory);

    @Test
    void mapsSourcesAndToolsAndRunsAsUser() {
        AtomicReference<String> userSeenByTools = new AtomicReference<>();
        var segment = TextSegment.from("Rotation des mots de passe : 90 jours.", Metadata.from("source", "policy.pdf"));
        var content = Content.from(segment, Map.of(ContentMetadata.SCORE, 0.87));
        var tool = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("listRecentTransactions").arguments("{}").build())
                .result("ok").build();

        when(assistant.chat(eq("c1"), anyString())).thenAnswer(inv -> {
            userSeenByTools.set(CurrentUser.get());
            return Result.<String>builder().content("Réponse").sources(List.of(content))
                    .toolExecutions(List.of(tool)).build();
        });

        var reply = service.chat("c1", "alice", "Question ?");

        assertThat(userSeenByTools.get()).isEqualTo("alice");
        assertThat(reply.reply()).isEqualTo("Réponse");
        assertThat(reply.sources()).singleElement().satisfies(s -> {
            assertThat(s.source()).isEqualTo("policy.pdf");
            assertThat(s.score()).isEqualTo(0.87);
        });
        assertThat(reply.toolsUsed()).containsExactly("listRecentTransactions");
        assertThat(reply.imageDescription()).isNull();
    }

    @Test
    void injectsImageDescriptionIntoPrompt() {
        byte[] img = {9};
        when(vision.describe(eq(img), eq("image/jpeg"), anyString())).thenReturn("Ticket de caisse : 23,90 €");
        when(assistant.chat(eq("c1"), anyString()))
                .thenReturn(Result.<String>builder().content("ok").build());

        var reply = service.chat("c1", "alice", "Ajoute cette dépense", img, "image/jpeg");

        verify(assistant).chat(eq("c1"), argThat(p -> p.startsWith("Ajoute cette dépense") && p.contains("23,90 €")));
        assertThat(reply.imageDescription()).isEqualTo("Ticket de caisse : 23,90 €");
        assertThat(reply.sources()).isEmpty();
    }

    @Test
    void resetDeletesMemory() {
        service.reset("c1");
        verify(memory).deleteMessages("c1");
    }
}
