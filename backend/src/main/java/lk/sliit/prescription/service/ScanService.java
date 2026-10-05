package lk.sliit.prescription.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.errors.PrescriptionException;
import lk.sliit.prescription.extractor.Extractor;
import lk.sliit.prescription.extractor.TokenUsage;
import lk.sliit.prescription.history.ScanRecord;
import lk.sliit.prescription.history.UsageLog;
import lk.sliit.prescription.image.ImageUtils;
import lk.sliit.prescription.model.Confidence;
import lk.sliit.prescription.model.Item;
import lk.sliit.prescription.namecheck.NameCheck;
import org.springframework.stereotype.Service;

/** Feature 1 pipeline: image/PDF checks -> Amazon Nova extraction -> name check, logging every attempt. */
@Service
public class ScanService {

    private final Extractor extractor;
    private final NameCheck nameCheck;
    private final UsageLog usageLog;

    public ScanService(Extractor extractor, NameCheck nameCheck, UsageLog usageLog) {
        this.extractor = extractor;
        this.nameCheck = nameCheck;
        this.usageLog = usageLog;
    }

    public ScanResult scan(String filename, byte[] upload) {
        String scanId = UUID.randomUUID().toString().substring(0, 8);
        TokenUsage usage = new TokenUsage();
        long start = System.currentTimeMillis();
        String outcome = "success";
        List<Item> items = List.of();
        try {
            List<byte[]> pages = ImageUtils.preparePages(filename, upload);
            items = nameCheck.check(extractor.extractItems(pages, usage));
            long latency = System.currentTimeMillis() - start;
            return new ScanResult(scanId, extractor.modelId(), pages.size(), items, lowRows(items), usage.inputTokens(),
                    usage.outputTokens(), latency, cost(usage.inputTokens(), usage.outputTokens()));
        } catch (PrescriptionException e) {
            outcome = e.code();
            throw e;
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            int low = lowRows(items).size();
            usageLog.append(new ScanRecord(Instant.now(), scanId, extractor.modelId(), outcome, items.size(), low,
                    usage.inputTokens(), usage.outputTokens(), System.currentTimeMillis() - start));
        }
    }

    /** 1-based row numbers of low-confidence items, for "Row 2: low confidence – please verify". */
    public static List<Integer> lowRows(List<Item> items) {
        return IntStream.range(0, items.size())
                .filter(i -> items.get(i).confidence() == Confidence.LOW)
                .mapToObj(i -> i + 1)
                .toList();
    }

    public static double cost(long inputTokens, long outputTokens) {
        return inputTokens / 1e6 * AppConfig.inputPricePerMillion()
                + outputTokens / 1e6 * AppConfig.outputPricePerMillion();
    }
}
