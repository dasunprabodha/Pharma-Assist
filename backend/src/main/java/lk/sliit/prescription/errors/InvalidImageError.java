package lk.sliit.prescription.errors;

/** Wrong file type, file too large, or a corrupt image. The user should choose another file. */
public class InvalidImageError extends PrescriptionException {

    public InvalidImageError(String message) {
        super(message);
    }

    public InvalidImageError(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "invalid_image";
    }

    @Override
    public boolean fallbackToManual() {
        return false;
    }
}
