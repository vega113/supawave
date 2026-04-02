# gpt-bot Example Robot Design

Status: implemented
Date: 2026-04-02
Scope: a copyable Java example robot for SupaWave that receives callback events, detects explicit mentions, and posts generated replies through the Wave robot APIs.

## Implemented Shape

The example now lives in `org.waveprotocol.examples.robots.gptbot` and includes:

- `GET /healthz`
- `GET /_wave/capabilities.xml`
- `GET /_wave/robot/profile`
- `POST /_wave/robot/jsonrpc`

The default flow is passive callback replies. Optional configuration enables active API writes and context fetches through SupaWave's JSON-RPC endpoints.
