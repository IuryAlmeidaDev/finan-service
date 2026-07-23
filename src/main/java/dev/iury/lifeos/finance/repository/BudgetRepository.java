package dev.iury.lifeos.finance.repository;

import java.util.Optional;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Budget;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BudgetRepository implements PanacheRepositoryBase<Budget, UUID> {
    public Optional<Budget> findByCategoryAndPeriod(UUID categoryId, int year, int month) {
        return find("category.id = ?1 and year = ?2 and month = ?3", categoryId, year, month)
                .firstResultOptional();
    }
}
