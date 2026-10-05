package lk.sliit.prescription.namecheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.model.Confidence;
import lk.sliit.prescription.model.Item;
import org.junit.jupiter.api.Test;

class NameCheckTest {

    private final NameCheck nameCheck = new NameCheck(new DataLoader());

    private static Item item(String name) {
        return Item.validated(name, "20 mg", "1 tablet", "at night (nocte)", "30 days", "high", null);
    }

    @Test
    void misspelledBrandMatchesStockList() {
        Item checked = nameCheck.check(item("Lipitr"));
        assertEquals(Confidence.HIGH, checked.confidence());
        assertTrue(checked.note().contains("Lipitor"), checked.note());
        assertTrue(checked.note().contains("atorvastatin"), checked.note());
    }

    @Test
    void genericNameMatches() {
        assertTrue(nameCheck.check(item("Amoxicillin")).note().contains("amoxicillin"));
    }

    @Test
    void unknownNameIsFlaggedLow() {
        Item checked = nameCheck.check(item("Xyzzol"));
        assertEquals(Confidence.LOW, checked.confidence());
        assertEquals(NameCheck.NOT_FOUND, checked.note());
    }

    @Test
    void missingNameIsFlaggedLow() {
        Item checked = nameCheck.check(item(null));
        assertEquals(Confidence.LOW, checked.confidence());
        assertEquals(NameCheck.NO_NAME, checked.note());
    }
}
