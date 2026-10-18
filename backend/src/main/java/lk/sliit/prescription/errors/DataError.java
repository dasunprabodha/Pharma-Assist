package lk.sliit.prescription.errors;

/** The simulated stock list or interaction rules are missing or malformed. The confirmed list is still shown. */
public class DataError extends PrescriptionException {

    public DataError(String message) {
        super(message);
    }

    public DataError(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "data_error";
    }

    @Override
    public boolean fallbackToManual() {
        return false;
    }
}
