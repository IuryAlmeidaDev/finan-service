package dev.iury.lifeos.finance.account;

import java.time.LocalDate;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Account;
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
    public void archive(UUID id) {
        Account account = accounts.findById(id);
        Balance balance = balanceCalculator.calculate(account, LocalDate.now());
        if (balance.realized().compareTo(java.math.BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account balance is not zero");
        }
        account.archived = true;
    }

    @Transactional
    public void unarchive(UUID id) {
        Account account = accounts.findById(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found: " + id);
        }
        account.archived = false;
    }

    @Transactional
    public void delete(UUID id, String confirmation) {
        if (!"EXCLUIR".equals(confirmation)) {
            throw new IllegalArgumentException("Invalid confirmation");
        }
        Account account = accounts.findById(id);
        if (!account.archived) {
            throw new IllegalStateException("Account must be archived to be deleted");
        }
        accounts.delete(account);
    }
}
