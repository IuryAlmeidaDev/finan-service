package dev.iury.lifeos.finance.repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Category;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryRepository implements PanacheRepositoryBase<Category, UUID> {
    public Set<UUID> descendantIds(UUID categoryId) {
        List<Category> categories = listAll();
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        result.add(categoryId);
        boolean changed;
        do {
            changed = false;
            for (Category category : categories) {
                if (category.parentCategory != null
                        && result.contains(category.parentCategory.id)
                        && result.add(category.id)) {
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }
}
