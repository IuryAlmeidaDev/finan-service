package dev.iury.lifeos.finance.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
@EntityListeners(TimestampEntityListener.class)
public abstract class CreatedEntity {

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
