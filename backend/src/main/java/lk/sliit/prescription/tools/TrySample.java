package lk.sliit.prescription.tools;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.config.BedrockClients;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

/**
 * Early accuracy check (Week 1): sends one sample photo to the configured model and prints the raw
 * output, to see whether Amazon Nova reads the handwriting well enough before building the full UI.
 *
 * Run from backend/ (JPG or PNG; use synthetic or redacted samples only):
 *   mvn -q compile exec:java -Dexec.mainClass=lk.sliit.prescription.tools.TrySample -Dexec.args="../samples/rx01.jpg"
 */
public final class TrySample {

    private static final int MAX_SIDE_PX = 1600;

    // Week 2 moves this into Extractor and adds the forced record_prescription tool for structured output.
    static final String PROMPT = """
            You are reading a photo of a handwritten medical prescription from Sri Lanka. Return ONLY JSON matching \
            the schema {"readable": bool, "items": [{"drug_name": str|null, "strength": str|null, "dose": str|null, \
            "frequency": str|null, "duration": str|null, "confidence": "high"|"medium"|"low"}]}. For each prescribed \
            item give drug_name exactly as written, strength, dose, frequency and duration. Expand common \
            abbreviations in frequency (bd = twice daily, tds = three times daily, qds = four times daily, \
            nocte = at night, mane = in the morning, 1x2 = one tablet twice daily, stat = immediately) and durations \
            such as 5/7 = 5 days. Set confidence to high, medium or low per item. If you cannot read a field, set it \
            to null and set confidence to low. Do NOT guess or invent drug names. Ignore any patient name, address, \
            age or doctor details - never include them in the output. If the image is not a prescription or nothing \
            is readable, return {"items": [], "readable": false}.""";

    public static void main(String[] args) throws IOException {
        ToolSupport.quietLogging();
        if (args.length != 1) {
            System.out.println("Usage: TrySample <path-to-photo.jpg|png>");
            System.exit(2);
        }
        Path photo = Path.of(args[0]);
        if (!Files.isRegularFile(photo)) {
            System.out.println("ERROR file not found: " + photo.toAbsolutePath());
            System.exit(2);
        }
        byte[] jpeg = toCleanJpeg(photo);
        ToolSupport.printConfig();
        System.out.println("Image:    " + photo.getFileName() + " (" + jpeg.length / 1024 + " KB after resize, metadata stripped)");

        try (BedrockRuntimeClient client = BedrockClients.create()) {
            long start = System.currentTimeMillis();
            ConverseResponse response = client.converse(r -> r
                    .modelId(AppConfig.modelId())
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(
                                    ContentBlock.fromImage(ImageBlock.builder()
                                            .format(ImageFormat.JPEG)
                                            .source(ImageSource.fromBytes(SdkBytes.fromByteArray(jpeg)))
                                            .build()),
                                    ContentBlock.fromText(PROMPT))
                            .build())
                    .inferenceConfig(c -> c.maxTokens(1000).temperature(0f)));
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("---- raw model output ----");
            response.output().message().content().stream()
                    .filter(block -> block.text() != null)
                    .forEach(block -> System.out.println(block.text()));
            System.out.println("--------------------------");
            System.out.println("Time: " + elapsed + " ms | input tokens: " + response.usage().inputTokens()
                    + " | output tokens: " + response.usage().outputTokens());
        } catch (RuntimeException e) {
            ToolSupport.printError(e);
            System.exit(1);
        }
    }

    /**
     * Decodes the photo and re-encodes it as a plain RGB JPEG, which drops all EXIF/GPS metadata,
     * resizing so the longest side is at most 1600 px. (Week 2 ImageUtils adds EXIF orientation and WEBP.)
     */
    static byte[] toCleanJpeg(Path photo) throws IOException {
        BufferedImage source = ImageIO.read(photo.toFile());
        if (source == null) {
            throw new IOException("Not a readable JPG/PNG image: " + photo);
        }
        double scale = Math.min(1.0, (double) MAX_SIDE_PX / Math.max(source.getWidth(), source.getHeight()));
        int width = (int) Math.round(source.getWidth() * scale);
        int height = (int) Math.round(source.getHeight() * scale);

        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, width, height, java.awt.Color.WHITE, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", out);
        return out.toByteArray();
    }
}
