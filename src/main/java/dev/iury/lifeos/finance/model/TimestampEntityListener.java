package dev.iury.lifeos.finance.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import dev.iury.lifeos.finance.common.TimeProvider;
import jakarta.inject.Inject;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class TimestampEntityListener {

    @Inject
    TimeProvider timeProvider;

    @PrePersist
    public void create(CreatedEntity entity) {
        LocalDateTime now = now();
        entity.createdAt = now;
        if (entity instanceof UpdatedEntity updatedEntity) {
            updatedEntity.updatedAt = now;
        }
    }

    @PreUpdate
    public void update(CreatedEntity entity) {
        if (entity instanceof UpdatedEntity updatedEntity) {
            updatedEntity.updatedAt = now();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(timeProvider.instant(), ZoneOffset.UTC);
    }
}
