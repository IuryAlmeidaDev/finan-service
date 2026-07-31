package dev.iury.lifeos.finance.category;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import dev.iury.lifeos.finance.model.Category;
import dev.iury.lifeos.finance.model.CategoryType;
import dev.iury.lifeos.finance.repository.CategoryRepository;
import dev.iury.lifeos.finance.repository.TransactionRepository;

@ApplicationScoped
public class CategoryService {

    @Inject CategoryRepository categories;
    @Inject TransactionRepository transactions;

    @Transactional
    public Category create(String name, CategoryType type, UUID parentId, String iconSlug, String color) {
        Category category = new Category();
        category.name = name;
        category.iconSlug = iconSlug;
        category.color = color;
        category.system = false;
        category.archived = false;
        category.sortOrder = 0;

        if (parentId != null) {
            Category parent = categories.findByIdOptional(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            
            if (parent.parentCategory != null) {
                throw new IllegalArgumentException("Maximum depth of 2 levels exceeded");
            }
            
            category.parentCategory = parent;
            category.type = parent.type; // inherits from parent
        } else {
            category.type = type;
        }

        categories.persist(category);
        return category;
    }

    @Transactional
    public void update(UUID id, String name, String iconSlug, String color, Integer sortOrder) {
        Category cat = categories.findByIdOptional(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            
        if (cat.system) {
            throw new IllegalStateException("Cannot modify system category");
        }
        
        if (name != null && !name.isBlank()) {
            cat.name = name;
        }
        if (iconSlug != null) {
            cat.iconSlug = iconSlug;
        }
        if (color != null) {
            cat.color = color;
        }
        if (sortOrder != null) {
            cat.sortOrder = sortOrder;
        }
    }

    @Transactional
    public void delete(UUID id) {
        Category cat = categories.findByIdOptional(id)
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            
        if (cat.system) {
            throw new IllegalStateException("Cannot delete system category");
        }
        
        long subcategoryCount = categories.count("parentCategory.id", id);
        if (subcategoryCount > 0) {
            throw new IllegalStateException("Cannot delete category with subcategories");
        }
        
        long transactionCount = transactions.count("category.id", id);
        if (transactionCount > 0) {
            throw new IllegalStateException("Cannot delete category with transactions");
        }
        
        categories.delete(cat);
    }

    @Transactional
    public long migrate(UUID oldCategoryId, UUID newCategoryId) {
        Category oldCat = categories.findByIdOptional(oldCategoryId)
            .orElseThrow(() -> new IllegalArgumentException("Old category not found"));
            
        Category newCat = categories.findByIdOptional(newCategoryId)
            .orElseThrow(() -> new IllegalArgumentException("New category not found"));
            
        if (oldCat.type != newCat.type) {
            throw new IllegalArgumentException("Cannot migrate to a category of a different type");
        }
        
        long updated = transactions.update("category = ?1 where category.id = ?2", newCat, oldCat.id);
        
        oldCat.archived = true;
        
        return updated;
    }
}
