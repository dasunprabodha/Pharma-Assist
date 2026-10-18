package lk.sliit.prescription.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.sliit.prescription.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Stores confirmed lists as JSON lines in logs/confirmed.jsonl (git-ignored). */
@Component
public class ConfirmedStore {

    private final Path file;
    private final ObjectMapper json;

    @Autowired
    public ConfirmedStore(ObjectMapper json) {
        this(AppConfig.logsDir().resolve("confirmed.jsonl"), json);
    }

    public ConfirmedStore(Path file, ObjectMapper json) {
        this.file = file;
        this.json = json;
    }

    public synchronized void save(ConfirmedPrescription p) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json.writeValueAsString(p) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /** All confirmed lists, newest first. */
    public synchronized List<ConfirmedPrescription> findAll() {
        List<ConfirmedPrescription> all = new ArrayList<>();
        if (!Files.exists(file)) {
            return all;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    all.add(json.readValue(line, ConfirmedPrescription.class));
                } catch (IOException e) {
                    // Skip a malformed line.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        all.sort((a, b) -> b.confirmedAt().compareTo(a.confirmedAt()));
        return all;
    }

    public Optional<ConfirmedPrescription> findById(String id) {
        return findAll().stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
