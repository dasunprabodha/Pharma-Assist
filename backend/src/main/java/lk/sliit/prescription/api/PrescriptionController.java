package lk.sliit.prescription.api;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.sliit.prescription.data.DataLoader;
import lk.sliit.prescription.errors.InvalidImageError;
import lk.sliit.prescription.history.ConfirmedPrescription;
import lk.sliit.prescription.history.ConfirmedStore;
import lk.sliit.prescription.image.ImageUtils;
import lk.sliit.prescription.model.Item;
import lk.sliit.prescription.namecheck.NameCheck;
import lk.sliit.prescription.service.DashboardService;
import lk.sliit.prescription.service.ScanResult;
import lk.sliit.prescription.service.ScanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class PrescriptionController {

    private final ScanService scanService;
    private final NameCheck nameCheck;
    private final ConfirmedStore confirmedStore;
    private final DashboardService dashboardService;
    private final DataLoader data;

    public PrescriptionController(ScanService scanService, NameCheck nameCheck, ConfirmedStore confirmedStore,
                                  DashboardService dashboardService, DataLoader data) {
        this.scanService = scanService;
        this.nameCheck = nameCheck;
        this.confirmedStore = confirmedStore;
        this.dashboardService = dashboardService;
        this.data = data;
    }

    /** Feature 1: photo or PDF -> AI extraction -> name-checked items. The upload is not stored. */
    @PostMapping("/scans")
    public ScanResult scan(@RequestParam("file") MultipartFile file) {
        try {
            return scanService.scan(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new InvalidImageError("The upload could not be read. Please try again.", e);
        }
    }

    /**
     * Renders an uploaded PDF to the same page images that would be sent to the model (no AWS call),
     * so the browser can preview it on any device. Images are returned as data URLs; nothing is stored.
     */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestParam("file") MultipartFile file) {
        try {
            List<String> pages = ImageUtils.preparePages(file.getOriginalFilename(), file.getBytes()).stream()
                    .map(jpeg -> "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg))
                    .toList();
            return Map.of("pages", pages);
        } catch (IOException e) {
            throw new InvalidImageError("The upload could not be read. Please try again.", e);
        }
    }

    /** Re-runs the stock-list name check after the pharmacist edits names. */
    @PostMapping("/name-check")
    public List<Item> nameCheck(@RequestBody List<Item> items) {
        return nameCheck.check(items);
    }

    public record ConfirmRequest(String source, String scanId, List<Item> items, Integer corrections) {
    }

    /** Saves the pharmacist-confirmed list. Every row must have a drug name. */
    @PostMapping("/prescriptions")
    public ResponseEntity<?> confirm(@RequestBody ConfirmRequest req) {
        List<Item> items = req.items() == null ? List.of() : req.items();
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).drugName() == null || items.get(i).drugName().isBlank()) {
                missing.add(i + 1);
            }
        }
        if (items.isEmpty() || !missing.isEmpty()) {
            String message = items.isEmpty() ? "Add at least one item before confirming."
                    : "Every row needs a drug name. Missing in row(s): " + missing;
            return ResponseEntity.badRequest().body(Map.of("error_type", "validation", "message", message,
                    "rows", missing));
        }
        String source = "ai".equals(req.source()) ? "ai" : "manual";
        ConfirmedPrescription saved = new ConfirmedPrescription(UUID.randomUUID().toString().substring(0, 8),
                Instant.now(), source, req.scanId(), items,
                source.equals("ai") && req.corrections() != null ? Math.max(0, req.corrections()) : 0);
        confirmedStore.save(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/prescriptions")
    public List<ConfirmedPrescription> prescriptions() {
        return confirmedStore.findAll();
    }

    @GetMapping("/prescriptions/{id}")
    public ResponseEntity<ConfirmedPrescription> prescription(@PathVariable String id) {
        return ResponseEntity.of(confirmedStore.findById(id));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return dashboardService.dashboard();
    }

    /** SIMULATED stock list (read-only reference view). */
    @GetMapping("/stock")
    public List<Map<String, String>> stock() {
        return data.stock();
    }

    /** SIMULATED interaction rules - textbook examples, not a clinical database. */
    @GetMapping("/rules")
    public List<Map<String, String>> rules() {
        return data.rules();
    }
}
