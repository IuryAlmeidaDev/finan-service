package dev.iury.lifeos.finance.repository;

import java.util.Optional;
import java.util.UUID;

import dev.iury.lifeos.finance.model.Tag;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TagRepository implements PanacheRepositoryBase<Tag, UUID> {
    public Optional<Tag> findByName(String name) {
        return find("lower(name) = lower(?1)", name).firstResultOptional();
    }
}
