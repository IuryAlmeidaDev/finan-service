package dev.iury.lifeos.finance.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.iury.lifeos.finance.common.Money;
import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.model.AccountType;
import dev.iury.lifeos.finance.repository.AccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AccountService {

    @Inject
    AccountRepository accounts;

    @Inject
    BalanceCalculator balanceCalculator;

    @Transactional
    public Account create(String name, AccountType type, BigDecimal initialBalance, LocalDate initialBalanceDate,
            String color, String iconSlug, boolean includeInTotal) {
        Account account = new Account();
        apply(account, name, type, initialBalance, initialBalanceDate, color, iconSlug, includeInTotal);
        accounts.persist(account);
        return account;
    }

    public List<Account> list(boolean includeArchived) {
        return includeArchived ? accounts.listAll() : accounts.list("archived = false");
    }

    public Account findById(UUID id) {
        return accounts.findByIdOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    @Transactional
    public Account update(UUID id, String name, AccountType type, BigDecimal initialBalance, LocalDate initialBalanceDate,
            String color, String iconSlug, boolean includeInTotal) {
        Account account = findById(id);
        apply(account, name, type, initialBalance, initialBalanceDate, color, iconSlug, includeInTotal);
        return account;
    }

    public Balance balance(UUID id) {
        return balanceCalculator.calculate(findById(id), LocalDate.now());
    }

    @Transactional
    public void archive(UUID id) {
        Account account = findById(id);
        Balance balance = balanceCalculator.calculate(account, LocalDate.now());
        if (balance.realized().compareTo(java.math.BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account balance is not zero");
        }
        account.archived = true;
    }

    @Transactional
    public void unarchive(UUID id) {
        Account account = findById(id);
        account.archived = false;
    }

    @Transactional
    public void delete(UUID id, String confirmation) {
        if (!"EXCLUIR".equals(confirmation)) {
            throw new IllegalArgumentException("Invalid confirmation");
        }
        Account account = findById(id);
        if (!account.archived) {
            throw new IllegalStateException("Account must be archived to be deleted");
        }
        accounts.delete(account);
    }

    private static void apply(Account account, String name, AccountType type, BigDecimal initialBalance,
            LocalDate initialBalanceDate, String color, String iconSlug, boolean includeInTotal) {
        if (name == null || name.isBlank() || type == null || initialBalance == null || initialBalanceDate == null) {
            throw new IllegalArgumentException("Account name, type, initial balance and initial balance date are required");
        }
        account.name = name.trim();
        account.type = type;
        account.initialBalance = Money.scale(initialBalance);
        account.initialBalanceDate = initialBalanceDate;
        account.color = color;
        account.iconSlug = iconSlug;
        account.includeInTotal = includeInTotal;
    }
}
