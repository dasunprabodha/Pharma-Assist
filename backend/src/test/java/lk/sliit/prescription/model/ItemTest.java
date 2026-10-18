package lk.sliit.prescription.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

class ItemTest {

    @Test
    void invalidConfidenceNormalisesToLow() {
        assertEquals(Confidence.LOW, Confidence.normalise("very sure"));
        assertEquals(Confidence.LOW, Confidence.normalise(null));
        assertEquals(Confidence.HIGH, Confidence.normalise(" High "));
        assertEquals(Confidence.MEDIUM, Confidence.normalise("medium"));
    }

    @Test
    void completeItemKeepsItsConfidence() {
        Item item = Item.validated("Lipitor", "20 mg", "1 tablet", "at night (nocte)", "30 days", "high", null);
        assertEquals(Confidence.HIGH, item.confidence());
    }

    @Test
    void nullOrBlankFieldMakesItemLow() {
        Item item = Item.validated("Lipitor", "20 mg", "  ", "at night (nocte)", "30 days", "high", null);
        assertNull(item.dose());
        assertEquals(Confidence.LOW, item.confidence());
    }

    @Test
    void itemWithoutDrugNameIsKeptAsLow() {
        Item item = Item.validated(null, "500 mg", "1 tablet", "twice daily (bd)", "5 days", "high", null);
        assertNull(item.drugName());
        assertEquals(Confidence.LOW, item.confidence());
    }

    @Test
    void jsonUsesSnakeCaseAndLowercaseConfidence() throws Exception {
        ObjectMapper json = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        String out = json.writeValueAsString(
                Item.validated("Klacid", "500 mg", "1 tablet", "twice daily (bd)", "5 days", "medium", null));
        assertTrue(out.contains("\"drug_name\":\"Klacid\""), out);
        assertTrue(out.contains("\"confidence\":\"medium\""), out);

        Item back = json.readValue("{\"drug_name\":\"Panadol\",\"confidence\":\"bogus\"}", Item.class);
        assertEquals("Panadol", back.drugName());
        assertEquals(Confidence.LOW, back.confidence());
    }
}
