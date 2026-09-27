package org.example.ragbackend.repository;

import org.example.ragbackend.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("""
            select t.currency as currency, sum(t.amount) as total
            from Transaction t
            where t.userId = :userId
            group by t.currency
            """)
    List<CurrencyTotal> sumByCurrency(String userId);

    interface CurrencyTotal {
        String getCurrency();
        BigDecimal getTotal();
    }
}
