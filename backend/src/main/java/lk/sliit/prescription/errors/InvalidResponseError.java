package lk.sliit.prescription.errors;

/** The model output is not valid JSON or fails validation after one retry. */
public class InvalidResponseError extends PrescriptionException {

    public InvalidResponseError(String message) {
        super(message);
    }

    public InvalidResponseError(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "invalid_response";
    }

}
