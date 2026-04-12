| Tool | Status |
| --- | --- |
| Jenkins | [![Build Status](https://builds.apache.org/buildStatus/icon?job=wave-small_tests)](https://builds.apache.org/job/wave-small_tests) |
| Travis | [![Build Status](https://travis-ci.org/apache/incubator-wave.svg?branch=master)](https://travis-ci.org/apache/incubator-wave) |

# Apache Wave

The Apache Wave project is a stand alone wave server and rich web client
that serves as a Wave reference implementation.
Apache Wave site: http://incubator.apache.org/wave/.
This project lets developers and enterprise users run wave servers and
host waves on their own hardware. And then share those waves with other
wave servers.

## Cryptographic Software Notice

This distribution includes cryptographic software.  The country in
which you currently reside may have restrictions on the import,
possession, use, and/or re-export to another country, of
encryption software.  BEFORE using any encryption software, please
check your country's laws, regulations and policies concerning the
import, possession, or use, and re-export of encryption software, to
see if this is permitted.  See <http://www.wassenaar.org/> for more
information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms.  The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS
Export Administration Regulations, Section 740.13) for both object
code and source code.

The following provides more details on the included cryptographic
software:

  Wave requires the BouncyCastle Java cryptography APIs:
    http://www.bouncycastle.org/java.html

## Quick Start (Dev)

Requirements: Java 17+, SBT 1.10+

1) One-time bootstrap (creates config and optional dev keystore):

   - Dev HTTP only:
     `scripts/wave-bootstrap.sh`

   - Dev HTTPS (self-signed):
     `scripts/wave-bootstrap.sh --with-ssl`

2) Run the server:

   `sbt run`

   - Default dev URL: http://localhost:9898/
   - If you enabled SSL: https://localhost:9898/
   - The server/runtime path is Jakarta-only (Jetty 12 EE10).

Notes:
- WebSocket auth: In dev, `network.session_cookie_http_only = false` so the legacy web client can read `JSESSIONID` for a WebSocket fallback authenticate.
- The server renders the WebSocket address from the request Host header to avoid localhost/127.0.0.1 cookie mismatches.

## Documentation map

- Top-level docs map: [docs/README.md](docs/README.md)
- Current status and prioritized backlog: [docs/current-state.md](docs/current-state.md)
- Live GitHub Issues workflow: [docs/github-issues.md](docs/github-issues.md)
- Agent onboarding and task routing: [docs/agents/README.md](docs/agents/README.md)
- Codex-specific tool guidance: [docs/agents/tool-usage.md](docs/agents/tool-usage.md)
- Architecture references and ledgers: [docs/architecture/README.md](docs/architecture/README.md)
- Deployment and operational runbooks: [docs/runbooks/README.md](docs/runbooks/README.md)
- SBT build notes: [docs/BUILDING-sbt.md](docs/BUILDING-sbt.md)
- Historical Beads archive index: [docs/epics/README.md](docs/epics/README.md)
- gpt-bot example robot: [docs/gpt-bot.md](docs/gpt-bot.md)

SBT is the canonical build, run, and packaging entry point. Build details and caveats live in
[docs/BUILDING-sbt.md](docs/BUILDING-sbt.md).

The default Jakarta build now compiles without `net.oauth`, and the legacy
robot, Data API, and import/export OAuth surfaces are intentionally
unsupported there for now while the replacement moves under the JWT-auth epic.

## Task tracking

This repository now tracks its active roadmap in GitHub Issues:

- Human-readable overview: `docs/current-state.md`
- Live issue workflow and label conventions: `docs/github-issues.md`
- Repository issue list: `https://github.com/vega113/supawave/issues`
- Historical Beads archive: `.beads/README.md`, `docs/epics/README.md`

The `.beads/` directory remains in the repo as a read-only historical archive.
Do not create or update new task records there.

## Legacy Vagrant Notes

The checked-in Vagrant files are historical convenience environments, not the
canonical SBT/Jakarta setup path for this repo. Prefer the quick start above
plus the docs map in [`docs/README.md`](docs/README.md) for current local
setup, build, and deployment guidance.

Note:

- requires vagrant and virtual box to be installed and an internet
  connection.
- these images predate the current Java 17 baseline and may lag the supported
  SBT/Jakarta path.

### Ubuntu & Fedora ( recommended )

running `vagrant up ubuntu` or `vagrant up fedora` will create a linux box
where the project will be compiled and dist installed to `/opt/apache/wave`. In
this folder you can find the current dist source and run the server. A server
config file has been provided to allow the server to be accessible outside the
vm listening on `0.0.0.0:9898`.

To update the dist just run `vagrant up linux` where linux is either ubuntu or
fedora.

note:
`vagrant ssh linux` where linux is either ubuntu or fedora can be used for a
ssh session.

### Windows 10 (requires vagrant 1.8+)

Running `vagrant up win10` will set up a Windows 10 environment for Apache Wave.
This VM does not set up a dist; if you still need it, use the SBT tasks listed
below inside the VM.

The virtual machine will make a copy of the current source into the users
documents folder under Apache-Wave. Running the vagrant up command again will
update this folder. The standard config for vagrant testing can be located at
`scripts/vagrant/application.conf`, just copy this file to the location
of the distributions config folder.

### Note

These vagrant setups are not production use and should not be used as such.

## Setup Dev

Apache Wave can be setup for IntelliJ IDEA by importing the project as an SBT project.

## SBT Tasks

Apache Wave now targets Java 17+. SBT 1.10+ is required.

SBT tasks can be run by `sbt <task name>`.

Compile Tasks:

- **compile**: compiles all Java sources (wave module).
- **pst/compile**: compiles the PST code-generation tool.

Run Tasks:

- **run**: runs the server with the default parameters.

Distribution Tasks:
- **assembly**: builds a fat JAR for the project.
- **Universal/stage**: stages a runnable distribution at `target/universal/stage/`.
- **Universal/packageBin**: builds a zip distribution.

Smoke Tasks:
- **smokeInstalled**: stages and smoke-tests the distribution via `scripts/wave-smoke.sh`.

## Build

To compile the server:

    sbt compile

Take a look at the reference.conf to learn about configuration and possible/default values.

To run from sources:

    sbt run

The web client is accessible by default at http://localhost:9898/.

To build a staged distribution:

    sbt Universal/stage

Use `scripts/wave-smoke.sh start|status|stop` against the staged dist.

### Jetty profile

**Jakarta EE 10:**
- Standard builds and `sbt run` target Jetty 12 with Jakarta APIs.

**Docker builds:**
- Build the development image: `docker build -t wave:dev .`
- Run the container: `docker run --rm -p 9898:9898 wave:dev`

### Enabling SSL and handling sensitive data

- To enable SSL locally or in Docker, you need a Java keystore that contains a certificate/private key.
  - Example (self-signed for development only):
    `keytool -genkeypair -alias wave -keyalg RSA -keysize 2048 -validity 365 \\
      -keystore wave/config/keystore.jks -storepass changeit -dname "CN=localhost"`
  - Set `WAVE_SSL_KEYSTORE_PASSWORD` and point the server config to the keystore path.
- Never commit keystores or passwords to source control. Avoid printing secrets in logs; review your logging configuration to ensure sensitive values are not logged.
- Scrub CI/CD logs and artifacts as needed and prefer environment variables or secret stores for sensitive configuration.


### WebSocket tuning (internal clients)

Apache Wave uses an internal Jetty WebSocket client for live RPC channels. You can tune connection behavior via Typesafe Config (reference.conf/application.conf):

- wave.websocket.connectTimeoutMs: Jetty client connect timeout (default: 10000)
- wave.websocket.connectWaitMs: Max wait for a connect to complete per attempt (default: 15000)
- wave.websocket.maxBackoffMs: Cap for exponential backoff between retries (default: 8000)
- wave.websocket.jitterFraction: Jitter applied to backoff sleeps, as a fraction (default: 0.2 for ±20%)

The server applies these to system properties at startup so the internal client picks them up. Override in config/application.conf to suit your environment.


## To learn more about Wave in a Box and Wave Federation Protocol:

1. Subscribe to the wave-dev mailing list, find instructions at http://incubator.apache.org/wave/mailing-lists.html.
2. Visit the Apache Wave wiki at https://cwiki.apache.org/confluence/display/WAVE/Home.
3. Look at the white papers folder - the information is a bit old but still usable.
4. Watch the Wave Summit videos on YouTube, find the links at: https://cwiki.apache.org/confluence/display/WAVE/Wave+Summit+Talks


## Dev vs. Prod configuration

Configuration files live under `wave/config/`.

For an overview of important server flags and test environment variables (including temporary experimental flags slated for removal), see docs/CONFIG_FLAGS.md.

- `reference.conf` — all defaults and documented options.
- `application.conf` — your overrides (created by `scripts/wave-bootstrap.sh`).

Key settings:

- `core.http_frontend_addresses`: list of `host:port` listeners. Dev default is `127.0.0.1:9898`.
- `network.session_cookie_http_only`:
  - Dev default: `false` (legacy web client reads `JSESSIONID` to send a WebSocket ProtocolAuthenticate).
  - Prod recommended: `true`.
- `security.enable_ssl`:
  - Dev default: `false` (HTTP only).
  - Prod recommended: `true` with a real certificate.

### Enabling SSL

Option A (dev self‑signed):

    scripts/wave-bootstrap.sh --with-ssl

This generates `wave/config/dev-keystore.jks` (password `changeme`) and flips `enable_ssl = true`.

Option B (prod):

1. Obtain a real certificate and create/import a Java keystore (JKS or PKCS12).
2. In `application.conf`:

```
security {
  enable_ssl = true
  ssl_keystore_path = "config/your-prod.jks"
  ssl_keystore_password = ${?WAVE_SSL_KEYSTORE_PASSWORD}
}
```

Run with the password provided by environment variable:

    export WAVE_SSL_KEYSTORE_PASSWORD='...'
    sbt run


### X.509 client authentication (optional)

If your users have X.509 certificates which include their email address, you can have
them logged in automatically (with their wave ID being the same as their email address):
You can get X.509 certificates issued from any normal CA (e.g. StartSSL offer them for free).
You can get your CA's certficate from their website, though note they might provide more than 1 certificate which you need to chain before your client certificates are considered trusted.

1. Add the signing CA to your keystore file.
2. Set enable_clientauth = true
3. Set clientauth_cert_domain (to the part after the "@" in your email addresses).
4. (optional) Set disable_loginpage = true to prevent password-based logins.

Users will be automatically logged in when they access the site, with the username
taken from the email address in their certificate.

Setting up third party optional dependencies:

## To enable MongoDB:

In order to specify MongoDB in server.config as the storage option for storing deltas, accounts and attachments - you need to install according to instructions at: http://www.mongodb.org/downloads.
Or on Ubuntu Linux you can use the following command:
    sudo apt-get install mongodb-org

## Solr (status)

Solr integration is currently disabled. The code paths remain for historical reference,
but the build no longer relies on Ant and we do not ship Solr helpers. If you want to
experiment, point `core.search_type = solr` and set `core.solr_base_url`, then run a
separate Solr instance yourself. Contributions to re-enable and modernize Solr support
are welcome.

## Docker

Build the image (multi-stage, Java 17):

    docker build -t wave:dev .

Run (HTTP on 9898):

    docker run --rm -p 9898:9898 wave:dev

Mount a custom config (optional):

    docker run --rm -p 9898:9898 -v "$PWD/wave/config:/opt/wave/config" wave:dev

Enable SSL in the container (mount keystore and set env):

    docker run --rm -p 9898:9898 \
      -v "$PWD/wave/config:/opt/wave/config" \
      -e WAVE_SSL_KEYSTORE_PASSWORD=changeme wave:dev
