package lk.sliit.prescription.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads configuration from environment variables, falling back to the project's .env file.
 * Used by both the Spring app and the CLI tools so they behave the same way.
 * Never holds AWS access keys - credentials come from the standard AWS chain.
 */
public final class AppConfig {

    public static final String DEFAULT_REGION = "us-east-1";
    public static final String DEFAULT_MODEL_ID = "us.amazon.nova-2-lite-v1:0";

    // The app may be started from the repo root or from backend/
    private static final List<Path> PROJECT_ROOT_CANDIDATES = List.of(Path.of("."), Path.of(".."));

    private static final Map<String, String> DOTENV = loadDotenv();

    private AppConfig() {
    }

    public static Optional<String> get(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = DOTENV.get(key);
        }
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    public static String region() {
        return get("AWS_REGION").orElse(DEFAULT_REGION);
    }

    public static Optional<String> profile() {
        return get("AWS_PROFILE");
    }

    public static String modelId() {
        return get("BEDROCK_MODEL_ID").orElse(DEFAULT_MODEL_ID);
    }

    /**
     * Prompt version: "v2" (default - safest in docs/evaluation.md), "v3" or "v1" (original baseline).
     */
    public static String promptVersion() {
        return get("PROMPT_VERSION").orElse("v2");
    }

    public static boolean isModelIdSet() {
        return get("BEDROCK_MODEL_ID").isPresent();
    }

    public static boolean isRegionSet() {
        return get("AWS_REGION").isPresent();
    }

    /** Folder holding stock_list.csv and interaction_rules.csv. */
    public static Path dataDir() {
        return get("DATA_DIR").map(Path::of).orElseGet(() -> PROJECT_ROOT_CANDIDATES.stream()
                .map(root -> root.resolve("data"))
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(Path.of("data")));
    }

    /** Folder for usage.csv and confirmed.jsonl (git-ignored). */
    public static Path logsDir() {
        return get("LOGS_DIR").map(Path::of).orElseGet(() -> dataDir().toAbsolutePath().normalize().resolveSibling("logs"));
    }

    /** USD per 1M input tokens, for cost estimates (default: Nova 2 Lite, US geo inference profile). */
    public static double inputPricePerMillion() {
        return get("PRICE_INPUT_PER_1M").map(Double::parseDouble).orElse(0.33);
    }

    /** USD per 1M output tokens, for cost estimates (default: Nova 2 Lite, US geo inference profile). */
    public static double outputPricePerMillion() {
        return get("PRICE_OUTPUT_PER_1M").map(Double::parseDouble).orElse(2.75);
    }

    /** Save the processed image of each confirmed AI list so History can show it (off by default). */
    public static boolean keepImages() {
        return get("KEEP_IMAGES").map(v -> v.equalsIgnoreCase("true") || v.equals("1")).orElse(false);
    }

    /** Days a saved image is kept before it is deleted automatically (default 7). */
    public static int imageRetentionDays() {
        try {
            return get("IMAGE_RETENTION_DAYS").map(Integer::parseInt).orElse(7);
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    private static Map<String, String> loadDotenv() {
        Map<String, String> values = new HashMap<>();
        for (Path root : PROJECT_ROOT_CANDIDATES) {
            Path file = root.resolve(".env");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    int eq = trimmed.indexOf('=');
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 1) {
                        continue;
                    }
                    String value = trimmed.substring(eq + 1).trim().replaceAll("^[\"']|[\"']$", "");
                    values.put(trimmed.substring(0, eq).trim(), value);
                }
            } catch (IOException e) {
                // An unreadable .env is treated as absent; the status box will show what is missing.
            }
            break;
        }
        return values;
    }
}
