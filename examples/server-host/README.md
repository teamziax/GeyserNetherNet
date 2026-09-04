# Server-host provisioning

The Compose fragment points every purchased Geyser server at the host's provider
instead of Warden. Supply a per-customer token in `customer-provider-token`; the
container reads it from `/run/secrets` and registers the service directly into the
account represented by that token, with no PoW.

If the host allows anonymous registration instead, remove the token-file variable
and set `NETHERNET_PROVIDER_AUTHORIZATION=anonymous-proof-of-work`. The same
`new-service` challenge then performs the host's advertised PoW policy. A panel may
instead write the token to `authorization-token` in `examples/provider-host.yml`,
though environment or mounted-secret injection is safer.

The provider must advertise the selected protocol/profile and same-origin
operations. It does not need to implement Warden's account API: how it issues and
maps the customer token is provider-specific.
