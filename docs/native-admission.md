# Native provider mode (experimental)

The ServiceLoader factory binds the native Warden admission endpoint and retains
Geyser's existing NetherNet Bedrock child pipeline. It loads the pre-provisioned
DTLS identity from `host-cert.pem` and `host-key.pem` in `provider.state-directory`.
The host publishes a fresh boot incarnation, actual certificate fingerprint and
fixed UDP candidate, and installs background host-specific admission keys. Client
context arrives only in the authenticated token carried by STUN. The native
adapter rejects per-join control admission.

Build the pinned NetworkCompatible integration in a disposable checkout with:

```sh
bash scripts/build-native-development.sh
```

The script checks out one immutable Network revision, builds its pinned JNI
dependencies, runs provider/native tests, builds the extension and verifies
the shaded factory service, sole native library and full revision manifest.
It never merges or pushes either source branch. Linux x86_64, JDK 8/17/21,
Python 3, OpenSSL CLI/development headers, CMake, Git and a C++ compiler are
required. This JNI development classifier links system OpenSSL; other native
platforms are not packaged until independently built and verified.

The first authenticated STUN request is retained while the peer is created, then
replayed through the mux thread as soon as that peer is ready. The client does
not need to send another connectivity check to receive its first response.
Retention happens only after bounded parsing, ticket validation and STUN
integrity verification. Each pending admission holds at most one 2048-byte
packet; the pending admission and native replay queues each allow 1024 entries.
Authenticated requests rejected because pending admission is full produce an
aggregated warning at most once every five seconds, outside the raw callback.

For a disposable game test, configure `mode: provider`, `fake-transport: false`,
an explicitly assigned `bind-address` and a `udp-port` separate from RakNet.
Wildcard `0.0.0.0` is refused: this initial profile publishes the bound interface
as its candidate. NAT/public advertised-address configuration remains a separate
profile extension. Provision the PEM key/certificate before starting the endpoint
and keep its state directory private. Do not change identity files while active.
Use the registration profile, provider URL and grant settings from the WS2 docs.

Build, factory packaging and native/Worker echo do not prove a stock Minecraft
game join. Stock-client identity, token limits, both channels into gameplay,
reconnect, two-host routing and direct-join regression must still be recorded.
