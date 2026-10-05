package lk.sliit.prescription.api;

import java.util.LinkedHashMap;
import java.util.Map;
import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.config.BedrockClients;
import lk.sliit.prescription.data.DataLoader;
import org.springframework.web.bind.annotation.GetMapping;
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
        status.put("credentialsFound", BedrockClients.credentialsAvailable());
        status.put("profile", AppConfig.profile().orElse(null));
        status.put("region", AppConfig.region());
        status.put("regionSet", AppConfig.isRegionSet());
        status.put("modelId", AppConfig.modelId());
        status.put("modelIdSet", AppConfig.isModelIdSet());
        status.put("stockItems", data.stock().size());
        status.put("interactionRules", data.rules().size());
        return status;
    }
}
