package org.example.ragbackend.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ragbackend.service.TransactionService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Tool "métier" : expose des méthodes du service transactionnel à l'agent.
 * L'agent peut ainsi consulter ou créer des transactions en réponse à une
 * demande utilisateur ("montre-moi mes 5 derniers achats", "ajoute une
 * dépense de 42€ pour un déjeuner").
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

    @Tool("Retourne les dernières transactions d'un utilisateur, du plus récent au plus ancien.")
    public String listRecentTransactions(
            @P("Identifiant utilisateur") String userId,
            @P("Nombre maximum de transactions à retourner (défaut 10)") int limit) {

        log.info("Tool listRecentTransactions user={} limit={}", userId, limit);
        return transactionService.listRecent(userId, limit).stream()
                .map(t -> "- %s | %.2f %s | %s".formatted(
                        t.getCreatedAt(), t.getAmount(), t.getCurrency(), t.getLabel()))
                .reduce("", (a, b) -> a + "\n" + b)
                .trim();
    }

    @Tool("Crée une nouvelle transaction pour l'utilisateur et retourne son identifiant.")
    public String createTransaction(
            @P("Identifiant utilisateur") String userId,
            @P("Libellé descriptif de la dépense") String label,
            @P("Montant (positif = crédit, négatif = débit)") BigDecimal amount,
            @P("Devise ISO 3 lettres, ex: EUR, USD") String currency) {

        log.info("Tool createTransaction user={} amount={} {}", userId, amount, currency);
        var saved = transactionService.create(userId, label, amount, currency);
        return "Transaction créée avec l'id=" + saved.getId();
    }
}
