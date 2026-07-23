package dev.iury.lifeos.finance.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class UpdatedEntity extends CreatedEntity {

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
