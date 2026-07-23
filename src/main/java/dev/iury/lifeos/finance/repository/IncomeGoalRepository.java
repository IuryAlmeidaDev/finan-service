package dev.iury.lifeos.finance.repository;

import java.util.Optional;
import java.util.UUID;

import dev.iury.lifeos.finance.model.IncomeGoal;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IncomeGoalRepository implements PanacheRepositoryBase<IncomeGoal, UUID> {
    public Optional<IncomeGoal> findByCategoryAndPeriod(UUID categoryId, int year, int month) {
        return find("category.id = ?1 and year = ?2 and month = ?3", categoryId, year, month)
                .firstResultOptional();
    }
}
