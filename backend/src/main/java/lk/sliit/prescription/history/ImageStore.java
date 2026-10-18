package lk.sliit.prescription.history;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lk.sliit.prescription.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional store for the prescription image behind each confirmed AI list, so History can show it.
 *
 * <p>Privacy rules:
 * <ul>
 *   <li>Off unless KEEP_IMAGES=true. When off, nothing is ever written to disk.</li>
 *   <li>Only the processed image is kept (metadata already stripped, at most 1600 px JPEG) - never the upload.</li>
 *   <li>After a scan the image is held in memory only; it is written to disk when, and only if, the
 *       pharmacist confirms the list. Unconfirmed scans are dropped after {@link #PENDING_TTL}.</li>
 *   <li>Saved images are deleted automatically after IMAGE_RETENTION_DAYS (default 7), or on request.</li>
 *   <li>Files live in logs/scan-images/ (git-ignored).</li>
 * </ul>
 */
@Component
public class ImageStore {

    static final Duration PENDING_TTL = Duration.ofMinutes(30);
    static final int MAX_PENDING = 20;
    /** Scan and prescription IDs are 8 hex characters; anything else is rejected (no path tricks). */
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}");

    private record Pending(List<byte[]> pages, Instant heldAt) {
    }

    private final Path dir;
    private final boolean enabled;
    private final int retentionDays;
    private final Clock clock;
    private final Map<String, Pending> pending = new LinkedHashMap<>();

    @Autowired
    public ImageStore() {
        this(AppConfig.logsDir().resolve("scan-images"), AppConfig.keepImages(), AppConfig.imageRetentionDays(),
                Clock.systemUTC());
    }

    public ImageStore(Path dir, boolean enabled, int retentionDays, Clock clock) {
        this.dir = dir;
        this.enabled = enabled;
        this.retentionDays = Math.max(1, retentionDays);
        this.clock = clock;
    }

    public boolean enabled() {
        return enabled;
    }

    public int retentionDays() {
        return retentionDays;
    }

    /** After a successful scan: keep the processed pages in memory until the list is confirmed. */
    public synchronized void holdForConfirm(String scanId, List<byte[]> pages) {
        if (!enabled || !valid(scanId) || pages.isEmpty()) {
            return;
        }
        dropStalePending();
        pending.put(scanId, new Pending(List.copyOf(pages), clock.instant()));
        // Bound memory: forget the oldest held scans first.
        Iterator<String> oldest = pending.keySet().iterator();
        while (pending.size() > MAX_PENDING) {
            oldest.next();
            oldest.remove();
        }
    }

    /** On confirm: write the held pages under the prescription ID. Returns the number of pages saved. */
    public synchronized int keep(String scanId, String prescriptionId) {
        if (!enabled || !valid(scanId) || !valid(prescriptionId)) {
            return 0;
        }
        dropStalePending();
        Pending held = pending.remove(scanId);
        if (held == null) {
            return 0;
        }
        try {
            Files.createDirectories(dir);
            for (int i = 0; i < held.pages().size(); i++) {
                Files.write(file(prescriptionId, i + 1), held.pages().get(i));
            }
            return held.pages().size();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save image for " + prescriptionId, e);
        }
    }

    /** Number of saved pages for a confirmed list (0 if none, deleted or expired). */
    public int pageCount(String prescriptionId) {
        if (!valid(prescriptionId)) {
            return 0;
        }
        int n = 0;
        while (Files.isRegularFile(file(prescriptionId, n + 1)) && !expired(file(prescriptionId, n + 1))) {
            n++;
        }
        return n;
    }

    /** When the saved image will be deleted automatically, if there is one. */
    public Optional<Instant> expiresAt(String prescriptionId) {
        if (pageCount(prescriptionId) == 0) {
            return Optional.empty();
        }
        try {
            Instant saved = Files.getLastModifiedTime(file(prescriptionId, 1)).toInstant();
            return Optional.of(saved.plus(Duration.ofDays(retentionDays)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** JPEG bytes of one page (1-based), unless missing or expired. */
    public Optional<byte[]> read(String prescriptionId, int page) {
        if (!valid(prescriptionId) || page < 1) {
            return Optional.empty();
        }
        Path f = file(prescriptionId, page);
        if (!Files.isRegularFile(f) || expired(f)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(f));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Delete every saved page of a confirmed list. Returns true if anything was deleted. */
    public boolean delete(String prescriptionId) {
        if (!valid(prescriptionId)) {
            return false;
        }
        boolean any = false;
        for (int page = 1; Files.exists(file(prescriptionId, page)); page++) {
            try {
                any |= Files.deleteIfExists(file(prescriptionId, page));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot delete image for " + prescriptionId, e);
            }
        }
        return any;
    }

    /**
     * Deletes saved images older than the retention period (hourly, and at start-up). Runs even when
     * KEEP_IMAGES has since been turned off, so images saved earlier still expire.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 60 * 60 * 1000)
    public int purgeExpired() {
        synchronized (this) {
            dropStalePending();
        }
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.jpg")) {
            for (Path f : files) {
                if (expired(f) && Files.deleteIfExists(f)) {
                    deleted++;
                }
            }
        } catch (IOException e) {
            // Try again next run.
        }
        return deleted;
    }

    private boolean expired(Path f) {
        try {
            Instant saved = Files.getLastModifiedTime(f).toInstant();
            return saved.plus(Duration.ofDays(retentionDays)).isBefore(clock.instant());
        } catch (IOException e) {
            return true;
        }
    }

    private void dropStalePending() {
        Instant cutoff = clock.instant().minus(PENDING_TTL);
        pending.values().removeIf(p -> p.heldAt().isBefore(cutoff));
    }

    private Path file(String prescriptionId, int page) {
        return dir.resolve(prescriptionId + "-" + page + ".jpg");
    }

    private static boolean valid(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
