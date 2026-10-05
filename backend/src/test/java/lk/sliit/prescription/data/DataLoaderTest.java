package lk.sliit.prescription.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Week 1 "Done when": both simulated CSVs load without errors. */
class DataLoaderTest {

    private final DataLoader data = new DataLoader();

    @Test
    void stockListLoadsAllRows() {
        assertEquals(34, data.stock().size());
        assertEquals("Panadol", data.stock().get(0).get("brand_name"));
        assertEquals("salbutamol", data.stock().get(33).get("generic_name"));
    }

    @Test
    void stockFlagsAndQuantitiesAreValid() {
        for (var row : data.stock()) {
            assertTrue(Set.of("yes", "no").contains(row.get("in_stock")), row.toString());
            int qty = Integer.parseInt(row.get("quantity_on_hand"));
            assertEquals(row.get("in_stock").equals("yes"), qty > 0, row.toString());
        }
    }

    @Test
    void interactionRulesLoadAllRows() {
        assertEquals(21, data.rules().size());
        Set<String> types = data.rules().stream().map(r -> r.get("rule_type")).collect(Collectors.toSet());
        assertEquals(Set.of("interaction", "duplicate_class", "duplicate_generic"), types);
        // The full warning text is read as a single column
        assertEquals("Increased bleeding risk. Confirm with prescriber.", data.rules().get(0).get("warning_message"));
    }
}
