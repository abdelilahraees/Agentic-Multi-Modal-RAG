package org.example.ragbackend.service;

import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    TransactionRepository repository;

    @InjectMocks
    TransactionService service;

    @Test
    void listRecentClampsLimit() {
        when(repository.findByUserIdOrderByCreatedAtDesc(eq("u1"), any())).thenReturn(List.of());

        service.listRecent("u1", 10_000);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdOrderByCreatedAtDesc(eq("u1"), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void createNormalizesCurrencyAndLabel() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction t = service.create("u1", "  Déjeuner ", new BigDecimal("-42.00"), "usd");

        assertThat(t.getCurrency()).isEqualTo("USD");
        assertThat(t.getLabel()).isEqualTo("Déjeuner");
        assertThat(t.getUserId()).isEqualTo("u1");
    }

    @Test
    void createDefaultsToEur() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create("u1", "x", BigDecimal.ONE, null).getCurrency()).isEqualTo("EUR");
    }

    @Test
    void createRejectsInvalidInput() {
        assertThatThrownBy(() -> service.create("u1", " ", BigDecimal.ONE, "EUR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("u1", "x", BigDecimal.ZERO, "EUR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("u1", "x", BigDecimal.ONE, "EURO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
