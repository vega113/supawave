# Standalone Deployment

In standalone mode, Wave terminates TLS directly and runs without a reverse proxy.

## When to choose it

Choose standalone when you want the fewest moving parts and you are comfortable managing certificate material for a Java/Jetty-based service.

## Topology

- Wave owns HTTPS on `:443`
- an optional `:80` companion path may be used for redirects or ACME HTTP-01 challenge handling, depending on the final certificate strategy
- no Caddy in the runtime topology

## Requirements

- Java 17 or later
- Wave keystore/certificate configuration
- host firewall and DNS aligned with the chosen public hostname

## Validation

Minimum checks:
```bash
curl -k -I https://<wave-host>/
curl -I https://<wave-host>/readyz
```

Confirm:
- TLS handshake succeeds
- the expected public hostname is presented
- Wave responds on its HTTPS endpoint

## Migration

From Caddy-fronted to standalone:
- move TLS ownership from Caddy to Wave
- move redirect handling to Wave or an explicit companion path
- update service startup and port bindings
- keep a rollback path to the previous proxy-fronted topology

To Caddy-fronted from standalone:
- see [caddy.md](caddy.md)

## Troubleshooting

Common issues:
- wrong hostname or certificate/keystore mismatch
- `:443` already in use
- `:80` helper/redirect path misconfigured
- DNS not pointing at the current host
