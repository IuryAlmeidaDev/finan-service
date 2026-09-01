package dev.iury.lifeos.finance.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ApiExceptionMapperTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void mapsFinanceExceptionToStableApiErrorWithUtcTimestamp() {
        FinanceException exception = new FinanceException("ACCOUNT_NOT_FOUND", 404, "Account not found");

        ApiError error = mapper.map(exception);

        assertThat(error.error()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.message()).isEqualTo("Account not found");
        assertThat(error.timestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void mapsIllegalArgumentsToBadRequestValidationError() {
        ApiError error = mapper.map(new IllegalArgumentException("Invalid period"));

        assertThat(error.error()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.message()).isEqualTo("Invalid period");
    }
}
