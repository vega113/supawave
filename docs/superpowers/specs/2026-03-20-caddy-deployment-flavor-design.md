# Caddy Deployment Flavor Design

## Goal
Support two official Wave deployment flavors that are both documented and maintained:
- standalone: Wave terminates TLS directly and runs without a reverse proxy
- caddy-fronted: Caddy terminates TLS and proxies to Wave on an internal port

The project should remain standalone-capable, while making Caddy easy and explicit for operators who want simpler TLS, redirects, and edge behavior.

## Product Stance
- Wave does not depend on Caddy at runtime.
- Wave remains deployable as a single service with direct TLS termination.
- Caddy is an official supported deployment flavor, not an embedded part of the Wave process.
- Documentation should treat standalone and caddy-fronted modes as first-class deployment choices.
- For many production operators, the recommended mode is caddy-fronted because certificate management and redirect handling are simpler and less error-prone.

## Supported Host Baseline
- The canonical deployment docs should be provider-neutral and assume a generic Linux host, not a specific VPS vendor.
- The baseline supported host should be a systemd-based x86_64 Linux machine, with Ubuntu LTS used as the reference environment for package names and examples.
- Other Linux distributions may work if they provide equivalent packages, service management, and network behavior.
- The docs should explicitly list required host prerequisites and should also provide a setup/bootstrap script for the reference Linux baseline when practical.

Minimum host-prerequisite topics to document:
- public DNS pointed at the host and ports `80`/`443` reachable
- systemd available for the non-Docker service examples
- common bootstrap tools such as `curl`, `tar`, and `openssl`
- Java 17 available for standalone Wave deployment
- Docker Engine and `docker compose` available for the Docker-based Caddy deployment path

## Why Support Caddy
Caddy solves internet-edge concerns that are adjacent to, but separate from, Wave itself:
- public TLS certificate issuance and renewal
- HTTP to HTTPS redirects
- apex and www redirects to the canonical host
- reverse proxying to an internal Wave listener
- easier origin switching and future blue/green routing

This improves operator experience without forcing Caddy into the Wave runtime model.

## Why Keep Standalone
Standalone Wave still matters because:
- it preserves a simple open-source self-hosting story
- it avoids making a reverse proxy mandatory
- it makes Wave usable in environments where operators prefer direct Jetty TLS
- it keeps deployment choices open for users who do not want another component

## Supported Modes

### 1. Standalone
Wave owns TLS directly.

Shape:
- Wave listens on `443` for HTTPS
- optional HTTP listener on `80` for redirects or ACME HTTP-01 challenge validation, depending on the chosen cert flow
- no Caddy in the runtime topology

Recommended use:
- users who want minimal moving parts
- environments where Wave is the only public service on the host
- operators who are comfortable managing certificate issuance for a Java keystore-backed server

Required docs/assets:
- standalone deployment guide
- direct-TLS config example
- certificate/keystore guidance with pre-provisioned public certificates as the baseline documented path
- standalone systemd example, including the optional `:80` listener/redirect companion path when that mode is documented
- validation checklist

### 2. Caddy-Fronted
Caddy owns the public edge and proxies to Wave.

Shape:
- Caddy listens on `80` and `443`
- Wave listens on an internal port such as `127.0.0.1:9898`, matching the current repo deployment example
- Caddy handles certs, redirects, and upstream routing

Recommended use:
- most production operators
- users who want automatic cert management and simpler redirect handling
- hosts running multiple public services

Required docs/assets:
- caddy deployment guide
- official `Caddyfile`
- official `docker compose` example for `wave + caddy`
- non-Docker systemd example for `wave + caddy`
- validation checklist

## Operator Decision Matrix
The docs should include a rendered comparison table, not only prose, using these axes:
- TLS management preference: direct Jetty TLS vs automated reverse-proxy TLS
- redirect requirements: none vs apex/www canonicalization
- reverse-proxy needs: none vs path/host proxying and future origin switching
- multi-service hosting: single public app vs multiple public services on one host
- operational complexity tolerance: fewer components vs simpler cert/edge operations

Minimum matrix format:

