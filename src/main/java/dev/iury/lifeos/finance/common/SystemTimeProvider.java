package dev.iury.lifeos.finance.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemTimeProvider implements TimeProvider {

    private final Clock clock;

    public SystemTimeProvider() {
        this(Clock.systemDefaultZone());
    }

    SystemTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant instant() {
        return clock.instant();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
