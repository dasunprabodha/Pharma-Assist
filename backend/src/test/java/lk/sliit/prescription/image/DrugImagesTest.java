package lk.sliit.prescription.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lk.sliit.prescription.data.DataLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DrugImagesTest {

    @Test
    void generatesALabelledIllustrationWhenNoPhotoExists() {
        DrugImages.DrugImage img = new DrugImages(new DataLoader()).forItem("S024").orElseThrow();
        String svg = new String(img.bytes(), StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", img.contentType());
        assertFalse(img.photo());
        assertTrue(svg.contains("Lipitor") && svg.contains("atorvastatin") && svg.contains("20 mg"));
        assertTrue(svg.contains("NOT A PRODUCT PHOTO"));
    }

    @Test
    void usesThePharmacysOwnPhotoWhenPresent(@TempDir Path dir) throws Exception {
        Files.copy(Path.of("../data/stock_list.csv"), dir.resolve("stock_list.csv"));
        Files.createDirectories(dir.resolve("drug_images"));
        byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        Files.write(dir.resolve("drug_images/S013.jpg"), photo);
        DrugImages.DrugImage img = new DrugImages(new DataLoader(dir)).forItem("S013").orElseThrow();
        assertTrue(img.photo());
        assertEquals("image/jpeg", img.contentType());
        assertArrayEquals(photo, img.bytes());
    }

    @Test
    void rejectsUnknownIdsAndPathTricks() {
        DrugImages images = new DrugImages(new DataLoader());
        assertTrue(images.forItem("S999").isEmpty());
        assertTrue(images.forItem("../../etc/passwd").isEmpty());
        assertTrue(images.forItem(null).isEmpty());
    }

    @Test
    void escapesTextInTheSvg() {
        String svg = DrugImages.illustration(Map.of("brand_name", "<script>", "generic_name", "a&b",
                "strength", "1 mg", "dosage_form", "capsule"));
        assertFalse(svg.contains("<script>"));
        assertTrue(svg.contains("&lt;script&gt;") && svg.contains("a&amp;b"));
    }
}
