package lk.sliit.prescription.namecheck;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
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
    public record Suggestion(String itemId, String name, String genericName, String strength, boolean inStock,
                             int score) {
    }

    /** The stock row a drug name resolves to, with its WRatio score (0-100). */
    public record Match(Map<String, String> row, int score) {
    }

    private final List<String> names = new ArrayList<>();
    private final List<Map<String, String>> rowForName = new ArrayList<>();
    /** Display form of each entry in names, e.g. "Lipitor", "Atorvastatin". */
    private final List<String> displayNames = new ArrayList<>();

    /** The stock list is read on first use, so a missing CSV raises DataError there instead of at startup. */
    private final Supplier<List<Map<String, String>>> stockSource;
    private volatile boolean indexed;

    @Autowired
    public NameCheck(DataLoader data) {
        this.stockSource = data::stock;
    }

    public NameCheck(List<Map<String, String>> stock) {
        this.stockSource = () -> stock;
    }

    private synchronized void ensureIndex() {
        if (indexed) {
            return;
        }
        for (Map<String, String> row : stockSource.get()) {
            add(row.get("brand_name"), row);
            add(row.get("generic_name"), row);
        }
        indexed = true;
    }

    /**
     * Resolves a drug name to a stock row (WRatio ≥ {@value #MATCH_THRESHOLD}), or empty. When several names score
     * equally (e.g. "Atorvastatin" is both Lipitor's generic and the generic product), the row whose strength
     * matches the prescribed strength wins, then an in-stock row.
     */
    public Optional<Match> match(String drugName, String prescribedStrength) {
        if (drugName == null || drugName.isBlank()) {
            return Optional.empty();
        }
        ensureIndex();
        List<ExtractedResult> results = FuzzySearch.extractSorted(drugName.toLowerCase(), names);
        if (results.isEmpty() || results.get(0).getScore() < MATCH_THRESHOLD) {
            return Optional.empty();
        }
        int best = results.get(0).getScore();
        String strength = firstNumber(prescribedStrength);
        Comparator<Map<String, String>> preference = Comparator
                .comparing((Map<String, String> r) -> strength != null && strength.equals(firstNumber(r.get("strength"))))
                .thenComparing(r -> "yes".equalsIgnoreCase(r.get("in_stock")));
        return results.stream()
                .filter(r -> r.getScore() == best)
                .map(r -> rowForName.get(r.getIndex()))
                .max(preference)
                .map(row -> new Match(row, best));
    }

    public Optional<Match> match(String drugName) {
        return match(drugName, null);
    }

    static String firstNumber(String s) {
        if (s == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(s);
        return m.find() ? m.group() : null;
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
        Optional<Match> match = match(item.drugName(), item.strength());
        if (match.isEmpty()) {
            return item.withConfidence(Confidence.LOW).withNote(NOT_FOUND);
        }
        Map<String, String> row = match.get().row();
        return item.withNote("Stock list: " + row.get("brand_name") + " (" + row.get("generic_name") + " "
                + row.get("strength") + ") – match " + match.get().score() + "%");
    }

    /**
     * Up to three distinct stock-list names that resemble the given drug name, best first.
     * Used for the clickable "Did you mean…?" chips; the pharmacist decides whether to apply one.
     */
    public List<Suggestion> suggest(String drugName) {
        if (drugName == null || drugName.isBlank()) {
            return List.of();
        }
        ensureIndex();
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ExtractedResult r : FuzzySearch.extractSorted(drugName.toLowerCase(), names)) {
            if (r.getScore() < SUGGEST_THRESHOLD || out.size() == MAX_SUGGESTIONS) {
                break;
            }
            String name = displayNames.get(r.getIndex());
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                Map<String, String> row = rowForName.get(r.getIndex());
                out.add(new Suggestion(row.get("item_id"), name, row.get("generic_name"), row.get("strength"),
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
