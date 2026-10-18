package lk.sliit.prescription.tools;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lk.sliit.prescription.config.AppConfig;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;

/** Shared helpers for the one-off command-line tools. */
final class ToolSupport {

    private ToolSupport() {
    }

    /** Outside Spring, Logback defaults to DEBUG; keep the console readable. */
    static void quietLogging() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    }

    static void printConfig() {
        System.out.println("Region:   " + AppConfig.region());
        System.out.println("Profile:  " + AppConfig.profile().orElse("(default credentials chain)"));
        System.out.println("Model ID: " + AppConfig.modelId());
    }

    /** Prints the exact AWS error code and message, plus a hint for the common setup problems. */
    static void printError(RuntimeException e) {
        if (e instanceof BedrockRuntimeException bre && bre.awsErrorDetails() != null) {
            String code = bre.awsErrorDetails().errorCode();
            System.out.println("ERROR " + code + " (HTTP " + bre.statusCode() + "): " + bre.awsErrorDetails().errorMessage());
            switch (String.valueOf(code)) {
                case "AccessDeniedException" -> System.out.println(
                        "Hint: enable model access in Bedrock, and check the IAM policy allows bedrock:InvokeModel.");
                case "ValidationException" -> System.out.println(
                        "Hint: check BEDROCK_MODEL_ID is a valid model / inference profile ID for " + AppConfig.region() + ".");
                case "ThrottlingException" -> System.out.println("Hint: rate limit or quota reached; wait and retry.");
                default -> { }
            }
        } else if (e instanceof SdkClientException) {
            System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("Hint: run `aws configure --profile prescription-reader` and set AWS_PROFILE in .env.");
        } else {
            System.out.println("ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
