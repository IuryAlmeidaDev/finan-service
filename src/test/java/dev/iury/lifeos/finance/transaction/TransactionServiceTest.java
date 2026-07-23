package dev.iury.lifeos.finance.transaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.account.AccountService;
import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class TransactionServiceTest {

    @Inject AccountService accountService;
    @Inject AccountRepository accounts;
    @Inject dev.iury.lifeos.finance.repository.TransactionRepository transactions;

    private Account account;

    @BeforeEach
    @Transactional
    void setup() {
        transactions.deleteAll();
        accounts.deleteAll();

        account = new Account();
        account.name = "Main";
        account.type = AccountType.CHECKING;
        account.initialBalance = new BigDecimal("100.00");
        account.initialBalanceDate = LocalDate.now();
        account.includeInTotal = true;
        accounts.persist(account);
    }

    @Test
    @Transactional
    void testAccountServiceRules() {
        // archive requires balance 0
        assertThatThrownBy(() -> accountService.archive(account.id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("balance");
            
        // delete requires EXCLUIR
        assertThatThrownBy(() -> accountService.delete(account.id, "DELETE"))
            .isInstanceOf(IllegalArgumentException.class);
            
        // delete requires archived
        assertThatThrownBy(() -> accountService.delete(account.id, "EXCLUIR"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("archived");
    }
}
