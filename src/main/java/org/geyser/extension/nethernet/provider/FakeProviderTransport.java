package org.geyser.extension.nethernet.provider;

import com.google.gson.*;
import org.cloudburstmc.netty.signalling.ProviderTransport;
import java.util.*;
import java.util.concurrent.*;

/** Explicit loopback-only conformance fixture. Does not create native or Bedrock sessions. */
public final class FakeProviderTransport implements ProviderTransport {
    private String ticketKeyId;
    public CompletionStage<Void> installTicketKeys(List<TicketKey> keys) { ticketKeyId = keys.getLast().keyId(); return CompletableFuture.completedFuture(null); }
    public CompletionStage<JsonObject> hostProfile() {
        JsonObject p = new JsonObject(); p.addProperty("credentialKeyId", ticketKeyId); p.addProperty("dtlsFingerprint", "sha-256 " + String.join(":", Collections.nCopies(32, "11")));
        p.addProperty("sctpPort", 5000); p.addProperty("maxMessageSize", 262144);
        JsonArray candidates = new JsonArray(); JsonObject candidate = new JsonObject(); candidate.addProperty("foundation", "fixture"); candidate.addProperty("component", 1); candidate.addProperty("protocol", "udp"); candidate.addProperty("priority", 2130706431); candidate.addProperty("address", "127.0.0.1"); candidate.addProperty("port", 19133); candidate.addProperty("type", "host"); candidates.add(candidate); p.add("candidates", candidates); return CompletableFuture.completedFuture(p);
    }
    public CompletionStage<ApplyResult> applyControl(JsonObject command) { return CompletableFuture.completedFuture(command.get("kind").getAsString().equals("join-admission") ? ApplyResult.REJECTED : ApplyResult.APPLIED); }
    public List<JsonObject> pollEvents() { return List.of(); }
    public CompletionStage<Void> drain() { return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> close() { return CompletableFuture.completedFuture(null); }
}
