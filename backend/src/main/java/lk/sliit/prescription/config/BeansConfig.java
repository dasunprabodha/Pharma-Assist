package lk.sliit.prescription.config;

import java.util.function.Supplier;
import lk.sliit.prescription.extractor.Extractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class BeansConfig {

    /** The Bedrock client is created on first use, so the app starts even when AWS is not configured. */
    @Bean
    public Extractor extractor() {
        Supplier<BedrockRuntimeClient> lazyClient = new Supplier<>() {
            private BedrockRuntimeClient client;

            @Override
            public synchronized BedrockRuntimeClient get() {
                if (client == null) {
                    client = BedrockClients.create();
                }
                return client;
            }
        };
        return new Extractor(lazyClient, AppConfig.modelId(), AppConfig.promptVersion());
    }
}
