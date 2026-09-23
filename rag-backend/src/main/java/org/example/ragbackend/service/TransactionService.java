package org.example.ragbackend.service;

import lombok.RequiredArgsConstructor;
import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;

    @Transactional(readOnly = true)
    public List<Transaction> listRecent(String userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit));
    }

    @Transactional
    public Transaction create(String userId, String label, BigDecimal amount, String currency) {
        return repository.save(Transaction.builder()
                .userId(userId)
                .label(label)
                .amount(amount)
                .currency(currency == null ? "EUR" : currency.toUpperCase())
                .build());
    }
}
