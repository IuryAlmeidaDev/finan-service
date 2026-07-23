package dev.iury.lifeos.finance.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, length = 80)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public CategoryType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    public Category parentCategory;

    @Column(name = "icon_slug", length = 80)
    public String iconSlug;

    @Column(length = 7)
    public String color;

    @Column(name = "is_system", nullable = false)
    public boolean system;

    @Column(name = "is_archived", nullable = false)
    public boolean archived;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

}
