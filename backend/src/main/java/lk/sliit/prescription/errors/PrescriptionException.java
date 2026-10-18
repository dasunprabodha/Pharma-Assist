package lk.sliit.prescription.errors;

/** Base class for the project's expected failures. {@link #code()} is sent to the UI as error_type. */
public abstract class PrescriptionException extends RuntimeException {

    protected PrescriptionException(String message) {
        super(message);
    }

    protected PrescriptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String code();

    /** Whether the UI should switch to manual entry automatically. */
    public boolean fallbackToManual() {
        return true;
    }
}
