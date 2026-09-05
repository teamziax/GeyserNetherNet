package org.geyser.extension.nethernet.admission;

import dev.kastle.netty.channel.nethernet.admission.AdmissionGate;
import org.cloudburstmc.netty.signalling.admission.NativeProviderTransport;
import io.netty.bootstrap.ServerBootstrap;
import org.geyser.extension.nethernet.provider.ProviderHostFactory;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Fixed native endpoint using the extension's existing Bedrock child pipeline. */
public final class NativeProviderHostFactory implements ProviderHostFactory {
    @Override public CompletionStage<Host> open(ServerBootstrap bootstrap, InetSocketAddress udpBind, Map<String,String> options) {
        try {
            String directory = options.get("stateDirectory");
            if (directory == null || directory.isBlank()) throw new IllegalArgumentException("Provider stateDirectory required");
            Path state = Path.of(directory);
            ProviderEndpoint endpoint = ProviderEndpoint.resolve(udpBind, options.get("advertisedAddress"),
                Integer.parseInt(options.getOrDefault("advertisedPort", "0")));
            var identity = ProviderHostIdentity.ensure(state);
            return NativeProviderTransport.open(bootstrap, endpoint.bind(), endpoint.advertised(),
                identity.certificate(), identity.privateKey(), AdmissionGate.Limits.defaults())
                .thenApply(transport -> new Host(transport, transport.channel()));
        } catch (Exception invalid) { return CompletableFuture.failedFuture(invalid); }
    }
}
