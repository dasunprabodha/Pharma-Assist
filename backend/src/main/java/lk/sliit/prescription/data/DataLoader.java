package lk.sliit.prescription.data;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.errors.DataError;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Loads the SIMULATED stock list and interaction rules (data/*.csv) once, on first use, and caches them.
 * These are proof-of-concept data, not a clinical database. A missing or malformed file raises {@link DataError}
 * (the app still starts; the UI shows "Stock/rule data unavailable").
 */
@Component
public class DataLoader {

    public static final List<String> STOCK_COLUMNS = List.of("item_id", "brand_name", "generic_name", "strength",
            "dosage_form", "therapeutic_class", "in_stock", "quantity_on_hand");
    public static final List<String> RULE_COLUMNS = List.of("rule_id", "rule_type", "item_a", "item_b", "severity",
            "warning_message");
    private static final Set<String> RULE_TYPES = Set.of("interaction", "duplicate_class", "duplicate_generic");
    private static final Set<String> SEVERITIES = Set.of("major", "moderate", "minor");

    private final Path dataDir;
    private volatile List<Map<String, String>> stock;
    private volatile List<Map<String, String>> rules;

    public DataLoader() {
        this(AppConfig.dataDir());
    }

    public DataLoader(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Path dataDir() {
        return dataDir;
    }

    public List<Map<String, String>> stock() {
        if (stock == null) {
            List<Map<String, String>> rows = read(dataDir.resolve("stock_list.csv"), STOCK_COLUMNS);
            for (Map<String, String> r : rows) {
                if (!Set.of("yes", "no").contains(r.get("in_stock")) || !r.get("quantity_on_hand").matches("\\d+")) {
                    throw new DataError("stock_list.csv: invalid in_stock/quantity_on_hand for " + r.get("item_id"));
                }
            }
            stock = rows;
        }
        return stock;
    }

    public List<Map<String, String>> rules() {
        if (rules == null) {
            List<Map<String, String>> rows = read(dataDir.resolve("interaction_rules.csv"), RULE_COLUMNS);
            for (Map<String, String> r : rows) {
                if (!RULE_TYPES.contains(r.get("rule_type")) || !SEVERITIES.contains(r.get("severity"))) {
                    throw new DataError("interaction_rules.csv: invalid rule_type/severity for " + r.get("rule_id"));
                }
            }
            rules = rows;
        }
        return rules;
    }

    static List<Map<String, String>> read(Path file, List<String> expectedColumns) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            if (!parser.getHeaderNames().equals(expectedColumns)) {
                throw new DataError(file.getFileName() + " has columns " + parser.getHeaderNames()
                        + " but expected " + expectedColumns);
            }
            List<CSVRecord> records = parser.getRecords();
            for (CSVRecord r : records) {
                if (!r.isConsistent()) {
                    throw new DataError(file.getFileName() + " line " + r.getRecordNumber() + " has the wrong number of columns");
                }
            }
            return records.stream().map(CSVRecord::toMap).toList();
        } catch (NoSuchFileException e) {
            throw new DataError(file.getFileName() + " not found in " + file.getParent().toAbsolutePath(), e);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new DataError("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
