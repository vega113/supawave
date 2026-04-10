# gpt-bot Example Robot

`gpt-bot` is a small Java robot example for SupaWave. It exposes the Wave callback endpoints, watches for explicit mentions, and calls the local `codex` CLI in headless mode to generate a reply.

## What It Serves

- `GET /healthz` — local readiness check
- `GET /_wave/capabilities.xml` — robot capabilities for registration
- `GET /_wave/robot/profile` — profile metadata used by Wave clients
- `POST /_wave/robot/jsonrpc` — Wave callback endpoint

## Prerequisites

- Java 17+
- SBT 1.10+
- the `codex` CLI on `PATH` (only when using `GPTBOT_CODEX_ENGINE=codex`)
- `cloudflared` if you want a public callback URL

## Local Run

1. Copy the sample env file and edit it for your machine:

   `cp docs/gpt-bot.env.example .env`

2. Source it:

   `set -a; source .env; set +a`

3. Start the robot:

   `sbt "runMain org.waveprotocol.examples.robots.gptbot.GptBotServer"`

The default listener is `0.0.0.0:8087`, so the local URLs are:

- `http://localhost:8087/healthz`
- `http://localhost:8087/_wave/capabilities.xml`
- `http://localhost:8087/_wave/robot/profile`
- `http://localhost:8087/_wave/robot/jsonrpc`

Set `GPTBOT_CODEX_ENGINE=echo` to run without the Codex CLI. In echo mode, the robot
mirrors back user prompts, which is useful for testing the callback pipeline without an
external LLM dependency.

The Codex subprocess path (default engine) uses:

- model: `gpt-5.4-mini`
- reasoning effort: `low`
- non-interactive mode: `codex exec --output-last-message ... -`
- sandbox and approval checks stay on by default; set `GPTBOT_CODEX_UNSAFE_BYPASS=true` only
  on a trusted local machine

The embedded HTTP server uses a bounded worker pool. Set `GPTBOT_HTTP_WORKERS` if you want to
adjust the concurrency limit.

## Cloudflare Tunnel

For a quick public callback URL, run:

`scripts/cloudflare/gpt-bot-tunnel.sh`

That forwards your local `GPTBOT_LISTEN_PORT` and prints an ephemeral `https://...trycloudflare.com` hostname.

If you already have a named tunnel hostname, set it first:

`GPTBOT_TUNNEL_HOSTNAME=bot.example.com scripts/cloudflare/gpt-bot-tunnel.sh`

Set `GPTBOT_PUBLIC_BASE_URL` to the resulting public origin so the root page shows the same URL you register with SupaWave.
Set `GPTBOT_CALLBACK_TOKEN` to a long random secret and include it in the callback URL you register.

The callback URL you register is the public base URL with the token:

`https://<public-host>/_wave/robot/jsonrpc?token=<callback-token>`

## Registering The Robot

Use the SupaWave management API with a bearer token that can create robots:

```bash
curl -sS -X POST "$SUPAWAVE_BASE_URL/api/robots" \
  -H "Authorization: Bearer $GPTBOT_MANAGEMENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "gpt-bot@example.com",
    "description": "Example gpt-bot robot",
    "callbackUrl": "https://<public-host>/_wave/robot/jsonrpc?token=<callback-token>",
    "tokenExpiry": 3600
  }'
```

The response includes the robot `id` and `secret`. Store them in `GPTBOT_API_ROBOT_ID` and `GPTBOT_API_ROBOT_SECRET` if you want the example to fetch extra context or send replies through the active API.

After registration, verify that SupaWave can reach the robot:

```bash
curl -sS -X POST "$SUPAWAVE_BASE_URL/api/robots/$GPTBOT_API_ROBOT_ID/verify" \
  -H "Authorization: Bearer $GPTBOT_MANAGEMENT_TOKEN"
```

## Robot Token Handling

`gpt-bot` does not require you to paste a static Data API token into `.env`.

Instead, the example uses `GPTBOT_API_ROBOT_SECRET` to mint short-lived JWTs from `/robot/dataapi/token` at runtime:

- Data API tokens are requested with `expiry=3600`
- Active API tokens use the same endpoint with `token_type=robot`
- the client refreshes each cached token approximately 30 seconds before expiry

That logic lives in `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java`.

Operator guidance:

- keep the robot secret; the JWT is disposable
- expect to mint a fresh token after any `401`
- if secret rotation or another `tokenVersion` bump invalidates an older JWT early, clear the cached token and re-authenticate with the current secret

For the full token lifecycle and passive bundle field rules, see [robot-data-api-authentication.md](robot-data-api-authentication.md).

## Passive And Active Modes

`gpt-bot` supports both passive webhook replies and active API writes:

- `GPTBOT_REPLY_MODE=passive` — default; the callback response returns Wave operations directly
- `GPTBOT_REPLY_MODE=active` — the callback still receives events, but the robot posts the reply via `/robot/rpc` using `blip.createChild`

For extra read context before calling Codex:

- `GPTBOT_CONTEXT_MODE=none` — callback-only mode
- `GPTBOT_CONTEXT_MODE=data` — fetch context through `/robot/dataapi/rpc`
- `GPTBOT_CONTEXT_MODE=active` — fetch context through `/robot/rpc` with `token_type=robot`

The reusable Java client also supports `robot.search(...)` if you want to extend the example with inbox or keyword context.

## Mention Pattern

The bot responds when a blip explicitly mentions it, such as:

- `@gpt-bot`
- `gpt-bot`
- `gpt bot`

Text after the mention becomes the Codex prompt.
