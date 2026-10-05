package lk.sliit.prescription.service;

import java.util.List;
import lk.sliit.prescription.model.Item;

/** Response of a successful scan. */
public record ScanResult(
        String scanId,
        String modelId,
        int pages,
        List<Item> items,
        List<Integer> lowConfidenceRows,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        double estimatedCostUsd) {
}
