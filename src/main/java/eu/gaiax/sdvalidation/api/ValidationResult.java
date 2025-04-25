package eu.gaiax.sdvalidation.api;

public class ValidationResult {
    private boolean valid;
    private String reason;
    private Object payload;

    public ValidationResult(boolean valid, String reason) {
        this.valid = valid;
        this.reason = reason;
    }

    public ValidationResult(boolean valid, String reason, Object payload) {
        this.valid = valid;
        this.reason = reason;
        this.payload = payload;
    }

    public boolean isValid() {
        return valid;
    }

    public String getReason() {
        return reason;
    }

    public Object getPayload() {
        return payload;
    }
}
