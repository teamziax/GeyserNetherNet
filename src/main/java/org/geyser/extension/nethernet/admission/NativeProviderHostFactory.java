package org.geyser.extension.nethernet.admission;

import dev.kastle.netty.channel.nethernet.admission.AdmissionGate;
import dev.kastle.warden.admission.NativeProviderTransport;
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
            return NativeProviderTransport.open(bootstrap, udpBind, state.resolve("host-cert.pem"), state.resolve("host-key.pem"), AdmissionGate.Limits.defaults())
                .thenApply(transport -> new Host(transport, transport.channel()));
        } catch (Exception invalid) { return CompletableFuture.failedFuture(invalid); }
    }
}
