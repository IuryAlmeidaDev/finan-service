package dev.iury.lifeos.finance.account;

import java.math.BigDecimal;
import java.time.LocalDate;

import dev.iury.lifeos.finance.model.Account;
import dev.iury.lifeos.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BalanceCalculator {

    @Inject
    TransactionRepository transactions;

    public Balance calculate(Account account, LocalDate date) {
        BigDecimal sumPaid = transactions.sumForAccount(account.id, date, true);
        BigDecimal realized = account.initialBalance.add(sumPaid);

        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());
        BigDecimal sumAll = transactions.sumForAccount(account.id, endOfMonth, false);
        BigDecimal projected = account.initialBalance.add(sumAll);

        return new Balance(realized, projected);
    }
}
