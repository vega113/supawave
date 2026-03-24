# Wiab.pro Contacts Feature: Evaluation and Port Plan

## What the Contacts Feature Does

The Wiab.pro contacts system automatically tracks which users interact with each
other on the wave server, building a scored contact list for each participant.
The primary consumer is a participant-suggestion autocomplete widget shown when
adding people to a wave. Contacts are ranked by a time-decaying score so that
frequently and recently contacted users appear first.

### Interaction model

1. **Recording** -- `ContactsRecorder` listens on the WaveBus. When a user adds
   a participant to a wavelet (`AddParticipant` op), a "call" is recorded
   between the author and the added participant (direct) and between every
   existing participant and the new participant (indirect).
2. **Scoring** -- Each contact accumulates a `scoreBonus` in milliseconds.
   Direct outgoing calls earn the most (10 years of bonus), direct incoming
   calls earn 1 year, and indirect calls earn proportionally less. Bonuses
   decay linearly over one year so stale contacts drop off.
3. **Storage** -- `ContactStore` persists per-user contact lists. Implementations
   exist for memory (in `MemoryStore`) and flat file (`FileContactStore`). The
   on-disk format is protobuf (`ProtoContacts`).
4. **Caching** -- `ContactManagerImpl` keeps a Guava `LoadingCache` of 10 000
   users and uses a write-behind cache that flushes to the store every 20
   seconds.
5. **Serving** -- `FetchContactsServlet` (GET `/fetchContacts?timestamp=<ms>`)
   returns contacts updated since the given timestamp as JSON. The response
   includes each contact's participant address and computed score.
6. **Client** -- `RemoteContactManagerImpl` (GWT) fetches contacts
   incrementally. `ContactSelectorWidget` renders a popup autocomplete that
   filters by typed prefix, highlights matches, and lets the user pick a
   participant with keyboard or mouse.

## Components Inventory

### Server-side (relevant to port)

| Component | Wiab.pro path | Complexity | Notes |
|---|---|---|---|
| `contacts.proto` (API) | `src/.../box/contact/contacts.proto` | Low | 2 messages, ~20 lines |
| `contact-store.proto` (storage) | `src/.../persistence/protos/contact-store.proto` | Low | 2 messages, ~25 lines |
| `Contact` / `ContactImpl` | `src/.../contact/` | Low | Simple POJO |
| `Call` / `CallImpl` | `src/.../contact/` | Low | Simple POJO (used by old store format only; not needed for port) |
| `ContactManager` interface | `src/.../contact/ContactManager.java` | Low | 3 methods |
| `ContactManagerImpl` | `src/.../contact/ContactManagerImpl.java` | Medium | Guava caches + scoring math |
| `ContactStore` interface | `src/.../persistence/ContactStore.java` | Low | 3 methods |
| `MemoryStore` additions | `src/.../persistence/memory/MemoryStore.java` | Low | ConcurrentHashMap |
| `FileContactStore` | `src/.../persistence/file/FileContactStore.java` | Medium | Protobuf file I/O |
| `ContactsRecorder` | `src/.../contact/ContactsRecorder.java` | Medium | WaveBus subscriber; maps AddParticipant deltas to calls |
| `ContactsBusSubscriber` | `src/.../contact/ContactsBusSubscriber.java` | Low | Marker interface |
| `FetchContactsServlet` (jakarta) | `src-jakarta/.../rpc/FetchContactsServlet.java` | Low | ~90 lines; JSON response |
| `RemakeContactsServlet` | `src/.../rpc/RemakeContactsServlet.java` | Medium | Bulk rebuild from delta history |
| `ProtoContactsDataSerializer` | `src/.../persistence/protos/ProtoContactsDataSerializer.java` | Low | Proto <-> domain roundtrip |
| Config: `CONTACT_STORE_TYPE`, `CONTACT_STORE_DIRECTORY` | `CoreSettings` | Low | Two new config keys |
| Executor: `ContactExecutor` | `ExecutorAnnotations` / `ExecutorsModule` | **Already exists** in incubator-wave |

