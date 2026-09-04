package org.geyser.extension.nethernet.provider;
import org.geyser.extension.nethernet.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ProviderConfigurationTest {
    @Test void defaultsToAnonymousWardenProviderAndSupportsBearerFleetEnvironment(@TempDir Path dir) throws Exception {
        var config = ConfigLoader.loadConfig(dir.resolve("config.yml").toFile());
        assertEquals("provider", config.mode());
        assertEquals(19133, config.provider().udpPort());
        assertFalse(config.provider().fakeTransport());
        var standalone = ProviderRuntimeConfiguration.resolve(config, dir, Map.of());
        assertEquals("https://agent.warden.cloud", standalone.origin().toString());
        assertEquals("new-service", standalone.registrationMode());
        assertEquals("anonymous-proof-of-work", standalone.authorizationScheme());

        Files.copy(Path.of("examples/provider-fleet.yml"), dir.resolve("fleet.yml"));
        var fleet = ConfigLoader.loadConfig(dir.resolve("fleet.yml").toFile());
        var runtime = ProviderRuntimeConfiguration.resolve(fleet, dir, Map.of(
            "NETHERNET_PROVIDER_TOKEN", "fleet-secret",
            "NETHERNET_PROVIDER_URL", "https://signal.example.net"
        ));
        assertEquals("https://signal.example.net", runtime.origin().toString());
        assertEquals("attach-instance", runtime.registrationMode());
        assertEquals("bearer-token", runtime.authorizationScheme());
        assertEquals(Map.of("location", "london", "role", "game-proxy"), runtime.tags());
        assertEquals(100, runtime.capacity());
        assertFalse(runtime.toString().contains("fleet-secret"));
        assertFalse(runtime.clientConfiguration().toString().contains("fleet-secret"));
    }

    @Test void acceptsAHostSecretFromConfigOrEnvironmentFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config-token.yml"), """
            mode: provider
            provider:
              url: https://signal.host.example
              registration-mode: new-service
              authorization: bearer-token
              authorization-token: config-secret
            """);
        var configured = ProviderRuntimeConfiguration.resolve(ConfigLoader.loadConfig(dir.resolve("config-token.yml").toFile()), dir, Map.of());
        assertEquals("config-secret", configured.authorizationToken());

        Files.writeString(dir.resolve("host-token"), "environment-file-secret\n");
        var environment = ProviderRuntimeConfiguration.resolve(ConfigLoader.loadConfig(dir.resolve("config-token.yml").toFile()), dir,
            Map.of("NETHERNET_PROVIDER_TOKEN_FILE", dir.resolve("host-token").toString()));
        assertEquals("environment-file-secret", environment.authorizationToken());
        assertFalse(environment.toString().contains("environment-file-secret"));
    }
}
