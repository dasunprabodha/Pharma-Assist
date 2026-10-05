package lk.sliit.prescription.model;

import java.util.stream.Stream;

/**
 * One prescription item - the shared structure produced by both the AI path and manual entry
 * (CLAUDE.md section 4). JSON field names are snake_case: drug_name, strength, dose, frequency,
 * duration, confidence, note.
 */
public record Item(
        String drugName,
        String strength,
        String dose,
        String frequency,
        String duration,
        Confidence confidence,
        String note) {

    public Item {
        confidence = confidence == null ? Confidence.LOW : confidence;
    }

    /**
     * Applies the section 4 rules: blank fields become null, any null field makes the item LOW,
     * and an invalid or missing confidence becomes LOW. Items without a drug name are kept, never dropped.
     */
    public static Item validated(String drugName, String strength, String dose, String frequency,
                                 String duration, Object confidence, String note) {
        Item item = new Item(clean(drugName), clean(strength), clean(dose), clean(frequency), clean(duration),
                Confidence.normalise(confidence), clean(note));
        return item.hasMissingField() ? item.withConfidence(Confidence.LOW) : item;
    }

    public boolean hasMissingField() {
        return Stream.of(drugName, strength, dose, frequency, duration).anyMatch(v -> v == null);
    }

    public Item withConfidence(Confidence newConfidence) {
        return new Item(drugName, strength, dose, frequency, duration, newConfidence, note);
    }

    public Item withNote(String newNote) {
        return new Item(drugName, strength, dose, frequency, duration, confidence, newNote);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") ? null : trimmed;
    }
}
