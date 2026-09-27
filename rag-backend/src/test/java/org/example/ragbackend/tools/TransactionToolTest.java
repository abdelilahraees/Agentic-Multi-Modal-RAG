package org.example.ragbackend.tools;

import org.example.ragbackend.agent.CurrentUser;
import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionToolTest {

    @Mock
    TransactionService service;

    @InjectMocks
    TransactionTool tool;

    @Test
    void listsTransactionsOfCurrentUser() {
        var t = new Transaction(7L, "alice", "Loyer", new BigDecimal("-950.00"), "EUR",
                OffsetDateTime.parse("2026-01-05T10:00:00Z"));
        when(service.listRecent("alice", 5)).thenReturn(List.of(t));

        String out = CurrentUser.runAs("alice", () -> tool.listRecentTransactions(5));

        assertThat(out).isEqualTo("- #7 | 2026-01-05 | -950.00 EUR | Loyer");
    }

    @Test
    void defaultsLimitWhenModelSendsZero() {
        when(service.listRecent("alice", 10)).thenReturn(List.of());

        String out = CurrentUser.runAs("alice", () -> tool.listRecentTransactions(0));

        assertThat(out).contains("Aucune transaction");
    }

    @Test
    void createsTransactionForCurrentUserOnly() {
        var saved = new Transaction(42L, "bob", "Taxi", new BigDecimal("-18.50"), "EUR", OffsetDateTime.now());
        when(service.create("bob", "Taxi", new BigDecimal("-18.50"), "EUR")).thenReturn(saved);

        String out = CurrentUser.runAs("bob", () -> tool.createTransaction("Taxi", new BigDecimal("-18.50"), "EUR"));

        assertThat(out).contains("id=42");
        verify(service).create("bob", "Taxi", new BigDecimal("-18.50"), "EUR");
    }

    @Test
    void balanceIsFormattedPerCurrency() {
        when(service.balanceByCurrency("alice"))
                .thenReturn(Map.of("EUR", new BigDecimal("12.30")));

        assertThat(CurrentUser.runAs("alice", tool::getBalance)).isEqualTo("12.30 EUR");
    }

    @Test
    void refusesToRunOutsideAgentContext() {
        assertThatThrownBy(() -> tool.listRecentTransactions(5)).isInstanceOf(IllegalStateException.class);
    }
}
