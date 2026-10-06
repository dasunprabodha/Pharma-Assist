package lk.sliit.prescription.namecheck;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    /** Lower bar than MATCH_THRESHOLD: suggestions are only offered, never applied automatically. */
    public static final int SUGGEST_THRESHOLD = 60;
    public static final int MAX_SUGGESTIONS = 3;

    /** A stock-list name the pharmacist can pick for a drug name ("Did you mean…?"). */
    public record Suggestion(String name, String genericName, String strength, boolean inStock, int score) {
    }

    private final List<String> names = new ArrayList<>();
    private final List<Map<String, String>> rowForName = new ArrayList<>();
    /** Display form of each entry in names, e.g. "Lipitor", "Atorvastatin". */
    private final List<String> displayNames = new ArrayList<>();

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
            displayNames.add(displayName(name));
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

    /**
     * Up to three distinct stock-list names that resemble the given drug name, best first.
     * Used for the clickable "Did you mean…?" chips; the pharmacist decides whether to apply one.
     */
    public List<Suggestion> suggest(String drugName) {
        if (drugName == null || drugName.isBlank()) {
            return List.of();
        }
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ExtractedResult r : FuzzySearch.extractSorted(drugName.toLowerCase(), names)) {
            if (r.getScore() < SUGGEST_THRESHOLD || out.size() == MAX_SUGGESTIONS) {
                break;
            }
            String name = displayNames.get(r.getIndex());
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                Map<String, String> row = rowForName.get(r.getIndex());
                out.add(new Suggestion(name, row.get("generic_name"), row.get("strength"),
                        "yes".equalsIgnoreCase(row.get("in_stock")), r.getScore()));
            }
        }
        return out;
    }

    /** "Atorvastatin (generic)" -> "Atorvastatin"; "amoxicillin" -> "Amoxicillin". */
    private static String displayName(String name) {
        String n = name.replaceFirst("(?i)\\s*\\(generic\\)$", "").trim();
        return n.isEmpty() ? name : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
