package lk.sliit.prescription.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lk.sliit.prescription.data.DataLoader;
import org.springframework.stereotype.Component;

/**
 * Product images for stock items, so the pharmacist can see which pack to pick.
 *
 * <p>If the pharmacy puts its own shelf photo in data/drug_images/&lt;item_id&gt;.jpg (or .png/.webp), that photo is
 * used. Otherwise an ILLUSTRATION is generated (brand, generic, strength, colour by dosage form) and clearly labelled
 * as not being a product photo - no internet images are used, so there are no copyright issues.
 */
@Component
public class DrugImages {

    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final Map<String, String> CONTENT_TYPES = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg",
            "png", "image/png", "webp", "image/webp");

    public record DrugImage(byte[] bytes, String contentType, boolean photo) {
    }

    private final DataLoader data;

    public DrugImages(DataLoader data) {
        this.data = data;
    }

    public Optional<DrugImage> forItem(String itemId) {
        if (itemId == null || !ITEM_ID.matcher(itemId).matches()) {
            return Optional.empty();
        }
        Optional<Map<String, String>> row = data.stock().stream().filter(r -> itemId.equals(r.get("item_id"))).findFirst();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Path dir = data.dataDir().resolve("drug_images");
        for (Map.Entry<String, String> ext : CONTENT_TYPES.entrySet()) {
            Path photo = dir.resolve(itemId + "." + ext.getKey());
            if (Files.isRegularFile(photo)) {
                try {
                    return Optional.of(new DrugImage(Files.readAllBytes(photo), ext.getValue(), true));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return Optional.of(new DrugImage(illustration(row.get()).getBytes(StandardCharsets.UTF_8), "image/svg+xml", false));
    }

    /** A simple pack illustration: a coloured band by dosage form, a form icon, brand, generic and strength. */
    static String illustration(Map<String, String> r) {
        String form = r.getOrDefault("dosage_form", "").toLowerCase(Locale.ROOT);
        String[] palette = form.contains("capsule") ? new String[] {"#eb6834", "#fdebe3"}
                : form.contains("inhaler") ? new String[] {"#1baf7a", "#e3f6ef"}
                : form.contains("dispersible") ? new String[] {"#4a3aa7", "#ebe8f8"}
                : new String[] {"#2a78d6", "#e6f0fb"};
        String icon;
        if (form.contains("capsule")) {
            icon = "<g transform='rotate(-35 42 75)'><rect x='18' y='62' width='48' height='26' rx='13' fill='#fff'/>"
                    + "<path d='M42 62h11a13 13 0 0 1 0 26H42z' fill='" + palette[1] + "'/></g>";
        } else if (form.contains("inhaler")) {
            icon = "<rect x='30' y='44' width='22' height='44' rx='6' fill='#fff'/><rect x='24' y='84' width='34' height='16' rx='4' fill='"
                    + palette[1] + "'/>";
        } else {
            icon = "<circle cx='42' cy='75' r='22' fill='#fff'/><line x1='42' y1='55' x2='42' y2='95' stroke='"
                    + palette[0] + "' stroke-width='3'/>";
        }
        String brand = clip(r.getOrDefault("brand_name", ""), 18);
        String generic = clip(r.getOrDefault("generic_name", ""), 22);
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 260 150' width='260' height='150' role='img' aria-label='"
                + esc(brand + " " + r.getOrDefault("strength", "") + " illustration") + "'>"
                + "<rect x='1' y='1' width='258' height='148' rx='10' fill='#ffffff' stroke='#d5dae1'/>"
                + "<rect x='1' y='1' width='84' height='148' rx='10' fill='" + palette[0] + "'/>"
                + "<rect x='70' y='1' width='15' height='148' fill='" + palette[0] + "'/>"
                + icon
                + "<text x='100' y='46' font-family='DejaVu Sans,Arial,sans-serif' font-size='19' font-weight='700' fill='#16181d'>" + esc(brand) + "</text>"
                + "<text x='100' y='70' font-family='DejaVu Sans,Arial,sans-serif' font-size='13' fill='#3b4150'>" + esc(generic) + "</text>"
                + "<text x='100' y='93' font-family='DejaVu Sans,Arial,sans-serif' font-size='15' font-weight='700' fill='" + palette[0] + "'>"
                + esc(r.getOrDefault("strength", "")) + "</text>"
                + "<text x='100' y='112' font-family='DejaVu Sans,Arial,sans-serif' font-size='11' fill='#5b6170'>" + esc(r.getOrDefault("dosage_form", "")) + "</text>"
                + "<text x='100' y='138' font-family='DejaVu Sans,Arial,sans-serif' font-size='8.5' letter-spacing='0.6' fill='#8a8f99'>ILLUSTRATION – NOT A PRODUCT PHOTO</text>"
                + "</svg>";
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;");
    }
}
