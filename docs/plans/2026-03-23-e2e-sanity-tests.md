# E2E Sanity Test Suite Plan

## Decision: Python pytest + requests + websockets

18 test scenarios covering registration, login, wave creation, cross-user messaging, and read state — all running against a real server (installDist or Docker).

## Test Scenarios

1. Health check (GET /healthz)
2. Register User 1 (alice)
3. Register User 2 (bob)
4. Duplicate registration rejected
5. User 1 login (captures JSESSIONID + JWT)
6. User 2 login
7. User 1 WebSocket auth (ProtocolAuthenticate)
8. User 1 creates wave (ProtocolSubmitRequest with add_participant + conversation manifest)
9. User 1 opens wave (ProtocolOpenRequest, receives snapshot)
10. User 1 adds User 2 as participant
11. User 1 writes blip ("Hello from Alice!")
12. User 2 WebSocket auth
13. User 2 sees wave in search (GET /search/)
14. User 2 opens wave, sees Alice's blip
15. Unread count > 0 for User 2
16. User 2 replies ("Hello from Bob!")
17. User 1 receives reply via WebSocket (real-time delta)
18. User 1 sees reply via fetch (GET /fetch/)

## Files to Create

| File | Purpose |
|---|---|
| `wave/src/e2e-test/requirements.txt` | pytest, requests, websockets |
| `wave/src/e2e-test/wave_client.py` | WaveServerClient: HTTP + WebSocket helpers |
| `wave/src/e2e-test/conftest.py` | pytest fixtures: server lifecycle, sessions |
| `wave/src/e2e-test/test_e2e_sanity.py` | All 18 test scenarios |
| `scripts/wave-e2e.sh` | Orchestrator: build, start, test, stop |
| `.github/workflows/e2e.yml` | CI workflow |

## Implementation Phases

- **Phase 1**: wave_client.py HTTP methods + requirements.txt
- **Phase 2**: conftest.py server lifecycle + test scenarios 1-6 (HTTP only)
- **Phase 3**: WebSocket support + scenarios 7-11 (User 1 creates/writes)
- **Phase 4**: Scenarios 12-18 (cross-user communication + read state)
- **Phase 5**: CI workflow + orchestration script

## Key Protocol Details (Codex-reviewed)

- WebSocket at ws://host:port/socket, JSON envelope: `{sequenceNumber, messageType, message}`
- **CRITICAL**: Inner message payloads use **numeric field keys** (PST/GSON serialization), NOT camelCase. E.g., `{"1": "waveletName", "2": {...delta...}}`. Must capture actual wire traffic to determine exact field numbering.
- WebSocket auth: pass JSESSIONID as cookie in upgrade headers (Jakarta path does NOT support ProtocolAuthenticate token lookup by default)
- Delta submit: ProtocolSubmitRequest with numeric-keyed fields
- Wave creation: implicit on first delta — must model full initial op set (add_participant + root conversation + blip document ops)
- Registration POST: `address=username&password=pass` (domain auto-appended; full address also accepted)
- Login POST: `address=username&password=pass` (domain auto-appended if @ missing)

## Codex Review Findings (addressed)

1. PST serializers use numeric keys, not camelCase — implementation must capture real wire format
2. Registration/login both accept bare username (domain auto-appended)
3. Jakarta WebSocket auth needs cookie-based, not token-based
4. Wave creation delta needs full initial op set, not simplified 2-op
5. pytest is fine for HTTP; consider hybrid approach for complex delta construction
