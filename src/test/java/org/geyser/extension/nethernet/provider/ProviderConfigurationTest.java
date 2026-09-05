package org.geyser.extension.nethernet.provider;
import org.geyser.extension.nethernet.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        assertEquals("nxs-admission-v1", standalone.profile());
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

    @ParameterizedTest @ValueSource(ints = {1, 3})
    void upgradesTheLegacyProfileWithoutReplacingOperatorIdentityOrEndpointSettings(int version, @TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.yml"), """
            config-version: %d
            mode: provider
            operator-extra: preserved
            provider:
              profile: warden-admission-v1
              state-directory: established-instance
              bind-address: 100.117.6.116
              udp-port: 19133
              authorization-token: preserved-token
            """.formatted(version));
        var runtime = ProviderRuntimeConfiguration.resolve(ConfigLoader.loadConfig(dir.resolve("config.yml").toFile()), dir,
            Map.of("NETHERNET_PROVIDER_ADVERTISED_ADDRESS", "203.0.113.9", "NETHERNET_PROVIDER_ADVERTISED_PORT", "29133"));
        assertEquals("nxs-admission-v1", runtime.profile());
        assertEquals(dir.resolve("established-instance"), runtime.stateDirectory());
        assertEquals("preserved-token", runtime.authorizationToken());
        assertEquals("100.117.6.116", runtime.bindAddress());
        assertEquals(19133, runtime.udpPort());
        assertEquals("203.0.113.9", runtime.advertisedAddress());
        assertEquals(29133, runtime.advertisedPort());
        assertFalse(Files.readString(dir.resolve("config.yml")).contains("warden-admission-v1"));
        assertTrue(Files.readString(dir.resolve("config.yml")).contains("operator-extra: preserved"));
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
