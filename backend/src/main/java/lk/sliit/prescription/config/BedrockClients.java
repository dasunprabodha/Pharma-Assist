package lk.sliit.prescription.config;

import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/** Builds the Bedrock Runtime client from AppConfig (region, optional named profile). */
public final class BedrockClients {

    private BedrockClients() {
    }

    /** Named profile from AWS_PROFILE if set, otherwise the standard AWS credentials chain. */
    public static AwsCredentialsProvider credentialsProvider() {
        return AppConfig.profile()
                .<AwsCredentialsProvider>map(ProfileCredentialsProvider::create)
                .orElseGet(() -> DefaultCredentialsProvider.builder().build());
    }

    /** True when credentials can be resolved. Never exposes the credentials themselves. */
    public static boolean credentialsAvailable() {
        try {
            credentialsProvider().resolveCredentials();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static BedrockRuntimeClient create() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(AppConfig.region()))
                .credentialsProvider(credentialsProvider())
                .overrideConfiguration(o -> o
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .retryStrategy(r -> r.maxAttempts(2)))
                .build();
    }
}
