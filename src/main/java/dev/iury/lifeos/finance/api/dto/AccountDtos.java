package dev.iury.lifeos.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import dev.iury.lifeos.finance.account.Balance;
import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            @NotNull BigDecimal initialBalance,
            @NotNull LocalDate initialBalanceDate,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
            @Size(max = 80) String iconSlug,
            boolean includeInTotal) {
    }

    public record Response(UUID id, String name, AccountType type, BigDecimal initialBalance,
            LocalDate initialBalanceDate, String color, String iconSlug, boolean includeInTotal, boolean archived) {
        public static Response from(Account account) {
            return new Response(account.id, account.name, account.type, account.initialBalance, account.initialBalanceDate,
                    account.color, account.iconSlug, account.includeInTotal, account.archived);
        }
    }

    public record BalanceResponse(BigDecimal realized, BigDecimal projected) {
        public static BalanceResponse from(Balance balance) {
            return new BalanceResponse(balance.realized(), balance.projected());
        }
    }

    public record DeleteRequest(@NotBlank String confirmation) {
    }
}
