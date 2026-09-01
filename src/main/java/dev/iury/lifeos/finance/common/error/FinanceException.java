package dev.iury.lifeos.finance.common.error;

public class FinanceException extends RuntimeException {

    private final String code;
    private final int status;

    public FinanceException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
