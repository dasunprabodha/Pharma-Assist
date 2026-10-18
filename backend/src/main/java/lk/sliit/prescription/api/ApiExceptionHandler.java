package lk.sliit.prescription.api;

import java.util.LinkedHashMap;
import java.util.Map;
import lk.sliit.prescription.errors.AIUnavailableError;
import lk.sliit.prescription.errors.ConfigError;
import lk.sliit.prescription.errors.DataError;
import lk.sliit.prescription.errors.InvalidImageError;
import lk.sliit.prescription.errors.InvalidResponseError;
import lk.sliit.prescription.errors.PrescriptionException;
import lk.sliit.prescription.errors.UnreadableImageError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Turns the project's exceptions into JSON: {error_type, message, detail, fallback_to_manual}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PrescriptionException.class)
    public ResponseEntity<Map<String, Object>> handle(PrescriptionException e) {
        HttpStatus status;
        if (e instanceof InvalidImageError) {
            status = HttpStatus.BAD_REQUEST;
        } else if (e instanceof UnreadableImageError) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        } else if (e instanceof InvalidResponseError) {
            status = HttpStatus.BAD_GATEWAY;
        } else if (e instanceof ConfigError || e instanceof AIUnavailableError || e instanceof DataError) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // Only the error type and message are logged - never image bytes or credentials.
        LOG.warn("Scan failed: {} - {}", e.code(), e.getMessage());
        return ResponseEntity.status(status).body(body(e.code(), userMessage(e), e.getMessage(), e.fallbackToManual()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(body("invalid_image",
                "The file is larger than 10 MB. Please use a smaller photo.", null, false));
    }

    /** The user-facing messages from CLAUDE.md 6.5. */
    static String userMessage(PrescriptionException e) {
        if (e instanceof DataError) {
            return "Stock/rule data unavailable – the dispensing check cannot run";
        } else if (e instanceof ConfigError) {
            return "AI not configured (AWS credentials or Bedrock model access) – using manual entry";
        } else if (e instanceof AIUnavailableError) {
            return "AI service unavailable – using manual entry";
        } else if (e instanceof UnreadableImageError) {
            return "Could not read this photo – please retake it, or enter the items manually";
        } else if (e instanceof InvalidResponseError) {
            return "AI returned an unexpected result – using manual entry";
        }
        return e.getMessage();
    }

    private static Map<String, Object> body(String type, String message, String detail, boolean fallback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_type", type);
        body.put("message", message);
        body.put("detail", detail);
        body.put("fallback_to_manual", fallback);
        return body;
    }
}
