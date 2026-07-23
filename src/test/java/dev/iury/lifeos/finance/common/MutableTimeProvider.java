package dev.iury.lifeos.finance.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class MutableTimeProvider implements TimeProvider {

    private Instant current = Instant.parse("2026-07-23T12:00:00Z");

    public void set(Instant current) {
        this.current = current;
    }

    @Override
    public Instant instant() {
        return current;
    }

    @Override
    public LocalDate today() {
        return current.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
