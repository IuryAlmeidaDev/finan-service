package dev.iury.lifeos.finance.repository;

import java.util.UUID;

import dev.iury.lifeos.finance.model.InstallmentGroup;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstallmentGroupRepository implements PanacheRepositoryBase<InstallmentGroup, UUID> {
}
