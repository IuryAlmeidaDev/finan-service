package dev.iury.lifeos.finance.common;

import java.time.Instant;
import java.time.LocalDate;

public interface TimeProvider {

    Instant instant();

    LocalDate today();
}
