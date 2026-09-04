package org.geyser.extension.nethernet;

import org.spongepowered.configurate.interfaces.meta.defaults.DefaultBoolean;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultString;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import java.util.Map;

@ConfigSerializable
public interface Config {
    @Comment("Signalling mode: local or provider")
    @DefaultString("provider")
    String mode();

    @Comment("Provider registration and instance storage")
    ProviderConfig provider();

    @ConfigSerializable
    interface ProviderConfig {
        @DefaultString("https://agent.warden.cloud") String url();
        @DefaultString("warden-admission-v1") String profile();
        @DefaultString("provider-state") String stateDirectory();
        @Comment("Registration mode: automatic, new-service or attach-instance")
        @DefaultString("automatic") String registrationMode();
        @Comment("Authorization: automatic, anonymous-proof-of-work, bearer-token or bootstrap-grant")
        @DefaultString("automatic") String authorization();
        @Comment("Provider bearer token. Prefer NETHERNET_PROVIDER_TOKEN or a token file for hosted deployments")
        @DefaultString("") String authorizationToken();
        @DefaultString("") String authorizationTokenFile();
        @Comment("Legacy one-machine grant file; automatic mode treats this as attach-instance")
        @DefaultString("") String bootstrapGrantFile();
        @DefaultString("") String region();
        @DefaultString("") String pool();
        @Comment("Provider-defined immutable routing tags")
        default Map<String, String> tags() { return Map.of(); }
        @DefaultString("Geyser") String label();
        @Comment("Separate UDP endpoint from the RakNet listener")
        @DefaultString("0.0.0.0") String bindAddress();
        default int udpPort() { return 19133; }
        @Comment("Routing admission capacity; independent of advertised maxPlayers")
        default int capacity() { return 100; }
        @Comment("World name when unavailable from the query response")
        @DefaultString("") String level();
        @Comment("Advertised game type: 0 survival, 1 creative, 2 adventure")
        default int gameType() { return 0; }
        @Comment("Explicit conformance transport; never use for a playable server")
        @DefaultBoolean(false) boolean fakeTransport();
    }

    @Comment("HTTPS settings")
    HttpsConfig https();

    @Comment("Identity settings")
    IdentityConfig identity();

    @ConfigSerializable
    interface HttpsConfig {
        @Comment("Whether HTTPS is enabled")
        @DefaultBoolean(false)
        boolean enabled();

        @Comment("Path to the https keystore file")
        @DefaultString("https.p12")
        String keystore();

        @Comment("Password for the https keystore file")
        @DefaultString()
        String password();
    }

    @ConfigSerializable
    interface IdentityConfig {
        @Comment("Path to the identity keystore file")
        @DefaultString("identity.p12")
        String keystore();

        @Comment("Password for the identity keystore file")
        @DefaultString()
        String password();
    }

    @Comment("Do not change!")
    @SuppressWarnings("unused")
    default int configVersion() {
        return 2;
    }
}
