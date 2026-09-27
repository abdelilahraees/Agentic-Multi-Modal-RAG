package org.example.ragbackend.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ragbackend.agent.CurrentUser;
import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.service.TransactionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool "métier" : expose des méthodes du service transactionnel à l'agent.
 * L'agent peut ainsi consulter ou créer des transactions en réponse à une
 * demande utilisateur ("montre-moi mes 5 derniers achats", "ajoute une
 * dépense de 42€ pour un déjeuner").
 *
 * L'utilisateur n'est jamais un paramètre choisi par le LLM : il provient de
 * {@link CurrentUser}, positionné par la couche web.
 *
 * Séparer clairement Tool ↔ Service permet :
 *  - de garder la logique métier testable indépendamment du LLM
 *  - d'ajouter facilement d'autres tools (paiements, notifications...)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionTool {

    private final TransactionService transactionService;

    @Tool("Retourne les dernières transactions de l'utilisateur connecté, du plus récent au plus ancien.")
    public String listRecentTransactions(
            @P("Nombre maximum de transactions à retourner (1 à 100, 10 conseillé)") int limit) {

        String userId = CurrentUser.get();
        log.info("Tool listRecentTransactions user={} limit={}", userId, limit);
        List<Transaction> transactions = transactionService.listRecent(userId, limit <= 0 ? 10 : limit);
        if (transactions.isEmpty()) {
            return "Aucune transaction trouvée pour cet utilisateur.";
        }
        return transactions.stream()
                .map(t -> "- #%d | %s | %s %s | %s".formatted(
                        t.getId(), t.getCreatedAt().toLocalDate(),
                        t.getAmount().toPlainString(), t.getCurrency(), t.getLabel()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("Calcule le solde (somme des montants) des transactions de l'utilisateur connecté, par devise.")
    public String getBalance() {
        String userId = CurrentUser.get();
        log.info("Tool getBalance user={}", userId);
        var balances = transactionService.balanceByCurrency(userId);
        if (balances.isEmpty()) {
            return "Aucune transaction : solde nul.";
        }
        return balances.entrySet().stream()
                .map(e -> "%s %s".formatted(e.getValue().toPlainString(), e.getKey()))
                .collect(Collectors.joining(", "));
    }

    @Tool("Crée une nouvelle transaction pour l'utilisateur connecté et retourne son identifiant.")
    public String createTransaction(
            @P("Libellé descriptif de la transaction") String label,
            @P("Montant (positif = crédit, négatif = débit/dépense)") BigDecimal amount,
            @P("Devise ISO 3 lettres, ex: EUR, USD") String currency) {

        String userId = CurrentUser.get();
        log.info("Tool createTransaction user={} amount={} {}", userId, amount, currency);
        var saved = transactionService.create(userId, label, amount, currency);
        return "Transaction créée avec l'id=%d (%s %s, %s)".formatted(
                saved.getId(), saved.getAmount().toPlainString(), saved.getCurrency(), saved.getLabel());
    }
}
