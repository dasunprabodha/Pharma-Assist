package lk.sliit.prescription.tools;

import lk.sliit.prescription.config.AppConfig;
import lk.sliit.prescription.config.BedrockClients;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

/**
 * One-off model-access check: sends one tiny text message to the configured model through the
 * Converse API and prints OK or the exact error.
 *
 * Run from backend/:
 *   mvn -q compile exec:java -Dexec.mainClass=lk.sliit.prescription.tools.CheckBedrock
 */
public final class CheckBedrock {

    public static void main(String[] args) {
        ToolSupport.quietLogging();
        ToolSupport.printConfig();

        try (BedrockRuntimeClient client = BedrockClients.create()) {
            ConverseResponse response = client.converse(r -> r
                    .modelId(AppConfig.modelId())
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText("Reply with the single word OK."))
                            .build())
                    .inferenceConfig(c -> c.maxTokens(10).temperature(0f)));

            String reply = response.output().message().content().get(0).text();
            System.out.println("OK - model replied: \"" + reply.trim() + "\" (input tokens: "
                    + response.usage().inputTokens() + ", output tokens: " + response.usage().outputTokens() + ")");
        } catch (RuntimeException e) {
            ToolSupport.printError(e);
            System.exit(1);
        }
    }
}
