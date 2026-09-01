package dev.iury.lifeos.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class TransactionDtos {
    private TransactionDtos() { }
    public record Request(@NotNull UUID accountId, @NotNull TransactionType type, @NotNull @Positive BigDecimal amount,
            @NotNull LocalDate date, @NotNull UUID categoryId, @Size(max = 255) String description,
            boolean paid, boolean ignoredFromBudget) { }
    public record TransferRequest(@NotNull UUID accountId, @NotNull UUID destinationAccountId,
            @NotNull @Positive BigDecimal amount, @NotNull LocalDate date, @Size(max = 255) String description,
            boolean paid) { }
    public record Response(UUID id, UUID accountId, UUID destinationAccountId, BigDecimal amount, TransactionType type,
            LocalDate date, UUID categoryId, String description, boolean paid, boolean ignoredFromBudget,
            boolean ignoredFromReports) {
        public static Response from(FinancialTransaction value) {
            return new Response(value.id, value.account.id, value.destinationAccount == null ? null : value.destinationAccount.id,
                    value.amount, value.type, value.date, value.category == null ? null : value.category.id,
                    value.description, value.paid, value.ignoredFromBudget, value.ignoredFromReports);
        }
    }
}
