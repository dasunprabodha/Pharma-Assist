package lk.sliit.prescription.checker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.model.Item;
import lk.sliit.prescription.namecheck.NameCheck;
import org.springframework.stereotype.Component;

/**
 * Feature 2 (CLAUDE.md Week 3): checks a confirmed list - from the AI path or manual entry - against the SIMULATED
 * stock list and interaction rules, and builds a dispensing checklist. Decision support only; not a clinical database.
 */
@Component
public class Checker {

    public static final String NOT_IN_LIST = "Not in stock list – check manually";
    public static final String NO_ALTERNATIVE = "No in-stock alternative – order or refer";
    private static final List<String> SEVERITY_ORDER = List.of("major", "moderate", "minor");

    private final DataLoader data;
    private final NameCheck names;

    public Checker(DataLoader data, NameCheck names) {
        this.data = data;
        this.names = names;
    }

    private record Resolved(int row, String display, String generic, String therapeuticClass, String itemId) {
    }

    /** @throws lk.sliit.prescription.errors.DataError if the stock list or rules are missing or malformed */
    public Checklist check(List<Item> items) {
        List<Map<String, String>> stock = data.stock();
        List<Map<String, String>> rules = data.rules();
        List<Checklist.Row> rows = new ArrayList<>();
        List<Checklist.Warning> warnings = new ArrayList<>();
        List<Resolved> resolved = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int index = i + 1;
            String prescribed = join(item.drugName(), item.strength());
            Optional<NameCheck.Match> match = names.match(item.drugName(), item.strength());
            if (match.isEmpty()) {
                rows.add(new Checklist.Row(index, prescribed, item.dose(), item.frequency(), item.duration(), null, null,
                        null, null, null, null, "not_in_list", null, null, List.of(), List.of(NOT_IN_LIST), null));
                String name = item.drugName() == null ? "Row " + index : item.drugName();
                warnings.add(new Checklist.Warning("moderate", null, "not_in_stock_list", List.of(name), List.of(index),
                        "\"" + name + "\" is not in the stock list, so it was not checked for interactions or duplicates – check manually."));
                continue;
            }
            Map<String, String> product = match.get().row();
            boolean inStock = "yes".equalsIgnoreCase(product.get("in_stock"));
            List<String> notes = new ArrayList<>();
            String prescribedNumber = firstNumber(item.strength());
            String stockNumber = firstNumber(product.get("strength"));
            if (prescribedNumber != null && stockNumber != null && !prescribedNumber.equals(stockNumber)) {
                notes.add("Prescribed " + item.strength() + "; stock item is " + product.get("strength") + " – check the strength");
            }
            List<Checklist.Alternative> alternatives = List.of();
            if (!inStock) {
                alternatives = alternatives(stock, product);
                notes.add(alternatives.isEmpty() ? NO_ALTERNATIVE
                        : "Out of stock – in-stock alternative with the same active ingredient listed");
            }
            rows.add(new Checklist.Row(index, prescribed, item.dose(), item.frequency(), item.duration(),
                    product.get("item_id"), product.get("brand_name"), product.get("generic_name"),
                    product.get("therapeutic_class"), product.get("strength"), product.get("dosage_form"),
                    inStock ? "in_stock" : "out_of_stock", Integer.parseInt(product.get("quantity_on_hand")),
                    match.get().score(), alternatives, notes, imageUrl(product.get("item_id"))));
            resolved.add(new Resolved(index, display(item, product), lower(product.get("generic_name")),
                    lower(product.get("therapeutic_class")), product.get("item_id")));
        }

        // Pairwise rule checks over the resolved generics (unmatched items are excluded, but warned about above).
        for (int a = 0; a < resolved.size(); a++) {
            for (int b = a + 1; b < resolved.size(); b++) {
                Resolved x = resolved.get(a);
                Resolved y = resolved.get(b);
                boolean sameGeneric = x.generic().equals(y.generic());
                for (Map<String, String> rule : rules) {
                    String itemA = lower(rule.get("item_a"));
                    String itemB = lower(rule.get("item_b"));
                    boolean fires = switch (rule.get("rule_type")) {
                        case "interaction" -> (itemA.equals(x.generic()) && itemB.equals(y.generic()))
                                || (itemA.equals(y.generic()) && itemB.equals(x.generic()));
                        // Same active ingredient is reported once, by duplicate_generic - not also as a duplicate class.
                        case "duplicate_class" -> !sameGeneric && x.therapeuticClass().equals(y.therapeuticClass())
                                && itemA.equals(x.therapeuticClass());
                        case "duplicate_generic" -> sameGeneric;
                        default -> false;
                    };
                    if (fires) {
                        warnings.add(new Checklist.Warning(rule.get("severity"), rule.get("rule_id"), rule.get("rule_type"),
                                List.of(x.display(), y.display()), List.of(x.row(), y.row()), rule.get("warning_message")));
                    }
                }
            }
        }
        warnings.sort(Comparator
                .comparingInt((Checklist.Warning w) -> SEVERITY_ORDER.indexOf(w.severity()))
                .thenComparing(w -> w.ruleId() == null ? "~" : w.ruleId()));
        return new Checklist(Instant.now(), rows, warnings);
    }

    /** In-stock products with the same generic name: same strength first, then the largest quantity. */
    private static List<Checklist.Alternative> alternatives(List<Map<String, String>> stock, Map<String, String> product) {
        String generic = lower(product.get("generic_name"));
        String strength = firstNumber(product.get("strength"));
        return stock.stream()
                .filter(r -> !r.get("item_id").equals(product.get("item_id")))
                .filter(r -> lower(r.get("generic_name")).equals(generic))
                .filter(r -> "yes".equalsIgnoreCase(r.get("in_stock")))
                .map(r -> new Checklist.Alternative(r.get("item_id"), r.get("brand_name"), r.get("generic_name"),
                        r.get("strength"), Integer.parseInt(r.get("quantity_on_hand")),
                        Objects.equals(strength, firstNumber(r.get("strength"))), imageUrl(r.get("item_id"))))
                .sorted(Comparator.comparing(Checklist.Alternative::sameStrength).reversed()
                        .thenComparing(Comparator.comparingInt(Checklist.Alternative::quantityOnHand).reversed()))
                .toList();
    }

    public static String imageUrl(String itemId) {
        return itemId == null ? null : "/api/stock/" + itemId + "/image";
    }

    private static String display(Item item, Map<String, String> product) {
        String written = item.drugName() == null ? product.get("brand_name") : item.drugName().trim();
        return written.equalsIgnoreCase(product.get("generic_name")) ? written
                : written + " (" + product.get("generic_name") + ")";
    }

    private static String join(String a, String b) {
        return ((a == null ? "" : a) + (b == null ? "" : " " + b)).trim();
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNumber(String s) {
        if (s == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(s);
        return m.find() ? m.group() : null;
    }
}
