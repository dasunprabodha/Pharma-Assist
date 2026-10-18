package lk.sliit.prescription.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.errors.DataError;
import lk.sliit.prescription.model.Item;
import lk.sliit.prescription.namecheck.NameCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CLAUDE.md Week 3 - tests/test_checker.py, using the simulated CSVs in data/. */
class CheckerTest {

    private final DataLoader data = new DataLoader();
    private final Checker checker = new Checker(data, new NameCheck(data));

    private static Item item(String name, String strength) {
        return Item.validated(name, strength, "1 tablet", "twice daily (bd)", "5 days", "high", null);
    }

    private Checklist check(Item... items) {
        return checker.check(Arrays.asList(items));
    }

    private static boolean hasWarning(Checklist c, String ruleId, String severity) {
        return c.warnings().stream().anyMatch(w -> ruleId.equals(w.ruleId()) && severity.equals(w.severity()));
    }

    @Test
    void lipitorIsOutOfStockWithGenericAlternative() {
        Checklist.Row row = check(item("Lipitor", "20 mg")).rows().get(0);
        assertEquals("out_of_stock", row.status());
        assertEquals("Lipitor", row.matchedProduct());
        assertEquals("Atorvastatin (generic)", row.alternatives().get(0).brandName());
        assertTrue(row.alternatives().get(0).sameStrength());
        assertEquals("/api/stock/S025/image", row.alternatives().get(0).imageUrl());
    }

    @Test
    void warfarinWithDisprinIsMajorInteraction() {
        Checklist c = check(item("Warfarin", "5 mg"), item("Disprin", "300 mg"));
        assertTrue(hasWarning(c, "R001", "major"), c.warnings().toString());
        assertEquals("major", c.warnings().get(0).severity());
    }

    @Test
    void brufenWithVoltarenIsDuplicateNsaid() {
        assertTrue(hasWarning(check(item("Brufen", "400 mg"), item("Voltaren", "50 mg")), "R017", "moderate"));
    }

    @Test
    void panadolWithGenericParacetamolIsDuplicateGeneric() {
        Checklist c = check(item("Panadol", "500 mg"), item("Paracetamol (generic)", "500 mg"));
        assertTrue(hasWarning(c, "R021", "moderate"), c.warnings().toString());
    }

    @Test
    void atorvastatinWithKlacidIsModerateInteractionInEitherOrder() {
        assertTrue(hasWarning(check(item("Atorvastatin", "20 mg"), item("Klacid", "500 mg")), "R008", "moderate"));
        assertTrue(hasWarning(check(item("Klacid", "500 mg"), item("Atorvastatin", "20 mg")), "R008", "moderate"));
    }

    @Test
    void genericNamePrefersTheInStockProduct() {
        Checklist.Row row = check(item("Atorvastatin", "20 mg")).rows().get(0);
        assertEquals("in_stock", row.status());
        assertEquals("S025", row.itemId());
    }

    @Test
    void unknownDrugIsFlaggedWithoutCrashing() {
        Checklist c = check(item("Xyzzol", "10 mg"), item("Warfarin", "5 mg"));
        assertEquals("not_in_list", c.rows().get(0).status());
        assertEquals(Checker.NOT_IN_LIST, c.rows().get(0).notes().get(0));
        assertTrue(c.warnings().stream().anyMatch(w -> "not_in_stock_list".equals(w.ruleType()) && w.rows().equals(List.of(1))));
    }

    @Test
    void emptyListGivesEmptyChecklist() {
        Checklist c = checker.check(List.of());
        assertTrue(c.rows().isEmpty());
        assertTrue(c.warnings().isEmpty());
    }

    @Test
    void differentStrengthIsNoted() {
        Checklist.Row row = check(item("Atorvastatin", "10 mg")).rows().get(0);
        assertTrue(row.notes().stream().anyMatch(n -> n.contains("check the strength")), row.notes().toString());
    }

    @Test
    void outOfStockBrandOffersInStockGeneric() {
        Checklist.Row row = check(item("Zithromax", "500 mg")).rows().get(0);
        assertEquals("Azithromycin (generic)", row.alternatives().get(0).brandName());
    }

    @Test
    void outOfStockWithoutAlternativeSaysOrderOrRefer(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock_list.csv"), String.join(",", DataLoader.STOCK_COLUMNS) + "\n"
                + "S900,Cozaar,losartan,50 mg,tablet,antihypertensive - ARB,no,0\n");
        Files.copy(Path.of("../data/interaction_rules.csv"), dir.resolve("interaction_rules.csv"));
        DataLoader small = new DataLoader(dir);
        Checklist.Row row = new Checker(small, new NameCheck(small)).check(List.of(item("Cozaar", "50 mg"))).rows().get(0);
        assertTrue(row.alternatives().isEmpty());
        assertTrue(row.notes().contains(Checker.NO_ALTERNATIVE), row.notes().toString());
    }

    @Test
    void missingCsvRaisesDataError(@TempDir Path empty) {
        DataLoader missing = new DataLoader(empty);
        Checker broken = new Checker(missing, new NameCheck(missing));
        assertThrows(DataError.class, () -> broken.check(List.of(item("Panadol", "500 mg"))));
    }

    @Test
    void malformedCsvRaisesDataError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stock_list.csv"), "item_id,brand_name\nS001,Panadol\n");
        Files.copy(Path.of("../data/interaction_rules.csv"), dir.resolve("interaction_rules.csv"));
        assertThrows(DataError.class, () -> new DataLoader(dir).stock());
    }
}
