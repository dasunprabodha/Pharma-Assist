package lk.sliit.prescription.errors;

/** Missing AWS credentials or region, AccessDeniedException (model access or IAM), or an invalid model ID. */
public class ConfigError extends PrescriptionException {

    public ConfigError(String message) {
        super(message);
    }

    public ConfigError(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "config_error";
    }

}
