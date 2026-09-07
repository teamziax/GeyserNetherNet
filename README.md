# Geyser NetherNet Extension
This is a Geyser extension that adds support for the NetherNet protocol via HTTP signaling.

This is a work in progress and is not yet ready for production use. The maintained branch builds the exact Network/native revisions recorded in the source manifests; see [PROVIDER.md](PROVIDER.md).

## Provider quick start

Start Geyser with the extension. Set the advertised UDP address on multihomed or NAT hosts as described in [PROVIDER.md](PROVIDER.md). The generated
defaults register with Warden using proof of work and the log prints the assigned
public address. Set `NETHERNET_PROVIDER_URL` to use another compatible signalling
provider; bearer-token, server-host and Kubernetes fleet examples are documented
in [PROVIDER.md](PROVIDER.md).

## Provider game admission reporting
In provider mode, native ticket events establish transport progress. The extension additionally reports `ticket.game_rejected` when Geyser sends a Bedrock disconnect before play-ready admission, and `ticket.game_joined` on Geyser's primary-session `SessionJoinEvent`. An unsupported network protocol reports `unsupported_version`; other explicit rejections report `server_rejected`. Client closes and disconnects after joining are not game rejections.

These events use signed NXS `outcomes` batches and the channel's authenticated ticket ID. No disconnect text or player identity is included. Reporting retains only channel-owned flags and a bounded 256-event queue, with drops visible in `nethernet diagnostics`. Heartbeat declares `gameOutcomes: "available"`. Transport progress and game admission remain separate evidence.

## Provider check-in scheduling

The native provider follows the bounded schedule negotiated through NXS discovery
and heartbeat responses. The provider chooses its activity and lease policy.

Local player/status changes wake reporting without waiting for the idle timer.
Unchanged local observations do not send requests. Heartbeat carries the host
profile, installed admission keys and applied provider state; there is no separate
control poll or readiness request. A JVM shutdown hook gives the asynchronous provider
up to 20 seconds to finish its drain notification on normal process termination,
including SIGTERM. Extension shutdown itself remains asynchronous. Hosts
learn a changed policy on their next report; they cannot receive a revised schedule
while silent. The control plane must implement the advertised NXS lease contract.

## Local-signalling setup

Set `mode: local` before following these steps. Local mode owns its HTTPS signalling
and identity certificates; provider mode does not use these keystores.

1. Start Geyser once with the extension enabled. On first start it generates `config.yml` in the extension's data folder, then stop Geyser. (The listener will fail to start until the keystores below are in place, that's expected on this first run.)
2. Place your HTTPS keystore file (`https.p12`) and identity keystore file (`identity.p12`) in the extension's data folder.
3. Edit `config.yml` to set the correct paths and passwords for your keystore files.
4. Start Geyser again.

## Keystore generation
### HTTPS - Development Local CA
Create a local CA:
```bash
openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca.crt \
    -subj "/CN=Dev Local CA" -days 3650
```

Create a server key and CSR:
```bash
openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr \
    -subj "/CN=localhost"
```

Create `san.ext` listing the names/IPs the cert is valid for. `localhost` and `127.0.0.1` are included; **add any other hostnames or IPs you'll connect to**:
```ini
subjectAltName = DNS:localhost, IP:127.0.0.1
# e.g. add a LAN address or hostname:
# subjectAltName = DNS:localhost, DNS:myhost.local, IP:127.0.0.1, IP:192.168.1.50
```

Sign the server cert with the CA:
```bash
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out server.crt -days 730 -extfile san.ext
```

Bundle key + cert (+ CA) into the keystore:
```bash
openssl pkcs12 -export -in server.crt -inkey server.key -certfile ca.crt \
    -name server -out https.p12 -passout pass:changeit
```

Clients must trust `ca.crt` for the connection to succeed (import it into the connecting machine's trust store).

### HTTPS - Public CA
If you already have a certificate and private key (e.g. from Let's Encrypt or a
public CA), skip the CA/self-signed steps above and bundle them directly. Include
the intermediate chain so clients receive the full path:
```bash
openssl pkcs12 -export -inkey privkey.pem -in cert.pem -certfile chain.pem \
    -name server -out https.p12 -passout pass:changeit
```

- `privkey.pem` - certificate private key
- `cert.pem` - certificate public key
- `chain.pem` - intermediate CA cert(s)

With Let's Encrypt, `fullchain.pem` already holds the leaf plus intermediates, so pass it as `-in` and drop `-certfile`:
```bash
openssl pkcs12 -export -inkey privkey.pem -in fullchain.pem \
    -name server -out https.p12 -passout pass:changeit
```

### Identity keystore
The CN (`Your Server`) becomes the identity domain shown to players, so set it to something recognisable.
```bash
keytool -genkeypair -alias identity -keyalg EC -groupname secp384r1 \
        -storetype PKCS12 -keystore identity.p12 -storepass changeit \
        -dname "CN=Your Server" -validity 3650
```
