package lk.sliit.prescription.api;

import java.util.List;
import lk.sliit.prescription.checker.Checker;
import lk.sliit.prescription.checker.Checklist;
import lk.sliit.prescription.image.DrugImages;
import lk.sliit.prescription.model.Item;
import lk.sliit.prescription.namecheck.NameCheck;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Feature 2: the dispensing check, and product images for stock items. */
@RestController
@RequestMapping("/api")
public class CheckController {

    private final Checker checker;
    private final DrugImages drugImages;
    private final NameCheck nameCheck;

    public CheckController(Checker checker, DrugImages drugImages, NameCheck nameCheck) {
        this.checker = checker;
        this.drugImages = drugImages;
        this.nameCheck = nameCheck;
    }

    public record CheckRequest(List<Item> items) {
    }

    public record NameAndStrength(String drugName, String strength) {
    }

    /** The stock item each name resolves to - the same matcher the checker uses - so the UI can show its image. */
    public record StockMatch(String itemId, String brandName, String genericName, String strength, boolean inStock,
                             int score, String imageUrl) {
    }

    @PostMapping("/stock-matches")
    public List<StockMatch> stockMatches(@RequestBody List<NameAndStrength> names) {
        return names.stream().map(n -> nameCheck.match(n.drugName(), n.strength())
                .map(m -> new StockMatch(m.row().get("item_id"), m.row().get("brand_name"), m.row().get("generic_name"),
                        m.row().get("strength"), "yes".equalsIgnoreCase(m.row().get("in_stock")), m.score(),
                        Checker.imageUrl(m.row().get("item_id"))))
                .orElse(null)).toList();
    }

    /** Works the same for AI-extracted and manually entered lists - both use the shared Item structure. */
    @PostMapping("/check")
    public Checklist check(@RequestBody CheckRequest request) {
        return checker.check(request.items() == null ? List.of() : request.items());
    }

    /** The pharmacy's own photo from data/drug_images/, or a generated illustration (SVG). */
    @GetMapping("/stock/{itemId}/image")
    public ResponseEntity<byte[]> image(@PathVariable String itemId) {
        return drugImages.forItem(itemId)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.contentType()))
                        .cacheControl(CacheControl.noCache())
                        .header("X-Image-Kind", img.photo() ? "photo" : "illustration")
                        // SVG served as an image must never run scripts.
                        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                        .body(img.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
