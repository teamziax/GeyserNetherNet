# Provider registration

Geyser now defaults to provider mode. It discovers
`https://agent.warden.cloud`, creates a fresh machine key, completes the advertised
anonymous proof-of-work flow and logs the assigned public address plus Warden's
optional account-claim action. An existing `mode: local` configuration remains an
explicit opt-out.

The client is not tied to Warden discovery or account APIs. `provider.url` can be
any HTTPS origin implementing `nethernet-external-signalling-v1` and advertising
a compatible operational profile. Protocol, profile, operations, authorization
schemes and limits are accepted only through same-origin discovery; redirects are
disabled. Every registration mode still proves possession of a new P-384 machine
key. The public protocol is documented in NetworkCompatible's
[NXS specification](https://github.com/teamziax/NetworkCompatible/blob/nxs-dev/docs/external-signalling/README.md).

## Endpoint and identity initialization

On first start the extension generates an EC P-256 DTLS key and self-signed
certificate using Java APIs. They are private `host-key.pem` (PKCS8) and
`host-cert.pem` files in the existing provider state directory. Restarts validate
and reuse that pair. Missing one half, mismatched keys and symbolic-link identity
files stop startup; restore the existing matching pair rather than replacing it.
The directory and key use owner-only permissions. Identity creation needs no
external OpenSSL command. Existing machine identity, registration and ticket-key
state retain their paths and values.

The default binds UDP `0.0.0.0:19133`. A concrete bind address is also the default
advertised candidate. With a wildcard bind, the extension selects an address only
when exactly one suitable interface exists. A multihomed host must set
`provider.advertised-address` or `NETHERNET_PROVIDER_ADVERTISED_ADDRESS` explicitly.
Set `advertised-port` when external forwarding differs from `udp-port`; arranging
that forwarding and choosing an address reachable by clients remain host tasks.
Wildcard addresses are never published as candidates. Keep this UDP endpoint
separate from Geyser's RakNet listener.

Existing `warden-admission-v1` configuration profiles migrate to
`nxs-admission-v1`; configured URLs, state directories, keys, tokens and endpoints
are preserved. Custom profiles are not silently replaced.

## Optional Warden ownership action

Only the optional `cloud.warden.claim` version-1 extension is interpreted as a
Warden claim action. Its HTTPS URL, text and expiry are displayed to the server
operator when present and still valid. Use the console command `nethernet claim`
to explicitly refresh the action through the advertised extension operation.
The adapter does not refresh automatically, persist action URLs, call an account
API, or require ownership for transport readiness. Providers that omit this
extension cause no claim requests and need no Warden behavior.

## Authorization modes

`provider.registration-mode` is `automatic`, `new-service` or `attach-instance`.
`provider.authorization` is `automatic`, `anonymous-proof-of-work` or
`bearer-token`. Automatic registration creates a service; automatic authorization
selects bearer when a token is present and anonymous PoW otherwise.

- Anonymous PoW requests a new service. On Warden it returns a private,
  optional claim URL.
- A bearer token plus `new-service` provisions directly into the provider account
  represented by that token, without PoW.
- A bearer token plus `attach-instance` joins an existing signalling service and
  placement. The token is reusable across independently keyed replicas.

Providers decide whether tokens are reusable, narrow, short-lived or single-use,
and how a wider credential may mint them. Token issuance is outside the protocol.

Tokens are used only for the registration challenge. They are never written to
`provider-state`, emitted by configuration `toString`, included in request JSON or
sent to discovered lifecycle endpoints. Machine identity, assigned IDs and ticket
keys are stored under `provider.state-directory`; give each logical replica its own
durable directory. Never share or copy that state between live instances on the
same physical node.

Configuration supports `authorization-token` and `authorization-token-file`.
For managed environments, these provider-neutral variables override YAML:

| Variable | Purpose |
| --- | --- |
| `NETHERNET_SIGNALLING_MODE` | `provider` or explicit `local` opt-out |
| `NETHERNET_PROVIDER_URL` | Discovery/control origin |
| `NETHERNET_PROVIDER_PROFILE` | Required advertised operational profile |
| `NETHERNET_PROVIDER_REGISTRATION_MODE` | `new-service` or `attach-instance` |
| `NETHERNET_PROVIDER_AUTHORIZATION` | PoW or bearer scheme |
| `NETHERNET_PROVIDER_TOKEN` | Bearer token value |
| `NETHERNET_PROVIDER_TOKEN_FILE` | File containing the bearer token |
| `NETHERNET_PROVIDER_REGION`, `NETHERNET_PROVIDER_POOL` | Immutable placement |
| `NETHERNET_PROVIDER_TAGS` | JSON string object, for example `{"location":"london","role":"game-proxy"}` |
| `NETHERNET_PROVIDER_LABEL` | Instance/service display label |
| `NETHERNET_PROVIDER_STATE_DIRECTORY` | Durable private state path |
| `NETHERNET_PROVIDER_BIND_ADDRESS`, `NETHERNET_PROVIDER_UDP_PORT` | Native UDP bind |
| `NETHERNET_PROVIDER_ADVERTISED_ADDRESS`, `NETHERNET_PROVIDER_ADVERTISED_PORT` | Reachable UDP candidate; advertised port 0 reuses the bind port |
| `NETHERNET_PROVIDER_CAPACITY` | Routing capacity, separate from player count |

Environment token value takes precedence over environment token file, which takes
precedence over YAML token value and YAML token file. Empty values are absent.

## Examples

- `examples/provider-local.yml`: loopback-only conformance transport.
- `examples/provider-fleet.yml`: token-authorized London proxy placement.
- `examples/provider-host.yml`: configuration distributed by a Minecraft host.
- `examples/kubernetes-proxy-fleet`: StatefulSet replicas joining and draining an
  existing proxy pool without PoW.
- `examples/server-host`: customer servers redirected to a host's own provider,
  with either a secret file/environment token or anonymous PoW.

For Warden fleet attachment, create a signal-server-scoped service key with
`game_server_bootstrap:write`, then delegate the exact region, pool and tag set.
For Warden direct provisioning, use an organisation-scoped key with
`provider_service:create`. Those scope names are Warden's mapping; another provider
can issue its own opaque credentials while using the same wire scheme.

## Lifecycle and evidence

Provider startup activates the instance, installs and acknowledges ticket keys,
publishes the host profile, sends a healthy heartbeat and then logs the public
address. Graceful Geyser shutdown waits for provider drain before closing the native
endpoint; a crash falls out of rotation when its signed lease expires. Persistent
replicas recover their assigned identity rather than registering again.

Automatic status uses Geyser's Bedrock query fields and actual session count.
Capacity/load remain separate routing inputs. Panel fixed status fields remain
authoritative. Fake transport is limited to loopback and proves only the control
contract; it cannot accept gameplay. The native `ProviderHostFactory`, stock-client
admission and real network reachability remain separate acceptance boundaries.

Build beside the owned NetworkCompatible checkout pinned by
`registration-network.properties`:

```sh
bash scripts/build-native-development.sh
# Tests against a coordinated local checkout and its compiled native repository:
bash gradlew test --max-workers=2 -PnetworkPath=/absolute/path/to/NetworkCompatible \
  -PnativeMavenRepository=/absolute/path/to/native-maven
```
