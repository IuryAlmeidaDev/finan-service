package dev.iury.lifeos.finance.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;

class AccountDtosTest {

    @Test
    void mapsAccountEntityWithoutExposingItFromTheApi() {
        Account account = new Account();
        account.id = UUID.randomUUID();
        account.name = "Conta principal";
        account.type = AccountType.CHECKING;
        account.initialBalance = new BigDecimal("10.00");
        account.initialBalanceDate = LocalDate.of(2026, 9, 1);
        account.includeInTotal = true;

        AccountDtos.Response response = AccountDtos.Response.from(account);

        assertThat(response.id()).isEqualTo(account.id);
        assertThat(response.name()).isEqualTo("Conta principal");
        assertThat(response.initialBalance()).isEqualByComparingTo("10.00");
    }
}
