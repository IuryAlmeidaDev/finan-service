package dev.iury.lifeos.finance.common.error;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        ApiError error = map(exception);
        return Response.status(error.status()).entity(error).build();
    }

    public ApiError map(RuntimeException exception) {
        if (exception instanceof FinanceException financeException) {
            return error(financeException.code(), financeException.status(), financeException.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return error("VALIDATION_ERROR", 400, exception.getMessage());
        }
        if (exception instanceof IllegalStateException) {
            return error("CONFLICT", 409, exception.getMessage());
        }
        return error("INTERNAL_ERROR", 500, "Unexpected internal error");
    }

    private static ApiError error(String code, int status, String message) {
        return new ApiError(code, message, status, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
