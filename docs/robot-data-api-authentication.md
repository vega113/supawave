# Robot Data API Authentication

This document explains the live robot token flow in SupaWave.

## Permanent Secret vs Expiring JWT

Each robot has two different credentials:

- The robot `consumer secret` is the long-lived credential stored with the robot account.
- The Data API token is a JWT Bearer token minted from that secret and sent on every RPC request.

For Data API calls, send:

```http
Authorization: Bearer <jwt>
```

The robot secret stays valid until you rotate it. The JWT does not.

## Requesting a Data API Token

Robots mint JWTs from the token endpoint with `client_credentials`:

```bash
curl -sS -X POST "$SUPAWAVE_BASE_URL/robot/dataapi/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=$ROBOT_ID" \
  --data-urlencode "client_secret=$ROBOT_SECRET" \
  --data-urlencode "expiry=3600"
```

Current server behavior:

- The robot must already have a configured callback URL.
- The robot must already be in verified/active registration state.
- If `expiry` is omitted, the server falls back to the robot account's `tokenExpirySeconds`.
- `tokenExpirySeconds=0` preserves legacy no-expiry behavior. New integrations should prefer explicit short-lived tokens such as `3600`.

Use `token_type=robot` when you need an Active API token for `/robot/rpc` rather than the Data API endpoint.

## JWT Structure

Data API JWTs are signed by the server's current JWT signing key.

Important JWT header fields:

- `typ`: `data-api-access`
- `kid`: the signing key id used for verification/key rotation

Important JWT payload claims:

- `sub`: the robot participant address
- `aud`: `["data-api"]`
- `scope`: `["wave:data:read", "wave:data:write"]`
- `exp`: expiry time
- `ver`: the robot account's current `tokenVersion`

## Refresh and Revocation Rules

Robots should keep the secret and treat JWTs as disposable:

- Store the permanent robot secret.
- Cache the current JWT only as a convenience.
- Renew before expiry by watching `expires_in` or decoding `exp`.
- Refresh the JWT after any HTTP `401` and retry the RPC call once with the newly issued token.

`tokenVersion` matters because the server checks it on every authenticated request:

- rotating the consumer secret bumps `tokenVersion`
- pausing a robot bumps `tokenVersion`
- deleting a robot bumps `tokenVersion`

When `tokenVersion` changes, older JWTs stop working immediately even if their `exp` is still in the future. Re-authenticate with the current secret.

## Passive Event Bundle Notes

Passive callback and fetch bundles use `EventMessageBundle` JSON.

Important fields:

- `robotAddress`: identifies the robot the bundle was built for
- `rpcServerUrl`: current servers populate this with the Data API endpoint; prefer it over hardcoding `/robot/dataapi/rpc` when it is present
- `threads`: older payloads may omit this field; treat missing `threads` as `{}`

## Related Docs

- Generated API docs: `/api-docs`
- LLM-oriented API docs: `/api/llm.txt`
- Example robot operator guide: [gpt-bot.md](gpt-bot.md)
