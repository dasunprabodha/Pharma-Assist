package lk.sliit.prescription.history;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.sliit.prescription.config.AppConfig;
import org.springframework.stereotype.Component;

/**
 * Appends one row per scan to logs/usage.csv (git-ignored): outcome, token usage and latency.
 * This gives real per-scan cost data for the report's cost section.
 */
@Component
public class UsageLog {

    static final String HEADER = "timestamp,scan_id,model_id,outcome,item_count,low_confidence_count,"
            + "input_tokens,output_tokens,latency_ms";

    private final Path file;

    public UsageLog() {
        this(AppConfig.logsDir().resolve("usage.csv"));
    }

    public UsageLog(Path file) {
        this.file = file;
    }

    public synchronized void append(ScanRecord r) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file, HEADER + "\n", StandardCharsets.UTF_8);
            }
            String line = String.join(",", r.timestamp().toString(), r.scanId(), r.modelId().replace(",", ";"),
                    r.outcome(), String.valueOf(r.itemCount()), String.valueOf(r.lowConfidenceCount()),
                    String.valueOf(r.inputTokens()), String.valueOf(r.outputTokens()), String.valueOf(r.latencyMs()));
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    public synchronized List<ScanRecord> readAll() {
        List<ScanRecord> records = new ArrayList<>();
        if (!Files.exists(file)) {
            return records;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] f = line.split(",", -1);
                if (f.length != 9 || line.startsWith("timestamp")) {
                    continue;
                }
                try {
                    records.add(new ScanRecord(Instant.parse(f[0]), f[1], f[2], f[3], Integer.parseInt(f[4]),
                            Integer.parseInt(f[5]), Integer.parseInt(f[6]), Integer.parseInt(f[7]), Long.parseLong(f[8])));
                } catch (RuntimeException e) {
                    // Skip a malformed line rather than failing the dashboard.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return records;
    }
}
