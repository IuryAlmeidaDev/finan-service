package dev.iury.lifeos.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.iury.lifeos.finance.model.FinancialTransaction;
import dev.iury.lifeos.finance.model.TransactionType;
import dev.iury.lifeos.finance.transaction.TransactionFilter;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<FinancialTransaction, UUID> {
    private static final Set<String> SORT_FIELDS =
            Set.of("date", "amount", "description", "createdAt");
    private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");

    @Inject CategoryRepository categories;
    @Inject EntityManager entityManager;

    public PanacheQuery<FinancialTransaction> search(TransactionFilter filter, Page page) {
        String sortBy = normalizedSort(filter.sortBy);
        String direction = normalizedDirection(filter.sortDirection);
        StringBuilder jpql = new StringBuilder("deletedAt is null");
        Map<String, Object> parameters = new HashMap<>();

        if (!filter.normalizedAccountIds().isEmpty()) {
            jpql.append(" and account.id in :accountIds");
            parameters.put("accountIds", filter.normalizedAccountIds());
        }
        if (filter.categoryId != null) {
            jpql.append(" and category.id in :categoryIds");
            parameters.put("categoryIds", filter.includeCategoryDescendants
                    ? categories.descendantIds(filter.categoryId)
                    : Set.of(filter.categoryId));
        }
        add(jpql, parameters, filter.paid, "paid = :paid", "paid");
        add(jpql, parameters, filter.startDate, "date >= :startDate", "startDate");
        add(jpql, parameters, filter.endDate, "date <= :endDate", "endDate");
        add(jpql, parameters, filter.minAmount, "amount >= :minAmount", "minAmount");
        add(jpql, parameters, filter.maxAmount, "amount <= :maxAmount", "maxAmount");
        if (filter.search != null && !filter.search.isBlank()) {
            jpql.append(" and (description ilike :search or notes ilike :search)");
            parameters.put("search", "%" + filter.search.trim() + "%");
        }
        if (filter.tagId != null) {
            jpql.append("""
                     and id in (select tt.transaction.id
                                  from TransactionTag tt
                                 where tt.tag.id = :tagId)""");
            parameters.put("tagId", filter.tagId);
        }
        add(jpql, parameters, filter.installmentGroupId,
                "installmentGroup.id = :installmentGroupId", "installmentGroupId");
        add(jpql, parameters, filter.recurringRuleId,
                "recurringRule.id = :recurringRuleId", "recurringRuleId");
        jpql.append(" order by ").append(sortBy).append(' ').append(direction).append(", id asc");
        return find(jpql.toString(), parameters).page(page);
    }

    public Optional<FinancialTransaction> findByLinkedTaskId(UUID linkedTaskId) {
        return find("linkedTaskId = ?1 and deletedAt is null", linkedTaskId).firstResultOptional();
    }

    public BigDecimal sumPaidForAccount(UUID accountId, LocalDate throughDate) {
        return sumForAccount(accountId, throughDate, true);
    }

    public BigDecimal sumForAccount(UUID accountId, LocalDate throughDate, boolean paidOnly) {
        BigDecimal income = sumAccountSide(accountId, throughDate, TransactionType.INCOME, false, paidOnly, false);
        BigDecimal expense = sumAccountSide(accountId, throughDate, TransactionType.EXPENSE, false, paidOnly, false);
        BigDecimal outgoingTransfer =
                sumAccountSide(accountId, throughDate, TransactionType.TRANSFER, false, paidOnly, false);
        BigDecimal incomingTransfer =
                sumAccountSide(accountId, throughDate, TransactionType.TRANSFER, true, paidOnly, false);
        BigDecimal outgoingAdjustment =
                sumAccountSide(accountId, throughDate, TransactionType.BALANCE_ADJUSTMENT, false, paidOnly, true);
        BigDecimal incomingAdjustment =
                sumAccountSide(accountId, throughDate, TransactionType.BALANCE_ADJUSTMENT, true, paidOnly, false);
        return income.add(incomingTransfer).add(incomingAdjustment)
                .subtract(expense).subtract(outgoingTransfer).subtract(outgoingAdjustment);
    }

    public BigDecimal sumBudgetExpenses(UUID categoryId, boolean includePending,
            LocalDate startDate, LocalDate endDate) {
        return sumByPurpose(TransactionType.EXPENSE, categories.descendantIds(categoryId),
                includePending, startDate, endDate, "t.ignoredFromBudget = false");
    }

    public BigDecimal sumIncomeGoal(UUID categoryId, LocalDate startDate, LocalDate endDate) {
        return sumByPurpose(TransactionType.INCOME, categories.descendantIds(categoryId),
                false, startDate, endDate, "t.ignoredFromReports = false");
    }

    public BigDecimal sumReport(TransactionType type, LocalDate startDate, LocalDate endDate) {
        String query = """
                select coalesce(sum(t.amount), 0)
                  from FinancialTransaction t
                 where t.deletedAt is null
                   and t.ignoredFromReports = false
                   and t.paid = true
                   and t.type = :type
                   and t.date between :startDate and :endDate
                """;
        return decimal(entityManager.createQuery(query, BigDecimal.class)
                .setParameter("type", type)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate));
    }

    private BigDecimal sumByPurpose(TransactionType type, Set<UUID> categoryIds,
            boolean includePending, LocalDate startDate, LocalDate endDate, String purposePredicate) {
        String query = """
                select coalesce(sum(t.amount), 0)
                  from FinancialTransaction t
                 where t.deletedAt is null
                   and %s
                   and t.type = :type
                   and t.category.id in :categoryIds
                   and t.date between :startDate and :endDate
                   and (:includePending = true or t.paid = true)
                """.formatted(purposePredicate);
        return decimal(entityManager.createQuery(query, BigDecimal.class)
                .setParameter("type", type)
                .setParameter("categoryIds", categoryIds)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("includePending", includePending));
    }

    private BigDecimal sumAccountSide(UUID accountId, LocalDate throughDate,
            TransactionType type, boolean destinationSide, boolean paidOnly, boolean requiresDestinationNull) {
        String accountPath = destinationSide ? "t.destinationAccount.id" : "t.account.id";
        String nullCheck = requiresDestinationNull ? " and t.destinationAccount is null " : "";
        String query = """
                select coalesce(sum(t.amount), 0)
                  from FinancialTransaction t
                 where t.deletedAt is null
                   and (:paidOnly = false or t.paid = true)
                   and t.date <= :throughDate
                   and t.type = :type
                   and %s = :accountId
                   %s
                """.formatted(accountPath, nullCheck);
        return decimal(entityManager.createQuery(query, BigDecimal.class)
                .setParameter("paidOnly", paidOnly)
                .setParameter("throughDate", throughDate)
                .setParameter("type", type)
                .setParameter("accountId", accountId));
    }

    private static void add(StringBuilder query, Map<String, Object> parameters,
            Object value, String predicate, String parameter) {
        if (value != null) {
            query.append(" and ").append(predicate);
            parameters.put(parameter, value);
        }
    }

    private static String normalizedSort(String sortBy) {
        String value = sortBy == null ? "date" : sortBy;
        if (!SORT_FIELDS.contains(value)) {
            throw new IllegalArgumentException("Unsupported sort field: " + value);
        }
        return value;
    }

    private static String normalizedDirection(String direction) {
        String value = direction == null ? "desc" : direction.toLowerCase();
        if (!SORT_DIRECTIONS.contains(value)) {
            throw new IllegalArgumentException("Unsupported sort direction: " + value);
        }
        return value;
    }

    private static BigDecimal decimal(TypedQuery<BigDecimal> query) {
        BigDecimal result = query.getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }
}