### Client-side (GWT; NOT ported now)

| Component | Path | Notes |
|---|---|---|
| `ContactManager` (client interface) | `wave/client/account/ContactManager.java` | |
| `ContactListener` | `wave/client/account/ContactListener.java` | |
| `AbstractContactManager` | `wave/client/account/impl/AbstractContactManager.java` | |
| `RemoteContactManagerImpl` | `box/webclient/contact/RemoteContactManagerImpl.java` | Fetches from servlet |
| `FetchContactsService[Impl]` | `box/webclient/contact/` | GWT HTTP helper |
| `FetchContactsBuilder` | `box/webclient/contact/` | GWT builder pattern |
| `ContactSelectorWidget` + UiBinder | `wave/client/wavepanel/impl/contact/` | Full GWT widget |
| `ContactWidget` + UiBinder | same | Per-contact row widget |
| `ContactInputWidget` | same | Text input with key handlers |
| `ContactModel[Impl]` / `ContactModelList` | same | Client-side view model |
| `ContactResourceLoader` | same | CSS resources |

## Key Differences from Incubator-Wave

1. **WaveBus.Subscriber signature** -- Wiab.pro passes `(WaveletName, DeltaSequence)`;
   incubator-wave passes `(ReadableWaveletData, DeltaSequence)`. The
   incubator-wave variant is actually better for our recorder because
   `ReadableWaveletData.getParticipants()` gives us the current participant set
   directly without an extra `WaveletProvider.getParticipants()` call.

2. **CoreSettings** -- Incubator-wave uses `CoreSettingsNames` (simple interface
   with constants) + Typesafe Config, not the annotated `CoreSettings` from
   Wiab.pro. Config keys go in `reference.conf`.

3. **No `LifeCycle`/`ShutdownPriority`** -- The `FileContactStore` in Wiab.pro
   uses a `LifeCycle` guard. Incubator-wave does not have that utility. We use
   simple synchronized methods instead.

4. **FetchProfilesServlet already exists** at `/profile/*` -- The contacts
   servlet is a separate endpoint (`/contacts`) that returns different data
   (scored contact list, not profile metadata).

## Assessment

### Value
**High**. The contacts feature is the primary enabler of participant
autocomplete. Without it, users must type full email addresses. For any
multi-user deployment this is table-stakes UX.

### Server-side feasibility
**Feasible now.** All dependencies exist in incubator-wave:
- `ContactExecutor` annotation and thread pool already wired
- `WaveBus` subscription mechanism works
- `PersistenceModule` pattern is well-established
- `ProtoSerializer` for JSON serialization exists
- Jakarta servlet model matches

### Client-side feasibility
**Deferred.** The GWT client widgets are tightly coupled to GWT UiBinder and
the legacy webclient. The React/modern web client replacement work is ongoing.
The servlet provides a clean JSON API that any future client can consume.

## What Is Ported (This PR)

Server-side components only:

1. **Proto definitions** -- `contacts.proto` (API) and `contact-store.proto` (storage)
2. **Domain model** -- `Contact`, `ContactImpl` interfaces and POJOs
3. **Storage** -- `ContactStore` interface, `MemoryStore` additions
4. **Manager** -- `ContactManager` interface and `ContactManagerImpl` with caching/scoring
5. **Bus recorder** -- `ContactsRecorder` adapted for incubator-wave's WaveBus signature
6. **Servlet** -- `FetchContactsServlet` (Jakarta) at `/contacts`
7. **Config** -- `contact_store_type` in `reference.conf`
8. **Wiring** -- `PersistenceModule` binding, `ServerMain` bus subscription and servlet registration

### What is NOT ported

- `FileContactStore` (deferred; memory store is sufficient for now)
- `RemakeContactsServlet` (admin tool; add later)
- All GWT client-side code (deferred to React client work)
- `Call`/`CallImpl` (not needed; scoring is computed directly)
- `ProtoContactsDataSerializer` (inlined into MemoryStore/FileContactStore)
