# Provider mode

Default `mode: local` preserves the existing HTTPS/local signalling lifecycle.
`mode: provider` runs registration and control on the reusable Network
`warden-signalling` client, off Netty event loops. The extension does not implement
registration cryptography or admission tokens.

Build beside the owned NetworkCompatible checkout at the commit in
`registration-network.properties`:

```sh
bash gradlew build
# Or use an explicit sibling location:
bash gradlew build -PwardenNetworkPath=/absolute/path/to/NetworkCompatible
```

CI checks out that exact Network commit and archives the extension with a full
commit label. The jar manifest records extension and Network registration source
revisions. This PR does not publish a release or change an installed server.

`examples/provider-local.yml` is an explicit fake transport configuration for a
loopback Warden conformance Worker. `examples/provider-fleet.yml` is the native
fleet configuration. Put the chosen settings in the extension's `config.yml`.
A dedicated UDP port is required; it must differ from the Geyser RakNet port.

For anonymous creation, omit bootstrap grant, region and pool. The client creates
one provisional service and logs a pending claim action; follow Warden's verified
claim and activate only after host readiness is valid. Treat the opaque claim URL
as private operator console output. For fleet attachment, create the persistent
machine key first with `ProviderIdentity.initialize`, send only the public JWK to
a controller delegated to your service and EU/proxy placement, and write its
one-use grant into a 0600 `bootstrap.grant` file. It must match this machine key.
Keep one private state directory per replica; restart reuses the IDs and hostname
without another grant or human claim. Lost/revoked authority never silently creates
a replacement public service.

Automatic status uses Geyser's Bedrock query MOTD/protocol/version/max players,
actual current Geyser sessions for players, and explicit level/game-type settings.
All seven fields can be replaced by `setServerStatus(ServerStatus)` or
`setServerStatusSupplier(Supplier<ServerStatus>)`. Join/disconnect/reload triggers
coalesce, and the supplier refreshes on the heartbeat cadence. A failed query
omits the snapshot so old status expires. Capacity/load are separate routing
inputs; displayed maxPlayers never replaces capacity. Warden panel fixed fields
remain authoritative and registration/reconnect never writes their settings.

The native integration boundary is
`org.geyser.extension.nethernet.provider.ProviderHostFactory`, discovered through
ServiceLoader. WS3 owns the native implementation and service descriptor. It
receives the existing Geyser child pipeline in `ServerBootstrap`, a dedicated UDP
bind address and state/profile options; it returns a bound channel and
`ProviderTransport`. Completion must mean the actual host is ready to export
metadata. The optional stateless profile object is preserved; WS2 invents no token
encoding or per-join delivery prerequisite. Missing native factory fails before
enrollment; fake mode is allowed only for loopback and accepts no gameplay.

Local verification covers compilation, configuration and complete status mapping.
Reusable client tests and Warden's `scripts/provider-java-bench.mjs` cover transport
of status, failure/coalescing, two-machine fleet enrollment, fixed overrides and
persistent restart. A live Geyser game connection and native endpoint validation
are separate WS3 acceptance evidence.

Owned fork parent: GeyserMC/GeyserNetherNet, common base
`83e12aa4807f3faecb57cb3b81c9d1b42be8ad1e`. Provider code uses
`codex/registration-provider`; the shared `codex/warden-integration` branch retains
the common base pending review. No Geyser core fork is needed by this slice.
