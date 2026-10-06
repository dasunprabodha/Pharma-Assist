package lk.sliit.prescription.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.sliit.prescription.errors.AIUnavailableError;
import lk.sliit.prescription.errors.ConfigError;
import lk.sliit.prescription.errors.InvalidResponseError;
import lk.sliit.prescription.errors.UnreadableImageError;
import lk.sliit.prescription.model.Item;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Feature 1 (CLAUDE.md 6.3): sends a prepared JPEG to Amazon Nova on Amazon Bedrock through the Converse API
 * and converts the structured result into validated {@link Item}s. Never logs image bytes or credentials.
 */
public class Extractor {

    public static final String TOOL_NAME = "record_prescription";

    /** v1 - the original prompt from CLAUDE.md 6.3 (baseline in docs/evaluation.md). */
    public static final String PROMPT_V1 = """
            You are reading a photo of a handwritten medical prescription from Sri Lanka. Return ONLY JSON matching \
            the schema. For each prescribed item give drug_name exactly as written, strength, dose, frequency and \
            duration. Expand common abbreviations in frequency (bd = twice daily, tds = three times daily, \
            qds = four times daily, nocte = at night, mane = in the morning, 1x2 = one tablet twice daily, \
            stat = immediately) and durations such as 5/7 = 5 days. Set confidence to high, medium or low per item. \
            If you cannot read a field, set it to null and set confidence to low. Do NOT guess or invent drug names. \
            Ignore any patient name, address, age or doctor details - never include them in the output. If the image \
            is not a prescription or nothing is readable, return {"items": [], "readable": false}. If several images \
            are given, they are pages of the same prescription: list the items from all pages once.""";

    /**
     * v2 - adds Sri Lankan prescription conventions, asks for every line (to reduce missed items),
     * and tightens when "high" confidence may be used. Compared with v1 in docs/evaluation.md.
     */
    public static final String PROMPT_V2 = """
            You are reading a photo of a handwritten prescription from a Sri Lankan clinic or hospital, for a \
            pharmacist. Record it with the record_prescription tool.
            LIST EVERY PRESCRIBED LINE, in order - tablets, capsules, syrups, creams, lotions, shampoos, injections \
            and medical supplies (syringes, needles, plasters, gloves, spirit, cotton wool). Lines are often numbered \
            (1, 2, 3, sometimes in circles) or start with a dash or dot. Never skip a line because it is hard to read: \
            write the letters you can see, set unreadable fields to null and set confidence to low.
            Fields:
            - drug_name: the product name exactly as written (brand or generic), without "Tab", "Cap" or "Inj". \
            Do NOT guess or invent a drug name.
            - strength: number and unit, e.g. "60 mg", "500 mg", "1 mg", "5%", "40 mg/ml"; null if not written.
            - dose: amount each time, e.g. "1 tablet", "1/2 tablet", "1/3 tablet", "2 tablets", "1 ml", "1 vial", \
            "02" (a supply quantity); null if not written.
            - frequency: expanded, keeping the abbreviation, e.g. "twice daily (bd)".
            - duration: expanded, e.g. "3 months (3/12)"; null if not written.
            Sri Lankan shorthand: bd = twice daily; tds = three times daily; qds = four times daily; daily or od = \
            once daily; mane = in the morning; nocte = at night; stat = immediately; sos or prn = when needed; \
            1x2 = one tablet twice daily; 2x3 = two tablets three times daily; 1+0+1 = morning and night. \
            Durations: 5/7 = 5 days, 2/52 = 2 weeks, 3/12 = 3 months, 1/12 = 1 month, 1/A or 1 yr = 1 year. \
            A number in a circle after a drug is usually the quantity to supply, not the dose.
            Confidence: high only if every written field of the item is clearly legible; medium if any part is \
            uncertain; low if the drug name itself is uncertain.
            Ignore the patient's name, age and address, the doctor's details, dates, laboratory results, blood \
            pressure and other clinical notes - never include them. If the image is not a prescription or nothing \
            is readable, return {"items": [], "readable": false}.""";

    /**
     * v3 - v2 plus rules against filling in values that are not written (the evaluation showed v1/v2 adding a
     * "5 days" duration to most items), against replacing a brand with its generic, and against splitting one item.
     */
    public static final String PROMPT_V3 = PROMPT_V2.replace(
            "Ignore the patient's name",
            """
            STRICT RULES - a pharmacist will dispense from this list:
            - Only record what is actually written on that item's line. If no duration is written, duration MUST be \
            null; if no dose is written, dose MUST be null; if no strength is written, strength MUST be null. Never \
            use a typical or default value (for example never add "5 days" or "1 tablet" unless it is written).
            - Never replace a brand name with its generic name or another drug (write "Sitavic", not "Sitagliptin").
            - One item = one drug name and the directions written next to or under it. Do not split one item into \
            several items, and do not turn a direction line (for example "for 1/A" or "after bath") into an item.
            Ignore the patient's name""");

