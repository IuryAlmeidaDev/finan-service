package dev.iury.lifeos.finance.recurring;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command object carrying the fields that can be updated on a recurring occurrence or rule.
 * Null fields are left unchanged.
 */
public record RecurringUpdateCommand(
        BigDecimal amount,
        String description,
        UUID categoryId,
        Boolean paid) {
}
