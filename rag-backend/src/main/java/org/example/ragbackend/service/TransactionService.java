package org.example.ragbackend.service;

import lombok.RequiredArgsConstructor;
import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final TransactionRepository repository;

    @Transactional(readOnly = true)
    public List<Transaction> listRecent(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit));
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> balanceByCurrency(String userId) {
        Map<String, BigDecimal> balances = new TreeMap<>();
        for (TransactionRepository.CurrencyTotal total : repository.sumByCurrency(userId)) {
            balances.put(total.getCurrency(), total.getTotal());
        }
        return balances;
    }

    @Transactional
    public Transaction create(String userId, String label, BigDecimal amount, String currency) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("L'identifiant utilisateur est obligatoire");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Le libellé est obligatoire");
        }
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Le montant doit être non nul");
        }
        String normalizedCurrency = currency == null || currency.isBlank()
                ? "EUR" : currency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(normalizedCurrency).matches()) {
            throw new IllegalArgumentException("Devise invalide : " + currency);
        }
        return repository.save(Transaction.builder()
                .userId(userId)
                .label(label.trim())
                .amount(amount)
                .currency(normalizedCurrency)
                .build());
    }
}
