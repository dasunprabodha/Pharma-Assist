package lk.sliit.prescription.extractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lk.sliit.prescription.errors.AIUnavailableError;
import lk.sliit.prescription.errors.ConfigError;
import lk.sliit.prescription.errors.InvalidResponseError;
import lk.sliit.prescription.errors.UnreadableImageError;
import lk.sliit.prescription.model.Confidence;
import lk.sliit.prescription.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ModelErrorException;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

/** CLAUDE.md 6.6 - all Bedrock calls are mocked; no real API calls. */
class ExtractorTest {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
    private static final String TC1_JSON = """
            {"readable": true, "items": [
              {"drug_name": "Lipitor", "strength": "20 mg", "dose": "1 tablet", "frequency": "at night (nocte)",
               "duration": "30 days", "confidence": "high"},
              {"drug_name": "Klacid", "strength": "500 mg", "dose": "1 tablet", "frequency": "twice daily (bd)",
               "duration": "5 days", "confidence": "medium"}]}""";

    private BedrockRuntimeClient client;
    private Extractor extractor;

    @BeforeEach
    void setUp() {
        client = mock(BedrockRuntimeClient.class);
        extractor = new Extractor(() -> client, "us.amazon.nova-2-lite-v1:0");
    }

    private static ConverseResponse response(ContentBlock... blocks) {
        return ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).content(blocks).build()))
                .usage(u -> u.inputTokens(1200).outputTokens(150).totalTokens(1350))
                .build();
    }

    private static ConverseResponse text(String text) {
        return response(ContentBlock.fromText(text));
    }

    private static ConverseResponse toolUse(String json) throws Exception {
        return response(ContentBlock.fromToolUse(ToolUseBlock.builder()
                .toolUseId("t1").name(Extractor.TOOL_NAME)
                .input(Documents.fromJson(new ObjectMapper().readTree(json)))
                .build()));
    }

    private static AwsErrorDetails details(String code) {
        return AwsErrorDetails.builder().errorCode(code).errorMessage(code + " message").build();
    }

    @Test
    void toolUseResponseGivesItems() throws Exception {
        when(client.converse(any(ConverseRequest.class))).thenReturn(toolUse(TC1_JSON));
        TokenUsage usage = new TokenUsage();

        List<Item> items = extractor.extractItems(List.of(IMAGE), usage);

        assertEquals(2, items.size());
        assertEquals("Lipitor", items.get(0).drugName());
        assertEquals("at night (nocte)", items.get(0).frequency());
        assertEquals(Confidence.HIGH, items.get(0).confidence());
        assertEquals(Confidence.MEDIUM, items.get(1).confidence());
        assertEquals(1200, usage.inputTokens());
        assertEquals(150, usage.outputTokens());
    }

    @Test
    void requestForcesToolAtTemperatureZero() throws Exception {
        when(client.converse(any(ConverseRequest.class))).thenReturn(toolUse(TC1_JSON));
        extractor.extractItems(IMAGE);

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        ConverseRequest req = captor.getValue();
        assertEquals(Extractor.TOOL_NAME, req.toolConfig().toolChoice().tool().name());
        assertEquals(0f, req.inferenceConfig().temperature());
        assertEquals(Extractor.MAX_TOKENS, req.inferenceConfig().maxTokens());
        assertEquals("jpeg", req.messages().get(0).content().get(0).image().formatAsString());
    }

    @Test
    void multiPagePdfSendsEveryPageThenThePrompt() throws Exception {
        when(client.converse(any(ConverseRequest.class))).thenReturn(toolUse(TC1_JSON));
        extractor.extractItems(List.of(IMAGE, IMAGE), new TokenUsage());

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        List<ContentBlock> content = captor.getValue().messages().get(0).content();
        assertEquals(3, content.size());
        assertEquals("jpeg", content.get(1).image().formatAsString());
        assertEquals(extractor.prompt(), content.get(2).text());
    }

    @Test
    void promptVersionIsSelectable() {
        assertEquals(Extractor.PROMPT_V1, new Extractor(() -> client, "m", "v1").prompt());
        assertEquals(Extractor.PROMPT_V2, new Extractor(() -> client, "m", "v2").prompt());
        assertEquals(Extractor.PROMPT_V3, new Extractor(() -> client, "m", "v3").prompt());
        assertEquals(Extractor.PROMPT_V2, new Extractor(() -> client, "m").prompt());
        assertTrue(Extractor.PROMPT_V3.contains("STRICT RULES") && Extractor.PROMPT_V3.endsWith("readable\": false}."));
    }

    @Test
    void textOnlyResponseFallsBackToJsonParsing() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(text(TC1_JSON));
        assertEquals(2, extractor.extractItems(IMAGE).size());
    }

    @Test
    void jsonInCodeFencesIsParsed() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(text("Here you go:\n```json\n" + TC1_JSON + "\n```"));
        assertEquals("Klacid", extractor.extractItems(IMAGE).get(1).drugName());
    }

    @Test
    void invalidJsonTwiceRaisesInvalidResponse() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(text("not json {"));
        assertThrows(InvalidResponseError.class, () -> extractor.extractItems(IMAGE));
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }

    @Test
    void invalidJsonOnceThenValidSucceeds() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(text("oops"), text(TC1_JSON));
        assertEquals(2, extractor.extractItems(IMAGE).size());
    }

    @Test
    void unreadableImageRaisesUnreadable() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(text("{\"readable\": false, \"items\": []}"));
        assertThrows(UnreadableImageError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void nullFieldMakesConfidenceLowAndMissingNameIsKept() {
        when(client.converse(any(ConverseRequest.class))).thenReturn(text("""
                {"readable": true, "items": [
                  {"drug_name": "Panadol", "strength": "500 mg", "dose": null, "frequency": "tds",
                   "duration": "3 days", "confidence": "high"},
                  {"drug_name": null, "strength": "10 mg", "dose": "1 tablet", "frequency": "mane",
                   "duration": "7 days", "confidence": "certain"}]}"""));
        List<Item> items = extractor.extractItems(IMAGE);
        assertEquals(2, items.size());
        assertEquals(Confidence.LOW, items.get(0).confidence());
        assertNull(items.get(1).drugName());
        assertEquals(Confidence.LOW, items.get(1).confidence());
    }

    @Test
    void modelErrorIsRetriedOnceThenInvalidResponse() {
        ModelErrorException bad = ModelErrorException.builder().statusCode(424)
                .awsErrorDetails(details("ModelErrorException")).build();
        when(client.converse(any(ConverseRequest.class))).thenThrow(bad).thenReturn(text(TC1_JSON));
        assertEquals(2, extractor.extractItems(IMAGE).size());

        when(client.converse(any(ConverseRequest.class))).thenThrow(bad);
        assertThrows(InvalidResponseError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void timeoutRaisesAiUnavailable() {
        when(client.converse(any(ConverseRequest.class))).thenThrow(ApiCallTimeoutException.create("timed out"));
        assertThrows(AIUnavailableError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void throttlingRaisesAiUnavailable() {
        when(client.converse(any(ConverseRequest.class))).thenThrow(ThrottlingException.builder()
                .awsErrorDetails(details("ThrottlingException")).statusCode(429).build());
        assertThrows(AIUnavailableError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void missingCredentialsRaisesConfigError() {
        when(client.converse(any(ConverseRequest.class))).thenThrow(
                SdkClientException.create("Unable to load credentials from any of the providers in the chain"));
        assertThrows(ConfigError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void accessDeniedRaisesConfigError() {
        when(client.converse(any(ConverseRequest.class))).thenThrow(AccessDeniedException.builder()
                .awsErrorDetails(details("AccessDeniedException")).statusCode(403).build());
        assertThrows(ConfigError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void invalidModelIdRaisesConfigError() {
        when(client.converse(any(ConverseRequest.class))).thenThrow(ValidationException.builder()
                .awsErrorDetails(details("ValidationException")).statusCode(400).build());
        assertThrows(ConfigError.class, () -> extractor.extractItems(IMAGE));
    }

    @Test
    void rejectedForcedToolChoiceRetriesWithAuto() throws Exception {
        ValidationException rejected = ValidationException.builder().statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationException")
                        .errorMessage("This model doesn't support the toolChoice.tool field").build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenThrow(rejected).thenReturn(text(TC1_JSON));

        assertEquals(2, extractor.extractItems(IMAGE).size());
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client, times(2)).converse(captor.capture());
        assertEquals(null, captor.getAllValues().get(1).toolConfig().toolChoice().tool());
    }
}
