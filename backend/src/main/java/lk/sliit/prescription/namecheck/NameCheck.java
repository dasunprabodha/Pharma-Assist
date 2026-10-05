package lk.sliit.prescription.namecheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.model.Confidence;
import lk.sliit.prescription.model.Item;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fuzzy-matches each drug name against the brand and generic names in the (simulated) stock list
 * (CLAUDE.md 6.4). A flagging aid only - it does not perform the Week 3 stock or interaction check.
 */
@Component
public class NameCheck {

    public static final int MATCH_THRESHOLD = 85;
    public static final String NOT_FOUND = "Not found in stock list – check carefully";
    public static final String NO_NAME = "Drug name unreadable – check carefully";

    private final List<String> names = new ArrayList<>();
    private final List<Map<String, String>> rowForName = new ArrayList<>();

    @Autowired
    public NameCheck(DataLoader data) {
        this(data.stock());
    }

    public NameCheck(List<Map<String, String>> stock) {
        for (Map<String, String> row : stock) {
            add(row.get("brand_name"), row);
            add(row.get("generic_name"), row);
        }
    }

    private void add(String name, Map<String, String> row) {
        if (name != null && !name.isBlank()) {
            names.add(name.toLowerCase());
            rowForName.add(row);
        }
    }

    public List<Item> check(List<Item> items) {
        return items.stream().map(this::check).toList();
    }

    public Item check(Item item) {
        if (item.drugName() == null || item.drugName().isBlank()) {
            return item.withConfidence(Confidence.LOW).withNote(NO_NAME);
        }
        ExtractedResult best = FuzzySearch.extractOne(item.drugName().toLowerCase(), names);
        if (best == null || best.getScore() < MATCH_THRESHOLD) {
            return item.withConfidence(Confidence.LOW).withNote(NOT_FOUND);
        }
        Map<String, String> row = rowForName.get(best.getIndex());
        return item.withNote("Stock list: " + row.get("brand_name") + " (" + row.get("generic_name") + " "
                + row.get("strength") + ") – match " + best.getScore() + "%");
    }
}
