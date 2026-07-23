package dev.iury.lifeos.finance.repository;

import java.util.UUID;

import dev.iury.lifeos.finance.model.Account;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountRepository implements PanacheRepositoryBase<Account, UUID> {
}