| Axis | Standalone | Caddy-Fronted |
| --- | --- | --- |
| TLS ownership | Wave/Jetty | Caddy |
| Cert lifecycle | operator-managed keystore | Caddy-managed ACME |
| Redirect handling | Wave/operator-specific | Caddy |
| Extra runtime component | none | Caddy |
| Best fit | minimal-component installs | most production operators |

Recommended outcome:
- prefer standalone when the operator explicitly wants the fewest components and accepts more TLS management responsibility
- prefer caddy-fronted when the operator wants simpler edge operations and is comfortable with one extra service

## Deliverables

### Documentation
Update or create:
- `README.md`
- `docs/current-state.md`
- `docs/deployment/linux-host.md`
- `docs/deployment/standalone.md`
- `docs/deployment/caddy.md`

Content requirements:
- explain what Caddy is and what role it plays relative to Wave
- explain how standalone Wave TLS differs from caddy-fronted deployment
- state clearly why Caddy is suggested for many production operators
- keep standalone documented as equally supported, not second-class

### Deployment Assets
Ship and maintain:
- official `deploy/caddy/Caddyfile`
- official `deploy/caddy/compose.yml`
- non-Docker `systemd` example for `wave + caddy`
- non-Docker `systemd` example for standalone Wave TLS
- Linux host prerequisites checklist
- reference Linux host setup/bootstrap script for host prerequisites and common packages

Asset/doc boundary:
- `deploy/` contains runnable deployment assets such as compose files, Caddyfiles, and service-unit examples
- `docs/deployment/` contains operator-facing prose guidance and validation steps
- provider-specific notes, if ever needed, should be optional examples layered on top of the provider-neutral deployment docs rather than the canonical deployment story

### Migration Guidance
Document:
- standalone to caddy-fronted migration
- caddy-fronted to standalone migration
- Cloudflare as optional DNS/proxy layer, not a requirement for either mode

Minimum migration content:
- public host and redirect changes
- port binding changes
- certificate ownership and renewal changes
- rollback path back to the previous mode

### Validation Guidance
Each mode should have:
- startup steps
- smoke checks
- expected healthy responses
- certificate/TLS verification steps
- rollback notes or recovery hints where relevant

Validation must explicitly cover:
- Docker example validation such as `docker compose config`, startup, and healthy-response checks
- systemd example validation such as unit-file linting, service startup, and healthy-response checks
- whether validation is CI-enforced, manual-only, or both for each shipped asset
- Linux host setup validation such as a dry-run or documented expected outcome for the reference setup/bootstrap script

### Troubleshooting
Include in this task a short operator FAQ covering:
- port conflicts
- certificate issuance failures
- redirect loops
- proxy-to-origin connection failures
- mismatch between public hostnames and Wave config

Troubleshooting in this task is limited to the listed scenarios. Deeper diagnostics, monitoring, and observability guidance remain out of scope.

## Out of Scope
This task should not include:
- embedding Caddy into the Wave JVM process
- making Caddy a runtime dependency of Wave
- Kubernetes or ingress-controller guidance
- load-balancer or HA topology guidance
- Caddy plugin/module development
- observability stack design for proxy metrics/logs
- non-systemd service manager coverage
- changing the Wave application protocol itself

## Recommended Documentation Structure
- `README.md`: short deployment overview and links to both supported modes
- `docs/deployment/standalone.md`: direct-TLS Wave setup and operational notes
- `docs/deployment/caddy.md`: what Caddy is, why to use it, and how it fronts Wave
- `docs/deployment/linux-host.md`: generic Linux host prerequisites, bootstrap flow, and links to both supported flavors
- `docs/current-state.md`: brief statement of the two supported deployment flavors and current recommendation

## Acceptance Criteria
- Both standalone and caddy-fronted deploys are documented as first-class supported modes.
- The docs clearly explain what Caddy is and why operators may prefer it.
- Official Caddy deployment assets are present in the repo and referenced from the docs.
- Official standalone and caddy-fronted non-Docker systemd examples are present in the repo and referenced from the docs.
- The repo documents a provider-neutral Linux host baseline and includes either a reference setup script or an explicit prerequisites checklist for that baseline.
- The docs include a decision matrix, migration guidance, and validation steps.
- The docs clearly state that Caddy is supported but not required and not embedded into Wave.
