package org.example.ragbackend.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.ragbackend.domain.Transaction;
import org.example.ragbackend.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * API REST "classique" sur les transactions : permet au front d'afficher
 * l'effet des actions de l'agent (et de tester sans LLM).
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionDto> list(@RequestParam(defaultValue = "demo") String userId,
                                     @RequestParam(defaultValue = "20") int limit) {
        return transactionService.listRecent(userId, limit).stream().map(TransactionDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto create(@Valid @RequestBody CreateTransactionRequest request) {
        return TransactionDto.from(transactionService.create(
                request.userId(), request.label(), request.amount(), request.currency()));
    }

    public record CreateTransactionRequest(
            @NotBlank @Size(max = 64) String userId,
            @NotBlank @Size(max = 255) String label,
            @NotNull BigDecimal amount,
            @Size(min = 3, max = 3) String currency) {}

    public record TransactionDto(Long id, String userId, String label, BigDecimal amount,
                                 String currency, OffsetDateTime createdAt) {
        static TransactionDto from(Transaction t) {
            return new TransactionDto(t.getId(), t.getUserId(), t.getLabel(), t.getAmount(),
                    t.getCurrency(), t.getCreatedAt());
        }
    }
}
