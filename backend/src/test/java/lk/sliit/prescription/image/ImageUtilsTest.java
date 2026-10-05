package lk.sliit.prescription.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import lk.sliit.prescription.errors.InvalidImageError;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

class ImageUtilsTest {

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    /** A PDF with the given number of A4 pages; each page has one line of text and metadata set. */
    private static byte[] pdf(int pages, String password) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.getDocumentInformation().setAuthor("Dr Example");
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Tab Lipitor 20mg nocte");
                    cs.endText();
                }
            }
            if (password != null) {
                doc.protect(new StandardProtectionPolicy(password, password, new AccessPermission()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void onePagePdfBecomesOneResizedJpeg() throws Exception {
        List<byte[]> pages = ImageUtils.preparePages("rx.pdf", pdf(1, null));

        assertEquals(1, pages.size());
        byte[] jpeg = pages.get(0);
        assertEquals(0xFF, jpeg[0] & 0xFF);
        assertEquals(0xD8, jpeg[1] & 0xFF);
        assertFalse(new String(jpeg, StandardCharsets.ISO_8859_1).contains("Dr Example"), "PDF metadata must not survive");
        BufferedImage page = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(ImageUtils.MAX_SIDE_PX, page.getHeight()); // A4 portrait at 200 DPI is 2339 px tall -> 1600
    }

    @Test
    void scannedImagePdfIsRendered() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            BufferedImage scan = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, scan), 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            assertEquals(2, ImageUtils.preparePages("scan.PDF", out.toByteArray()).size());
        }
    }

    @Test
    void pdfWithTooManyPagesIsRejected() throws Exception {
        InvalidImageError e = assertThrows(InvalidImageError.class,
                () -> ImageUtils.preparePages("bundle.pdf", pdf(ImageUtils.MAX_PDF_PAGES + 1, null)));
        assertTrue(e.getMessage().contains("pages"), e.getMessage());
    }

    @Test
    void passwordProtectedPdfIsRejected() throws Exception {
        InvalidImageError e = assertThrows(InvalidImageError.class, () -> ImageUtils.preparePages("locked.pdf", pdf(1, "secret")));
        assertTrue(e.getMessage().contains("password"), e.getMessage());
    }

    @Test
    void corruptPdfIsRejected() {
        byte[] corrupt = "%PDF-1.7 this is not really a pdf".getBytes(StandardCharsets.US_ASCII);
        assertThrows(InvalidImageError.class, () -> ImageUtils.preparePages("broken.pdf", corrupt));
    }

    @Test
    void textRenamedToPdfIsRejected() {
        byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidImageError.class, () -> ImageUtils.preparePages("fake.pdf", text));
    }

    @Test
    void jpegExtensionIsAccepted() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "jpg", out);
        assertEquals(1, ImageUtils.preparePages("photo.JPEG", out.toByteArray()).size());
    }

    @Test
    void textFileIsRejected() {
        byte[] text = "Tab Lipitor 20mg nocte".getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidImageError.class, () -> ImageUtils.prepare("notes.txt", text));
    }

    @Test
    void textRenamedToJpgIsRejected() {
        byte[] text = "not really an image".getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidImageError.class, () -> ImageUtils.prepare("photo.jpg", text));
    }

    @Test
    void oversizedFileIsRejected() {
        byte[] big = new byte[(int) ImageUtils.MAX_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        InvalidImageError e = assertThrows(InvalidImageError.class, () -> ImageUtils.prepare("big.jpg", big));
        assertTrue(e.getMessage().contains("10 MB"));
    }

    @Test
    void corruptJpegIsRejected() {
        byte[] corrupt = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};
        assertThrows(InvalidImageError.class, () -> ImageUtils.prepare("broken.jpg", corrupt));
    }

    @Test
    void largePngBecomesResizedJpegWithoutMetadata() throws Exception {
        byte[] jpeg = ImageUtils.prepare("rx.png", png(3200, 2400));

        assertEquals(0xFF, jpeg[0] & 0xFF);
        assertEquals(0xD8, jpeg[1] & 0xFF);
        assertFalse(new String(jpeg, StandardCharsets.ISO_8859_1).contains("Exif"), "EXIF must be stripped");
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertEquals(1600, out.getWidth());
        assertEquals(1200, out.getHeight());
    }

    @Test
    void orientationSixRotatesToPortrait() {
        BufferedImage landscape = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage rotated = ImageUtils.applyOrientation(landscape, 6);
        assertEquals(300, rotated.getWidth());
        assertEquals(400, rotated.getHeight());
    }
}
