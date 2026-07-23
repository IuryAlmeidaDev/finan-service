package dev.iury.lifeos.finance.repository;

import java.util.List;
import java.util.UUID;

import dev.iury.lifeos.finance.model.RecurringRule;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RecurringRuleRepository implements PanacheRepositoryBase<RecurringRule, UUID> {
    public List<RecurringRule> findActive() {
        return list("active", true);
    }
}
