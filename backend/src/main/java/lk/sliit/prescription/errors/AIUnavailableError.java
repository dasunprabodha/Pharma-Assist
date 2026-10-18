package lk.sliit.prescription.errors;

/** Network error, timeout, quota or rate limit, or a Bedrock server error. */
public class AIUnavailableError extends PrescriptionException {

    public AIUnavailableError(String message) {
        super(message);
    }

    public AIUnavailableError(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "ai_unavailable";
    }

}
