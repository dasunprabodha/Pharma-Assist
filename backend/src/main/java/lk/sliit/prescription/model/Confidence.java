package lk.sliit.prescription.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Item confidence. Serialised as "high" | "medium" | "low"; anything else normalises to LOW. */
public enum Confidence {
    HIGH, MEDIUM, LOW;

    @JsonCreator
    public static Confidence normalise(Object value) {
        if (value == null) {
            return LOW;
        }
        return switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
            case "high" -> HIGH;
            case "medium" -> MEDIUM;
            default -> LOW;
        };
    }

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
