package lk.sliit.prescription.checker;

import java.time.Instant;
import java.util.List;

/**
 * Feature 2 output: one row per confirmed item plus warnings sorted major → moderate → minor.
 * Contains drug items only - no patient details.
 */
public record Checklist(Instant checkedAt, List<Row> rows, List<Warning> warnings) {

    /**
     * @param status "in_stock", "out_of_stock" or "not_in_list"
     */
    public record Row(
            int index,
            String prescribed,
            String dose,
            String frequency,
            String duration,
            String itemId,
            String matchedProduct,
            String genericName,
            String therapeuticClass,
            String stockStrength,
            String dosageForm,
            String status,
            Integer quantityOnHand,
            Integer matchScore,
            List<Alternative> alternatives,
            List<String> notes,
            String imageUrl) {
    }

    public record Alternative(String itemId, String brandName, String genericName, String strength,
                              int quantityOnHand, boolean sameStrength, String imageUrl) {
    }

    /**
     * @param ruleType "interaction", "duplicate_class", "duplicate_generic" or "not_in_stock_list"
     * @param rows     1-based checklist rows involved
     */
    public record Warning(String severity, String ruleId, String ruleType, List<String> items, List<Integer> rows,
                          String message) {
    }
}
