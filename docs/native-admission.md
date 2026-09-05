# Native provider mode (experimental)

The ServiceLoader factory binds the native NXS admission endpoint and retains
Geyser's existing NetherNet Bedrock child pipeline. It creates or validates the host-owned
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
Python 3, OpenSSL development headers, CMake, Git and a C++ compiler are
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

For a disposable game test, configure `mode: provider`, `fake-transport: false`
and a `udp-port` separate from RakNet. A wildcard `bind-address` is supported with
an explicit `advertised-address`, or an unambiguous interface selection. Public
NAT mappings may also set `advertised-port`. The generated PEM pair is retained
in the private state directory; existing keys are validated and never replaced.
Use profile `nxs-admission-v1` and the configured provider URL/authorization mode.

The script verifies all source pins before building, compares the packaged JNI
bytes with the native artifact and writes `build/provenance.json` with source
revisions and the final extension hash. For unpublished coordinated local work,
set `NXS_NETWORK_SOURCE` to the clean pinned Network repository and
`NATIVE_JAVA_CHECKOUT` to the clean pinned Java/native repository. Both are still
verified against committed manifests. Source snapshots, merges and deployments
are separate from artifact creation.

Build, factory packaging and native/Worker echo do not prove a stock Minecraft
game join. Stock-client identity, token limits, both channels into gameplay,
reconnect, two-host routing and direct-join regression must still be recorded.
