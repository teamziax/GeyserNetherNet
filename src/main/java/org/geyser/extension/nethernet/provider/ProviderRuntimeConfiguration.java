package org.geyser.extension.nethernet.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.netty.warden.ProviderClient;
import org.geyser.extension.nethernet.Config;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Resolves vendor-neutral environment overrides without ever rendering secrets. */
public record ProviderRuntimeConfiguration(
    String signallingMode, URI origin, String profile, Path stateDirectory, String registrationMode,
    String authorizationScheme, String authorizationToken, String region, String pool,
    Map<String, String> tags, String label, String bindAddress, int udpPort, int capacity
) {
    public static ProviderRuntimeConfiguration resolve(Config config, Path dataDirectory, Map<String, String> environment) throws IOException {
        Config.ProviderConfig provider = config.provider();
        String signallingMode = value(environment, "NETHERNET_SIGNALLING_MODE", config.mode());
        URI origin = URI.create(value(environment, "NETHERNET_PROVIDER_URL", provider.url()));
        String profile = value(environment, "NETHERNET_PROVIDER_PROFILE", provider.profile());
        Path stateDirectory = resolvePath(dataDirectory, value(environment, "NETHERNET_PROVIDER_STATE_DIRECTORY", provider.stateDirectory()));
        String token = secret(environment, "NETHERNET_PROVIDER_TOKEN", "NETHERNET_PROVIDER_TOKEN_FILE", provider.authorizationToken(), provider.authorizationTokenFile(), dataDirectory);
        String requestedMode = value(environment, "NETHERNET_PROVIDER_REGISTRATION_MODE", provider.registrationMode());
        String registrationMode = requestedMode.equals("automatic") ? ProviderClient.NEW_SERVICE : requestedMode;
        String requestedAuthorization = value(environment, "NETHERNET_PROVIDER_AUTHORIZATION", provider.authorization());
        String authorization = requestedAuthorization.equals("automatic")
            ? token == null ? ProviderClient.ANONYMOUS_PROOF_OF_WORK : ProviderClient.BEARER_TOKEN
            : requestedAuthorization;
        String region = nullable(value(environment, "NETHERNET_PROVIDER_REGION", provider.region()));
        String pool = nullable(value(environment, "NETHERNET_PROVIDER_POOL", provider.pool()));
        Map<String, String> tags = environment.containsKey("NETHERNET_PROVIDER_TAGS")
            ? parseTags(environment.get("NETHERNET_PROVIDER_TAGS"))
            : new TreeMap<>(provider.tags() == null ? Map.of() : provider.tags());
        String label = value(environment, "NETHERNET_PROVIDER_LABEL", provider.label());
        String bindAddress = value(environment, "NETHERNET_PROVIDER_BIND_ADDRESS", provider.bindAddress());
        int udpPort = integer(environment, "NETHERNET_PROVIDER_UDP_PORT", provider.udpPort());
        int capacity = integer(environment, "NETHERNET_PROVIDER_CAPACITY", provider.capacity());
        // Reuse the library's complete mode, placement and tag validation before opening native resources.
        new ProviderClient.Configuration(origin, profile, label, registrationMode, authorization, token, region, pool, tags);
        return new ProviderRuntimeConfiguration(signallingMode, origin, profile, stateDirectory, registrationMode, authorization, token, region, pool,
            Map.copyOf(tags), label, bindAddress, udpPort, capacity);
    }

    public ProviderClient.Configuration clientConfiguration() {
        return new ProviderClient.Configuration(origin, profile, label, registrationMode, authorizationScheme, authorizationToken, region, pool, tags);
    }

    private static String secret(Map<String, String> environment, String directName, String fileName, String configured, String configuredFile, Path dataDirectory) throws IOException {
        String direct = nullable(environment.get(directName));
        if (direct != null) return direct;
        String environmentFile = nullable(environment.get(fileName));
        if (environmentFile != null) return readSecret(resolvePath(dataDirectory, environmentFile));
        direct = nullable(configured);
        return direct != null ? direct : nullable(configuredFile) == null ? null : readSecret(resolvePath(dataDirectory, configuredFile));
    }

    private static String readSecret(Path path) throws IOException {
        String secret = Files.readString(path).trim();
        if (secret.isEmpty()) throw new IOException("Provider secret file is empty: " + path);
        return secret;
    }

    private static Map<String, String> parseTags(String encoded) throws IOException {
        try {
            JsonObject object = JsonParser.parseString(encoded).getAsJsonObject();
            Map<String, String> tags = new TreeMap<>();
            for (var entry : object.entrySet()) tags.put(entry.getKey(), entry.getValue().getAsString());
            return tags;
        } catch (RuntimeException invalid) {
            throw new IOException("NETHERNET_PROVIDER_TAGS must be a JSON string object", invalid);
        }
    }

    private static int integer(Map<String, String> environment, String name, int fallback) throws IOException {
        try { return environment.containsKey(name) ? Integer.parseInt(environment.get(name)) : fallback; }
        catch (NumberFormatException invalid) { throw new IOException(name + " must be an integer", invalid); }
    }

    private static Path resolvePath(Path dataDirectory, String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path.normalize() : dataDirectory.resolve(path).normalize();
    }

    private static String value(Map<String, String> environment, String name, String fallback) {
        return environment.containsKey(name) ? environment.get(name) : fallback;
    }

    private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @Override public String toString() {
        return "ProviderRuntimeConfiguration[signallingMode=" + signallingMode + ", origin=" + origin + ", profile=" + profile +
            ", registrationMode=" + registrationMode + ", authorizationScheme=" + authorizationScheme + ", region=" + region + ", pool=" + pool + "]";
    }
}
