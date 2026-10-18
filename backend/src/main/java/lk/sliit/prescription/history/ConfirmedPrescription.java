package lk.sliit.prescription.history;

import java.time.Instant;
import java.util.List;
import lk.sliit.prescription.model.Item;

/**
 * A pharmacist-confirmed list - the input to Feature 2 (Week 3). Holds drug items only; no patient details.
 *
 * @param source      "ai" (AI-extracted, then reviewed) or "manual" (manual entry)
 * @param corrections number of cells the pharmacist changed in the AI result (0 for manual entry)
 */
public record ConfirmedPrescription(
        String id,
        Instant confirmedAt,
        String source,
        String scanId,
        List<Item> items,
        int corrections) {
}
