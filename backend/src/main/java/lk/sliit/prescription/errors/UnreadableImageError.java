package lk.sliit.prescription.errors;

/** The model reports that the photo contains no readable prescription. */
public class UnreadableImageError extends PrescriptionException {

    public UnreadableImageError(String message) {
        super(message);
    }

    public UnreadableImageError(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "unreadable_image";
    }

}
