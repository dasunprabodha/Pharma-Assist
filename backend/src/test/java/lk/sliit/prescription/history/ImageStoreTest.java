package lk.sliit.prescription.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Privacy behaviour of the optional History image store. */
class ImageStoreTest {

    @TempDir
    Path dir;

    private final byte[] page1 = {1, 2, 3};
    private final byte[] page2 = {4, 5};

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.now();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();

    private ImageStore store(boolean enabled) {
        return new ImageStore(dir.resolve("scan-images"), enabled, 7, clock);
    }

    private long filesOnDisk() throws IOException {
        Path images = dir.resolve("scan-images");
        if (!Files.isDirectory(images)) {
            return 0;
        }
        try (var files = Files.list(images)) {
            return files.count();
        }
    }

    @Test
    void disabledStoreNeverWritesToDisk() throws IOException {
        ImageStore store = store(false);
        store.holdForConfirm("aaaa1111", List.of(page1));
        assertEquals(0, store.keep("aaaa1111", "bbbb2222"));
        assertEquals(0, filesOnDisk());
        assertEquals(0, store.pageCount("bbbb2222"));
    }

    @Test
    void imageIsWrittenOnlyWhenTheListIsConfirmed() throws IOException {
        ImageStore store = store(true);
        store.holdForConfirm("aaaa1111", List.of(page1, page2));
        assertEquals(0, filesOnDisk(), "a scan alone must not write the image");

        assertEquals(2, store.keep("aaaa1111", "bbbb2222"));
        assertEquals(2, filesOnDisk());
        assertEquals(2, store.pageCount("bbbb2222"));
        assertArrayEquals(page2, store.read("bbbb2222", 2).orElseThrow());
        assertTrue(store.expiresAt("bbbb2222").isPresent());
        assertEquals(0, store.keep("aaaa1111", "cccc3333"), "a held image is used once");
    }

    @Test
    void unconfirmedScanIsForgottenAfterTimeout() throws IOException {
        ImageStore store = store(true);
        store.holdForConfirm("aaaa1111", List.of(page1));
        clock.now = clock.now.plus(ImageStore.PENDING_TTL).plusSeconds(1);
        assertEquals(0, store.keep("aaaa1111", "bbbb2222"));
        assertEquals(0, filesOnDisk());
    }

    @Test
    void savedImageExpiresAfterRetentionPeriodAndIsPurged() throws IOException {
        ImageStore store = store(true);
        store.holdForConfirm("aaaa1111", List.of(page1));
        store.keep("aaaa1111", "bbbb2222");
        Path saved = dir.resolve("scan-images/bbbb2222-1.jpg");
        Files.setLastModifiedTime(saved, FileTime.from(clock.now.minus(Duration.ofDays(8))));

        assertEquals(0, store.pageCount("bbbb2222"));
        assertTrue(store.read("bbbb2222", 1).isEmpty());
        assertEquals(1, store.purgeExpired());
        assertFalse(Files.exists(saved));
    }

    @Test
    void deleteRemovesEveryPage() throws IOException {
        ImageStore store = store(true);
        store.holdForConfirm("aaaa1111", List.of(page1, page2));
        store.keep("aaaa1111", "bbbb2222");
        assertTrue(store.delete("bbbb2222"));
        assertEquals(0, filesOnDisk());
        assertFalse(store.delete("bbbb2222"));
    }

    @Test
    void unsafeIdsAreRejected() throws IOException {
        ImageStore store = store(true);
        store.holdForConfirm("../../etc", List.of(page1));
        assertEquals(0, store.keep("../../etc", "bbbb2222"));
        assertTrue(store.read("../bbbb2222", 1).isEmpty());
        assertFalse(store.delete("*"));
        assertEquals(0, filesOnDisk());
    }
}
