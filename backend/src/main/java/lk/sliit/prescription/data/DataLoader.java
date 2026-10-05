package lk.sliit.prescription.data;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lk.sliit.prescription.config.AppConfig;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Loads the SIMULATED stock list and interaction rules (data/*.csv).
 * These are proof-of-concept data, not a clinical database.
 */
@Component
public class DataLoader {

    public static final List<String> STOCK_COLUMNS = List.of("item_id", "brand_name", "generic_name", "strength",
            "dosage_form", "therapeutic_class", "in_stock", "quantity_on_hand");
    public static final List<String> RULE_COLUMNS = List.of("rule_id", "rule_type", "item_a", "item_b", "severity",
            "warning_message");

    private final List<Map<String, String>> stock;
    private final List<Map<String, String>> rules;

    public DataLoader() {
        this(AppConfig.dataDir());
    }

    public DataLoader(Path dataDir) {
        this.stock = read(dataDir.resolve("stock_list.csv"), STOCK_COLUMNS);
        this.rules = read(dataDir.resolve("interaction_rules.csv"), RULE_COLUMNS);
    }

    public List<Map<String, String>> stock() {
        return stock;
    }

    public List<Map<String, String>> rules() {
        return rules;
    }

    static List<Map<String, String>> read(Path file, List<String> expectedColumns) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            if (!parser.getHeaderNames().equals(expectedColumns)) {
                throw new IllegalStateException(file + " has columns " + parser.getHeaderNames()
                        + " but expected " + expectedColumns);
            }
            return parser.getRecords().stream().map(CSVRecord::toMap).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }
}
