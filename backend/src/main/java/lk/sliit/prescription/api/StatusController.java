package lk.sliit.prescription.api;

import java.util.LinkedHashMap;
import java.util.Map;
import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.config.BedrockClients;
import lk.sliit.prescription.data.DataLoader;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import lk.sliit.prescription.errors.PrescriptionException;
import lk.sliit.prescription.extractor.Extractor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuration status for the UI. Reports only whether credentials exist - never their values. */
@RestController
@RequestMapping("/api")
public class StatusController {

    private final DataLoader data;

    public StatusController(DataLoader data) {
        this.data = data;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("credentials_found", BedrockClients.credentialsAvailable());
        status.put("profile", AppConfig.profile().orElse(null));
        status.put("region", AppConfig.region());
        status.put("region_set", AppConfig.isRegionSet());
        status.put("model_id", AppConfig.modelId());
        status.put("model_id_set", AppConfig.isModelIdSet());
        status.put("stock_items", data.stock().size());
        status.put("interaction_rules", data.rules().size());
        return status;
    }

    /** Same check as tools/CheckBedrock: one tiny text message (about 20 tokens) to the configured model. */
    @PostMapping("/status/check-bedrock")
    public Map<String, Object> checkBedrock() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model_id", AppConfig.modelId());
        long start = System.currentTimeMillis();
        try (BedrockRuntimeClient client = BedrockClients.create()) {
            ConverseResponse response = client.converse(r -> r
                    .modelId(AppConfig.modelId())
                    .messages(Message.builder().role(ConversationRole.USER)
                            .content(ContentBlock.fromText("Reply with the single word OK.")).build())
                    .inferenceConfig(c -> c.maxTokens(10).temperature(0f)));
            result.put("ok", true);
            result.put("reply", response.output().message().content().get(0).text().trim());
        } catch (RuntimeException e) {
            RuntimeException mapped = Extractor.mapError(e);
            result.put("ok", false);
            result.put("error_type", mapped instanceof PrescriptionException pe ? pe.code() : "error");
            result.put("message", mapped.getMessage());
        }
        result.put("latency_ms", System.currentTimeMillis() - start);
        return result;
    }
}