    static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "readable": {"type": "boolean", "description": "false if the image is not a prescription or nothing is readable"},
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "drug_name": {"type": "string", "description": "exactly as written; null if unreadable"},
                      "strength": {"type": "string", "description": "e.g. 20 mg; null if unreadable"},
                      "dose": {"type": "string", "description": "e.g. 1 tablet; null if unreadable"},
                      "frequency": {"type": "string", "description": "expanded, e.g. twice daily (bd); null if unreadable"},
                      "duration": {"type": "string", "description": "e.g. 5 days; null if unreadable"},
                      "confidence": {"type": "string", "enum": ["high", "medium", "low"]}
                    },
                    "required": ["drug_name", "strength", "dose", "frequency", "duration", "confidence"]
                  }
                }
              },
              "required": ["readable", "items"]
            }""";

    static final int MAX_TOKENS = 1000;
    private static final int MAX_ATTEMPTS = 2;
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Supplier<BedrockRuntimeClient> clientSupplier;
    private final String modelId;
    private final ToolSpecification toolSpec;
    private final String prompt;
    private volatile boolean forcedToolSupported = true;

    public Extractor(Supplier<BedrockRuntimeClient> clientSupplier, String modelId) {
        this(clientSupplier, modelId, "v2");
    }

    /** @param promptVersion "v1" (baseline), "v2" (Sri Lankan conventions) or "v3" (v2 + no invented values). */
    public Extractor(Supplier<BedrockRuntimeClient> clientSupplier, String modelId, String promptVersion) {
        this.clientSupplier = clientSupplier;
        this.modelId = modelId;
        this.prompt = switch (promptVersion == null ? "" : promptVersion.toLowerCase()) {
            case "v1" -> PROMPT_V1;
            case "v3" -> PROMPT_V3;
            default -> PROMPT_V2;
        };
        try {
            this.toolSpec = ToolSpecification.builder()
                    .name(TOOL_NAME)
                    .description("Record the items read from the prescription photo.")
                    .inputSchema(ToolInputSchema.fromJson(Documents.fromJson(JSON.readTree(SCHEMA_JSON))))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String modelId() {
        return modelId;
    }

    public String prompt() {
        return prompt;
    }

    public String promptVersion() {
        return prompt == PROMPT_V1 ? "v1" : prompt == PROMPT_V2 ? "v2" : "v3";
    }

    public List<Item> extractItems(byte[] jpeg) {
        return extractItems(List.of(jpeg), new TokenUsage());
    }

    /**
     * Calls the model (retrying once on an invalid response) and returns validated items.
     *
     * @param pages one JPEG per page (a photo is a single page)
     */
    public List<Item> extractItems(List<byte[]> pages, TokenUsage usage) {
        for (int attempt = 1; ; attempt++) {
            ConverseResponse response;
            try {
                response = callModel(pages);
            } catch (InvalidResponseError e) {
                // Bedrock ModelErrorException: the model produced a malformed answer - retry once, like invalid JSON.
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                continue;
            }
            if (response.usage() != null) {
                usage.add(response.usage().inputTokens(), response.usage().outputTokens());
            }
            JsonNode result;
            try {
                result = parseResponse(response);
            } catch (InvalidResponseError e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                continue;
            }
            return toItems(result);
        }
    }

    private ConverseResponse callModel(List<byte[]> pages) {
        BedrockRuntimeClient client;
        try {
            client = clientSupplier.get();
        } catch (SdkClientException e) {
            throw new ConfigError("AWS is not configured: " + e.getMessage(), e);
        }
        try {
            return client.converse(buildRequest(pages, forcedToolSupported));
        } catch (BedrockRuntimeException e) {
            if (forcedToolSupported && isToolChoiceRejected(e)) {
                // Some models reject a forced tool choice; fall back to "auto" plus text parsing.
                forcedToolSupported = false;
                return callModel(pages);
            }
            throw mapError(e);
        } catch (SdkClientException e) {
            throw mapError(e);
        }
    }

    ConverseRequest buildRequest(List<byte[]> pages, boolean forceTool) {
        List<ContentBlock> content = new ArrayList<>();
        for (byte[] jpeg : pages) {
            content.add(ContentBlock.fromImage(ImageBlock.builder()
                    .format(ImageFormat.JPEG)
                    .source(ImageSource.fromBytes(SdkBytes.fromByteArray(jpeg)))
                    .build()));
        }
        content.add(ContentBlock.fromText(prompt));
        ToolChoice choice = forceTool
                ? ToolChoice.fromTool(SpecificToolChoice.builder().name(TOOL_NAME).build())
                : ToolChoice.fromAuto(a -> { });
        return ConverseRequest.builder()
                .modelId(modelId)
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(content)
                        .build())
                .toolConfig(ToolConfiguration.builder()
                        .tools(Tool.fromToolSpec(toolSpec))
                        .toolChoice(choice)
                        .build())
                .inferenceConfig(c -> c.temperature(0f).maxTokens(MAX_TOKENS))
                .build();
    }

    private static boolean isToolChoiceRejected(BedrockRuntimeException e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return "ValidationException".equals(errorCode(e)) && msg.contains("tool") && msg.contains("choice");
    }

    /** Reads the toolUse input; if there is none, parses the text output after stripping code fences. */
    static JsonNode parseResponse(ConverseResponse response) {
        List<ContentBlock> content = Optional.ofNullable(response.output())
                .map(o -> o.message())
                .map(Message::content)
                .orElse(List.of());

        for (ContentBlock block : content) {
            ToolUseBlock toolUse = block.toolUse();
            if (toolUse != null && toolUse.input() != null) {
                return validateShape(Documents.toJson(toolUse.input()));
            }
        }
        StringBuilder text = new StringBuilder();
        content.stream().filter(b -> b.text() != null).forEach(b -> text.append(b.text()));
        return validateShape(parseJsonText(text.toString()));
    }

    static JsonNode parseJsonText(String text) {
        String candidate = text.trim();
        Matcher fence = CODE_FENCE.matcher(candidate);
        if (fence.find()) {
            candidate = fence.group(1).trim();
        } else {
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                candidate = candidate.substring(start, end + 1);
            }
        }
        try {
            return JSON.readTree(candidate);
        } catch (JsonProcessingException e) {
            throw new InvalidResponseError("The model output is not valid JSON.", e);
        }
    }

    private static JsonNode validateShape(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new InvalidResponseError("The model output is not a JSON object.");
        }
        JsonNode items = root.get("items");
        if (items != null && !items.isNull() && !items.isArray()) {
            throw new InvalidResponseError("'items' is not a list.");
        }
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                if (!item.isObject()) {
                    throw new InvalidResponseError("An item is not a JSON object.");
                }
            }
        }
        return root;
    }

    static List<Item> toItems(JsonNode root) {
        JsonNode items = root.path("items");
        boolean readable = !root.has("readable") || root.get("readable").asBoolean(true);
        if (!readable || !items.isArray() || items.isEmpty()) {
            throw new UnreadableImageError("The model found no readable prescription in the photo.");
        }
        List<Item> result = new ArrayList<>();
        for (JsonNode n : items) {
            result.add(Item.validated(text(n, "drug_name"), text(n, "strength"), text(n, "dose"),
                    text(n, "frequency"), text(n, "duration"), text(n, "confidence"), null));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Maps AWS SDK errors to the project's exceptions (CLAUDE.md 6.1 / 6.3). */
    public static RuntimeException mapError(RuntimeException e) {
        if (e instanceof BedrockRuntimeException bre) {
            String code = String.valueOf(errorCode(bre));
            String detail = code + ": " + (bre.awsErrorDetails() != null ? bre.awsErrorDetails().errorMessage() : bre.getMessage());
            return switch (code) {
                case "AccessDeniedException", "ValidationException", "ResourceNotFoundException",
                     "UnrecognizedClientException", "ExpiredTokenException", "InvalidSignatureException" ->
                        new ConfigError(detail, e);
                case "ModelErrorException" -> new InvalidResponseError(detail, e);
                case "ThrottlingException", "ServiceUnavailableException", "ModelNotReadyException",
                     "InternalServerException", "ModelTimeoutException", "ServiceQuotaExceededException" ->
                        new AIUnavailableError(detail, e);
                default -> bre.statusCode() >= 500 || bre.statusCode() == 429
                        ? new AIUnavailableError(detail, e) : new ConfigError(detail, e);
            };
        }
        if (e instanceof ApiCallTimeoutException || e instanceof ApiCallAttemptTimeoutException) {
            return new AIUnavailableError("The request to Amazon Bedrock timed out.", e);
        }
        if (e instanceof SdkClientException) {
            if (hasIoCause(e)) {
                return new AIUnavailableError("Cannot reach Amazon Bedrock: " + e.getMessage(), e);
            }
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("credentials") || msg.contains("region") || msg.contains("profile")) {
                return new ConfigError("AWS is not configured: " + e.getMessage(), e);
            }
            return new AIUnavailableError(e.getMessage(), e);
        }
        return e;
    }

    private static String errorCode(BedrockRuntimeException e) {
        return e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
    }

    private static boolean hasIoCause(Throwable e) {
        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            if (t instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
