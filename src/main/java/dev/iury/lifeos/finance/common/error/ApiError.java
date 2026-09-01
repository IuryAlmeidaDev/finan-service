package dev.iury.lifeos.finance.common.error;

import java.time.OffsetDateTime;

public record ApiError(String error, String message, int status, OffsetDateTime timestamp) {
}
