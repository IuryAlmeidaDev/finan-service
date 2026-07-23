package dev.iury.lifeos.finance.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

public class TransactionFilter {
    public UUID accountId;
    public Collection<UUID> accountIds;
    public UUID categoryId;
    public boolean includeCategoryDescendants;
    public Boolean paid;
    public LocalDate startDate;
    public LocalDate endDate;
    public BigDecimal minAmount;
    public BigDecimal maxAmount;
    public String search;
    public UUID tagId;
    public UUID installmentGroupId;
    public UUID recurringRuleId;
    public String sortBy = "date";
    public String sortDirection = "desc";

    public LinkedHashSet<UUID> normalizedAccountIds() {
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        if (accountId != null) {
            normalized.add(accountId);
        }
        if (accountIds != null) {
            normalized.addAll(accountIds);
        }
        return normalized;
    }
}
