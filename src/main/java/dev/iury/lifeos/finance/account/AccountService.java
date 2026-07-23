package dev.iury.lifeos.finance.account;

import java.time.LocalDate;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.repository.AccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AccountService {

    @Inject
    AccountRepository accounts;

    @Inject
    BalanceCalculator balanceCalculator;

    public void archive(UUID id) {
        Account account = accounts.findById(id);
        Balance balance = balanceCalculator.calculate(account, LocalDate.now());
        if (balance.realized().compareTo(java.math.BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account balance is not zero");
        }
        account.archived = true;
    }

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
