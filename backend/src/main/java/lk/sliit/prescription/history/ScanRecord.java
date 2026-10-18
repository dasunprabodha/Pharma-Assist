package lk.sliit.prescription.history;

import java.time.Instant;

/** One line of logs/usage.csv: a scan attempt with its outcome and token usage. No image or patient data. */
public record ScanRecord(
        Instant timestamp,
        String scanId,
        String modelId,
        String outcome,
        int itemCount,
        int lowConfidenceCount,
        int inputTokens,
        int outputTokens,
        long latencyMs) {
}
