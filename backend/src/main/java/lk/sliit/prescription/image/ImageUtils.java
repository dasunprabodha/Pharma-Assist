package lk.sliit.prescription.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lk.sliit.prescription.errors.InvalidImageError;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Prepares an upload for the model (CLAUDE.md 6.2): accepts JPG, PNG, WEBP or PDF up to 10 MB,
 * applies the EXIF orientation, strips all metadata (privacy), resizes so the longest side is at most
 * 1600 px, and returns RGB JPEG bytes. PDF pages are rendered to images, so the PDF's own metadata
 * (author, software, ...) is never sent either.
 */
public final class ImageUtils {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_SIDE_PX = 1600;
    public static final int MAX_PDF_PAGES = 3;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final float PDF_RENDER_DPI = 200f;
    private static final String ACCEPTED_MESSAGE = "Only JPG, PNG, WEBP or PDF files are accepted.";
    private static final float JPEG_QUALITY = 0.9f;

    private ImageUtils() {
    }

    /** One JPEG per page: a single image for photos, up to {@value #MAX_PDF_PAGES} for a PDF. */
    public static List<byte[]> preparePages(String filename, byte[] bytes) {
        checkSize(bytes);
        if ("pdf".equals(extension(filename)) && isPdf(bytes)) {
            return renderPdf(bytes);
        }
        return List.of(prepare(filename, bytes));
    }

    /** Prepares a single photo (JPG, PNG or WEBP). */
    public static byte[] prepare(String filename, byte[] bytes) {
        validate(filename, bytes);
        BufferedImage image = decode(bytes);
        image = applyOrientation(image, readOrientation(bytes));
        return toJpeg(resize(image));
    }

    static void validate(String filename, byte[] bytes) {
        checkSize(bytes);
        if (!IMAGE_EXTENSIONS.contains(extension(filename)) || !hasImageSignature(bytes)) {
            throw new InvalidImageError(ACCEPTED_MESSAGE);
        }
    }

    private static void checkSize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageError("The file is empty. Please choose a photo or PDF of the prescription.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new InvalidImageError(String.format(Locale.ROOT,
                    "The file is %.1f MB; the maximum is 10 MB. Please use a smaller file.", bytes.length / 1048576.0));
        }
    }

    private static String extension(String filename) {
        return filename == null || !filename.contains(".") ? ""
                : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isPdf(byte[] b) {
        return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }

    /** Renders each page at 200 DPI, then resizes and re-encodes it like a photo. */
    static List<byte[]> renderPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                throw new InvalidImageError("The PDF has no pages.");
            }
            if (pages > MAX_PDF_PAGES) {
                throw new InvalidImageError(String.format(Locale.ROOT,
                        "The PDF has %d pages; the maximum is %d. Please upload only the prescription page(s).",
                        pages, MAX_PDF_PAGES));
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            List<byte[]> result = new ArrayList<>();
            for (int i = 0; i < pages; i++) {
                result.add(toJpeg(resize(renderer.renderImageWithDPI(i, PDF_RENDER_DPI, ImageType.RGB))));
            }
            return result;
        } catch (InvalidPasswordException e) {
            throw new InvalidImageError("The PDF is password-protected. Please upload an unprotected copy.", e);
        } catch (IOException e) {
            throw new InvalidImageError("The PDF could not be opened - the file may be corrupt.", e);
        }
    }

    private static boolean hasImageSignature(byte[] b) {
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || webp;
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new InvalidImageError("The image could not be opened - the file may be corrupt.");
            }
            return image;
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageError iie) {
                throw iie;
            }
            throw new InvalidImageError("The image could not be opened - the file may be corrupt.", e);
        }
    }

    private static int readOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // No readable EXIF: treat as upright.
        }
        return 1;
    }

    /** EXIF orientation values 1-8 (see the EXIF spec): rotations and mirror flips. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> t.setTransform(0, 1, 1, 0, 0, 0);       // transpose: (x, y) -> (y, x)
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> t.setTransform(0, -1, -1, 0, h, w);     // transverse: (x, y) -> (h - y, w - x)
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /** Scales down so the longest side is at most 1600 px and flattens onto white RGB (drops alpha). */
    static BufferedImage resize(BufferedImage src) {
        double scale = Math.min(1.0, (double) MAX_SIDE_PX / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, Color.WHITE, null);
        g.dispose();
        return out;
    }

    /** Encodes a fresh JPEG from pixels only, so no EXIF/GPS/ICC metadata from the original survives. */
    static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new InvalidImageError("The image could not be converted.", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
