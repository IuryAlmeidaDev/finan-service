package dev.iury.lifeos.finance.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import io.quarkus.test.TestTransaction;`r`nimport io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class BalanceCalculatorTest {

    @Inject BalanceCalculator calculator;
    @Inject AccountRepository accounts;
    @Inject CategoryRepository categories;
    @Inject TransactionRepository transactions;

    private Account account;
    private Account otherAccount;
    private Category incomeCat;
    private Category expenseCat;

    @BeforeEach
    @Transactional
    void setup() {
        transactions.deleteAll();
        accounts.deleteAll();
        categories.delete("parentCategory is not null and system = false");`r`n        categories.delete("system", false);

        account = new Account();
        account.name = "Main";
        account.type = AccountType.CHECKING;
        account.initialBalance = new BigDecimal("100.00");
        account.initialBalanceDate = LocalDate.now().minusDays(30);
        account.includeInTotal = true;
        accounts.persist(account);

        otherAccount = new Account();
        otherAccount.name = "Other";
        otherAccount.type = AccountType.CHECKING;
        otherAccount.initialBalance = new BigDecimal("50.00");
        otherAccount.initialBalanceDate = LocalDate.now().minusDays(30);
        otherAccount.includeInTotal = true;
        accounts.persist(otherAccount);

        incomeCat = new Category();
        incomeCat.name = "Income";
        incomeCat.type = CategoryType.INCOME;
        categories.persist(incomeCat);

        expenseCat = new Category();
        expenseCat.name = "Expense";
        expenseCat.type = CategoryType.EXPENSE;
        categories.persist(expenseCat);
    }

    @Test
    @Transactional
    void shouldCalculateCorrectlyWithAllTypes() {
        // Realized: date <= today and paid = true
        // Projected: date <= end of current month (since we test against today, it is included)
        
        // Income paid -> Realized +100
        createTx(account, null, "100.00", TransactionType.INCOME, true, LocalDate.now());
        
        // Income pending -> Realized +0, Projected +200
        createTx(account, null, "200.00", TransactionType.INCOME, false, LocalDate.now());
        
        // Expense paid -> Realized -50
        createTx(account, null, "50.00", TransactionType.EXPENSE, true, LocalDate.now());
        
        // Outgoing Transfer paid -> Realized -30
        createTx(account, otherAccount, "30.00", TransactionType.TRANSFER, true, LocalDate.now());
        
        // Incoming Transfer paid -> Realized +40
        createTx(otherAccount, account, "40.00", TransactionType.TRANSFER, true, LocalDate.now());

        // Balance Adjustment outgoing (reducing balance -> account is origin)
        createTx(account, null, "20.00", TransactionType.BALANCE_ADJUSTMENT, true, LocalDate.now());

        // Balance Adjustment incoming (increasing balance -> account is destination)
        createTx(account, account, "60.00", TransactionType.BALANCE_ADJUSTMENT, true, LocalDate.now());

        // Expected Realized: Initial(100) + Income(100) - Expense(50) - Out Transfer(30) + In Transfer(40) - Out Adj(20) + In Adj(60) = 200
        // Expected Projected: Realized(200) + Pending Income(200) = 400

        Balance balance = calculator.calculate(account, LocalDate.now());
        assertThat(balance.realized()).isEqualByComparingTo("200.00");
        assertThat(balance.projected()).isEqualByComparingTo("400.00");
    }

    private void createTx(Account acc, Account dest, String amount, TransactionType type, boolean paid, LocalDate date) {
        FinancialTransaction tx = new FinancialTransaction();
        tx.account = acc;
        tx.destinationAccount = dest;
        tx.amount = new BigDecimal(amount);
        tx.type = type;
        tx.paid = paid;
        tx.date = date;
        if (type == TransactionType.INCOME) {
            tx.category = incomeCat;
        } else if (type == TransactionType.EXPENSE) {
            tx.category = expenseCat;
        } else {
            tx.category = null;
        }
        transactions.persist(tx);
    }
}
