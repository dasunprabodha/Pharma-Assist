package lk.sliit.prescription.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.history.ConfirmedPrescription;
import lk.sliit.prescription.history.ConfirmedStore;
import lk.sliit.prescription.history.ScanRecord;
import lk.sliit.prescription.history.UsageLog;
import lk.sliit.prescription.model.Item;
import org.springframework.stereotype.Service;

/** Aggregates logs/usage.csv and logs/confirmed.jsonl into dashboard figures. */
@Service
public class DashboardService {

    static final long TARGET_MS = 30_000;   // R1: the list must appear within 30 s
    private static final int DAYS = 14;
    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private final UsageLog usageLog;
    private final ConfirmedStore confirmed;

    public DashboardService(UsageLog usageLog, ConfirmedStore confirmed) {
        this.usageLog = usageLog;
        this.confirmed = confirmed;
    }

    public Map<String, Object> dashboard() {
        List<ScanRecord> scans = usageLog.readAll();
        List<ConfirmedPrescription> lists = confirmed.findAll();
        List<ScanRecord> ok = scans.stream().filter(s -> s.outcome().equals("success")).toList();

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("scans_total", scans.size());
        d.put("scans_success", ok.size());
        d.put("success_rate", scans.isEmpty() ? null : (double) ok.size() / scans.size());

        List<Long> latencies = ok.stream().map(ScanRecord::latencyMs).sorted().toList();
        d.put("avg_latency_ms", latencies.isEmpty() ? null : latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        d.put("p95_latency_ms", latencies.isEmpty() ? null : latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1));
        d.put("within_target_rate", latencies.isEmpty() ? null
                : (double) latencies.stream().filter(l -> l <= TARGET_MS).count() / latencies.size());
        d.put("target_ms", TARGET_MS);

        long in = scans.stream().mapToLong(ScanRecord::inputTokens).sum();
        long out = scans.stream().mapToLong(ScanRecord::outputTokens).sum();
        double total = ScanService.cost(in, out);
        d.put("input_tokens", in);
        d.put("output_tokens", out);
        d.put("cost_total_usd", total);
        d.put("cost_per_scan_usd", ok.isEmpty() ? null : total / ok.size());
        d.put("cost_per_1000_scans_usd", ok.isEmpty() ? null : total / ok.size() * 1000);
        d.put("price_input_per_1m", AppConfig.inputPricePerMillion());
        d.put("price_output_per_1m", AppConfig.outputPricePerMillion());

        d.put("outcomes", countBy(scans, ScanRecord::outcome));
        d.put("scans_per_day", perDay(scans));

        List<ConfirmedPrescription> aiLists = lists.stream().filter(p -> p.source().equals("ai")).toList();
        d.put("confirmed_total", lists.size());
        d.put("confirmed_ai", aiLists.size());
        d.put("confirmed_manual", lists.size() - aiLists.size());
        d.put("items_confirmed", lists.stream().mapToInt(p -> p.items().size()).sum());
        d.put("corrections_total", aiLists.stream().mapToInt(ConfirmedPrescription::corrections).sum());
        d.put("corrections_per_ai_list", aiLists.isEmpty() ? null
                : aiLists.stream().mapToInt(ConfirmedPrescription::corrections).average().orElse(0));
        d.put("ai_lists_unchanged_rate", aiLists.isEmpty() ? null
                : (double) aiLists.stream().filter(p -> p.corrections() == 0).count() / aiLists.size());

        int low = ok.stream().mapToInt(ScanRecord::lowConfidenceCount).sum();
        int extracted = ok.stream().mapToInt(ScanRecord::itemCount).sum();
        d.put("items_extracted", extracted);
        d.put("items_low_confidence", low);

        List<Item> allItems = lists.stream().flatMap(p -> p.items().stream()).toList();
        d.put("confidence_confirmed", countBy(allItems, i -> i.confidence().json()));
        d.put("top_drugs", topDrugs(allItems, 8));

        List<ScanRecord> recent = new ArrayList<>(scans);
        recent.sort(Comparator.comparing(ScanRecord::timestamp).reversed());
        d.put("recent_scans", recent.stream().limit(10).toList());
        return d;
    }

    private static <T> Map<String, Long> countBy(List<T> list, Function<T, String> key) {
        return list.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }

    private static List<Map<String, Object>> perDay(List<ScanRecord> scans) {
        LocalDate today = LocalDate.now(ZONE);
        Map<LocalDate, long[]> days = new TreeMap<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            days.put(today.minusDays(i), new long[2]);
        }
        for (ScanRecord s : scans) {
            long[] c = days.get(s.timestamp().atZone(ZONE).toLocalDate());
            if (c != null) {
                c[s.outcome().equals("success") ? 0 : 1]++;
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        days.forEach((day, c) -> result.add(Map.of("date", day.toString(), "success", c[0], "failed", c[1])));
        return result;
    }

    private static List<Map<String, Object>> topDrugs(List<Item> items, int limit) {
        Map<String, Long> counts = items.stream()
                .filter(i -> i.drugName() != null)
                .collect(Collectors.groupingBy(i -> capitalise(i.drugName()), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue()))
                .toList();
    }

    private static String capitalise(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
