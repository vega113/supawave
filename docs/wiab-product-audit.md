# Wiab.pro Product Feature Audit: Tags, Archive, and Stored Searches

Date: 2026-03-23

## Purpose

Audit the tags, archive (folder operations), and stored-search features in
Wiab.pro to determine what already exists in Apache Wave, what is missing,
and what should be imported.

---

## 1. Tags

### 1.1 What exists in Wiab.pro

Tags in Wiab.pro are a **full-stack feature**: model, event system, rendering,
and interactive UI controls.

**Model layer** (shared with Apache Wave -- already upstream):
- `TagsDocument` -- XML document backing store (`<tag>name</tag>` elements)
- `TagsDocument.Listener` -- add/remove callbacks

**Model layer** (Wiab.pro only -- NOT upstream):
- `Conversation.getTags()` / `addTag(String)` / `removeTag(String)`
- `WaveletBasedConversation` implementation of getTags/addTag/removeTag,
  backed by `Wavelet.getTags()` / `addTag()` / `removeTag()`
- `ObservableConversation.TagListener` (onTagAdded / onTagRemoved with
  `WaveletOperationContext`)
- Dedicated listener registry: `addTagListener()` / `removeTagListener()`

**Client view layer** (Wiab.pro only -- NOT upstream):
- `TagsView` / `IntrinsicTagsView` -- view interfaces for tag collection
- `TagView` / `IntrinsicTagView` -- view interface for individual tag
- `TagsViewBuilder` -- DOM builder for the tag collection
- `TagViewBuilder` -- DOM builder for a single tag
- `TagsViewImpl` / `TagViewImpl` -- view implementations
- `TagsDomImpl` / `TagDomImpl` -- DOM accessors
- `TagUpdateRenderer` -- live rendering of tag add/remove events
- `TagController` -- interactive add/remove UI with dialog prompts
- i18n: `TagMessages` + property files (en, es, fr, ru, sl) in two locations
  (wavepanel view and wavepanel edit)
- CSS classes for tag styling and add-button

**Server-side rendering** (Wiab.pro only):
- `TagsViewImpl` / `TagsViewBuilder` in `box.server.rpc.render.view`

**Supplement (read-state tracking)** -- both codebases:
- `isTagsUnread(WaveletId)` / `wasTagsEverRead(WaveletId)` /
  `markTagsAsRead(WaveletId)` -- present in both Wiab.pro and Apache Wave
  `ReadableSupplementedWave` / `WritableSupplementedWave`.

### 1.2 What exists in Apache Wave

- `TagsDocument` -- identical functionality (model-level tag storage)
- `TagsDocumentTest` -- unit test
- `Tags` / `Wavelet` tag accessors in the Robot API (`com.google.wave.api`)
- Supplement tag-read tracking (`isTagsUnread`, etc.)
- **No** `Conversation.getTags/addTag/removeTag`
- **No** `ObservableConversation.TagListener`
- **No** client-side tag views, rendering, or controller
- **No** server-side tag rendering

### 1.3 Gap analysis

| Layer | Gap |
|-------|-----|
| `Conversation` interface | Missing `getTags()`, `addTag()`, `removeTag()` |
| `WaveletBasedConversation` | Missing tag operations and `TagListener` dispatch |
| `ObservableConversation` | Missing `TagListener` + registration methods |
| Client view interfaces | Missing `TagsView`, `TagView`, `IntrinsicTag*View` |
| Client DOM builders | Missing `TagsViewBuilder`, `TagViewBuilder`, DOM impls |
| Client controller | Missing `TagController` (add/remove dialogs) |
| Client renderer | Missing `TagUpdateRenderer` |
| i18n | Missing all `TagMessages` + property files |

### 1.4 Assessment

- **Scope**: ~20 files to port
- **Dependencies**: Model changes (server-safe), client GWT view/controller
  changes (GWT-dependent)
- **Value**: High -- tags are a core organizational feature
- **Effort**: Medium -- model changes are clean; client views follow existing
  patterns but require GWT compilation
- **Recommendation**: **Import** -- model layer first, client layer second

---

## 2. Archive (Folder Operations)

### 2.1 What exists in Wiab.pro

Archive in Wiab.pro is implemented as a **folder servlet + client service**
on top of the supplement's existing `archive()` / `inbox()` methods.

**Server:**
- `FolderServlet` (javax.servlet + jakarta.servlet variants) -- REST endpoint
  at `/folder/?operation=move&folder=archive&waveId=...`
- Opens the user-data wavelet, creates a `SupplementedWaveImpl`, calls
  `supplement.archive()` or `supplement.inbox()`, submits deltas
- Registered in `ServerMain`

**Client:**
- `FolderOperationService` -- async callback interface
- `FolderOperationServiceImpl` -- GWT `RequestBuilder` impl hitting `/folder`
- `FolderOperationBuilder` / `FolderOperationBuilderImpl` -- URL builder for
  folder operations
- `SearchPresenter` -- toolbar buttons "To Archive" / "To Inbox" that invoke
  folder operations on selected waves

**Query infrastructure:**
- `QueryCondition` class with `INBOX`, `ARCHIVE`, `PUBLIC` constants
- `QueryCondition.Field` enum includes `IN`, `TAG`, `CONTENT`, etc.
- `SearchQuery` with `isInbox()` / `isArchive()` / `isPublic()` helpers
- `QueryParser` -- parses query strings into `SearchQuery`
- `InboxState` enum (INBOX, MUTE, ARCHIVE) in `model.conversation`

### 2.2 What exists in Apache Wave

- `SupplementedWave.archive()` / `inbox()` / `isArchived()` / `isInbox()` --
  the **underlying model** for archive/inbox state exists and works
- `SimpleSearchProviderImpl` uses `in:inbox` as the default search, recognizes
  `in:` queries via `TokenQueryType`
- `TokenQueryType` enum has `IN`, `WITH`, `CREATOR`, `ORDERBY`, `ID` but
  **no TAG**
- **No** `FolderServlet` or REST endpoint for move-to-archive/inbox
- **No** client-side `FolderOperationService` or toolbar archive buttons
- **No** `QueryCondition`, `SearchQuery`, `QueryParser` (Wiab.pro's
  structured query model)
- **No** `InboxState` enum

### 2.3 Gap analysis

| Layer | Gap |
|-------|-----|
| Server REST endpoint | Missing `FolderServlet` |
| Client folder service | Missing `FolderOperation*` (4 files) |
| Client toolbar | Missing archive/inbox buttons in `SearchPresenter` |
| Query infrastructure | Missing `QueryCondition`, `SearchQuery`, `QueryParser` |
| Query token types | `TokenQueryType` missing `TAG`, `CONTENT`, `TITLE` fields |
| InboxState enum | Missing `InboxState` enum |

### 2.4 Assessment

- **Scope**: ~10 server/client files
- **Dependencies**: Server servlet (straightforward), client GWT
  (toolbar integration)
- **Value**: High -- archive is essential for inbox management
- **Effort**: Low-Medium -- the hard part (supplement model) already exists;
  this is plumbing and UI
- **Recommendation**: **Import** -- server endpoint first, then client buttons

---

## 3. Stored (Saved) Searches

### 3.1 What exists in Wiab.pro

Stored searches let users persist custom search queries (name + query string)
in their account.

**Proto / model:**
- `searches.proto` -- `Searches` message with repeated `SearchesItem`
  (name + query)
- `SearchesItem` / `SearchesItemImpl` -- generated Java interface and impl
- `SearchesItemJsoImpl` / `SearchesJsoImpl` -- GWT JSO variants
- `account-store.proto` imports `searches.proto`; `HumanAccountData` has
  `getSearches()` / `setSearches()`

**Server:**
- `SearchesServlet` (javax + jakarta variants) -- GET returns user's saved
  searches as JSON; POST stores them
- Uses `AccountStore` for persistence (MongoDB / file)
- Default searches: inbox, archive, public
- `ProtoAccountDataSerializer` extended to serialize/deserialize searches

**Client:**
- `SearchesService` -- async get/store interface
- `RemoteSearchesService` -- GWT HTTP impl hitting `/searches`
- `SearchesEditorPopup` -- popup with list management (add, modify, remove,
  reorder)
- `SearchesItemEditorPopup` -- name + query editor for a single search
- `SearchesModifyEvent` / `SearchesModifyEventHandler` -- GWT event bus
- `SearchPresenter` integration -- uses searches to populate sidebar tabs
  (inbox, archive, public, custom)

### 3.2 What exists in Apache Wave

- `SearchPresenter` -- hardcoded `DEFAULT_SEARCH = "in:inbox"`, no notion
  of multiple saved searches
- `SearchServlet` + `AbstractSearchServlet` -- execute queries, no persistence
- `AccountStore` / `HumanAccountData` -- **no** `getSearches()` /
  `setSearches()` methods
- **No** `searches.proto`
- **No** `SearchesServlet`
- **No** client-side `SearchesService`, `RemoteSearchesService`, or
  editor popups
- **No** `SearchesModifyEvent` / handler

### 3.3 Gap analysis

| Layer | Gap |
|-------|-----|
| Proto definition | Missing `searches.proto` |
| Account model | Missing `getSearches()` / `setSearches()` on `HumanAccountData` |
| Account serialization | `ProtoAccountDataSerializer` needs extension |
| Server servlet | Missing `SearchesServlet` (+ jakarta variant) |
| Client service | Missing `SearchesService`, `RemoteSearchesService` |
| Client UI | Missing `SearchesEditorPopup`, `SearchesItemEditorPopup` |
| Client events | Missing `SearchesModifyEvent*` |
| SearchPresenter | Missing multi-search tab support |

### 3.4 Assessment

- **Scope**: ~15 files (proto, server, client, UI popups)
- **Dependencies**: Proto generation, account store schema change, GWT client
- **Value**: Medium-High -- improves power-user workflow significantly
- **Effort**: Medium -- proto changes touch persistence layer; UI is
  self-contained
- **Recommendation**: **Import** -- proto + server first, client second

---

## 4. Import Recommendations Summary

| Feature | Recommendation | Priority | Notes |
|---------|---------------|----------|-------|
| Tags: model layer | **Import** | P1 | `Conversation` + `ObservableConversation` tag API |
| Tags: client views | **Import** | P2 | Depends on model; GWT-coupled |
| Archive: FolderServlet | **Import** | P1 | Small, self-contained |
| Archive: client toolbar | **Import** | P2 | Depends on FolderServlet |
| Archive: query infrastructure | **Import** | P1 | `QueryCondition`, `SearchQuery`, `QueryParser` |
| Stored searches: proto + server | **Import** | P2 | Extends account store |
| Stored searches: client UI | **Import** | P3 | Depends on server; GWT-coupled |
| InboxState enum | **Import** | P1 | Trivial, useful |

### Features to defer

None -- all three features are recommended for import. The existing port plan
noted "Tags and basic archiving: Already available upstream" but this audit
reveals that while the **storage/model primitives** exist, the **user-facing
plumbing** (Conversation API, views, controllers, servlets, toolbar) is absent.

---

## 5. Suggested Import Order

### Phase 1 -- Model and server plumbing (no GWT dependency)

1. **Tags model**: Add `getTags()`, `addTag()`, `removeTag()` to `Conversation`
   and `WaveletBasedConversation`; add `TagListener` to
   `ObservableConversation`
2. **InboxState enum**: Port `InboxState` to `model.conversation`
3. **Query infrastructure**: Port `QueryCondition`, `SearchQuery`,
   `QueryParser`; extend `TokenQueryType` with `TAG`, `CONTENT`, `TITLE`
4. **FolderServlet**: Port servlet (javax + jakarta); wire in `ServerMain`
5. **Stored searches proto + server**: Add `searches.proto`; extend
   `HumanAccountData`; port `SearchesServlet`; update `account-store.proto`
   and serializer

### Phase 2 -- Client views and controllers (GWT-dependent)

6. **Tags client**: Port `TagsView`, `TagView`, `TagsViewBuilder`,
   `TagViewBuilder`, `TagController`, `TagUpdateRenderer`, i18n files
7. **Archive toolbar**: Port `FolderOperationService`, builder, and
   `SearchPresenter` archive/inbox buttons
8. **Stored searches client**: Port `SearchesService`,
   `RemoteSearchesService`, `SearchesEditorPopup`, event handlers

---

## 6. Files to Port Per Feature

### Tags -- model (Phase 1)

Source directory: `${WIAB_PRO_ROOT}/src/org/waveprotocol`

> **Setup note**: `${WIAB_PRO_ROOT}` refers to the root of your local
> [Wiab.pro](https://github.com/nicedoc/Wiab.pro) checkout. Clone the
> repository and set the variable (or substitute the path) before using
> the file references below.

| Wiab.pro file | Action |
|--------------|--------|
| `wave/model/conversation/Conversation.java` | Merge: add `getTags()`, `addTag()`, `removeTag()`, `isRoot()` |
| `wave/model/conversation/ObservableConversation.java` | Merge: add `TagListener`, split listener interfaces |
| `wave/model/conversation/WaveletBasedConversation.java` | Merge: add tag operations + TagListener dispatch |
| `wave/model/conversation/quasi/QuasiConversationImpl.java` | Port if quasi layer imported |

### Tags -- client (Phase 2)

| Wiab.pro file | Action |
|--------------|--------|
| `wave/client/wavepanel/view/TagsView.java` | Port new file |
| `wave/client/wavepanel/view/TagView.java` | Port new file |
| `wave/client/wavepanel/view/IntrinsicTagsView.java` | Port new file |
| `wave/client/wavepanel/view/IntrinsicTagView.java` | Port new file |
| `wave/client/wavepanel/view/impl/TagsViewImpl.java` | Port new file |
| `wave/client/wavepanel/view/impl/TagViewImpl.java` | Port new file |
| `wave/client/wavepanel/view/dom/TagsDomImpl.java` | Port new file |
| `wave/client/wavepanel/view/dom/TagDomImpl.java` | Port new file |
| `wave/client/wavepanel/view/dom/full/TagsViewBuilder.java` | Port new file |
| `wave/client/wavepanel/view/dom/full/TagViewBuilder.java` | Port new file |
| `wave/client/wavepanel/view/dom/full/i18n/TagMessages.java` | Port new file |
| `wave/client/wavepanel/view/dom/full/i18n/TagMessages_*.properties` | Port new files (5 locales) |
| `wave/client/wavepanel/render/TagUpdateRenderer.java` | Port new file |
| `wave/client/wavepanel/impl/edit/TagController.java` | Port new file |
| `wave/client/wavepanel/impl/edit/i18n/TagMessages.java` | Port new file |
| `wave/client/wavepanel/impl/edit/i18n/TagMessages_*.properties` | Port new files |

### Archive -- server (Phase 1)

| Wiab.pro file | Action |
|--------------|--------|
| `wave/model/conversation/InboxState.java` | Port new file |
| `box/search/query/QueryCondition.java` | Port new file |
| `box/search/query/SearchQuery.java` | Port new file |
| `box/search/query/QueryParser.java` | Port new file |
| `box/server/rpc/FolderServlet.java` (javax) | Port new file |
| Jakarta: `box/server/rpc/FolderServlet.java` | Port new file |
| `box/server/waveserver/TokenQueryType.java` | Merge: add TAG, CONTENT, TITLE |

### Archive -- client (Phase 2)

| Wiab.pro file | Action |
|--------------|--------|
| `box/webclient/folder/FolderOperationService.java` | Port new file |
| `box/webclient/folder/FolderOperationServiceImpl.java` | Port new file |
| `box/webclient/folder/FolderOperationBuilder.java` | Port new file |
| `box/webclient/folder/FolderOperationBuilderImpl.java` | Port new file |
| `box/webclient/search/SearchPresenter.java` | Merge: add archive/inbox toolbar |

### Stored searches -- server (Phase 1)

| Wiab.pro file | Action |
|--------------|--------|
| `box/searches/searches.proto` | Port new file |
| `box/searches/Searches.gwt.xml` | Port new file |
| `box/server/rpc/SearchesServlet.java` (javax) | Port new file |
| Jakarta: `box/server/rpc/SearchesServlet.java` | Port new file |
| `box/server/account/HumanAccountData.java` | Merge: add getSearches/setSearches |
| `box/server/account/HumanAccountDataImpl.java` | Merge: add searches field |
| `box/server/persistence/protos/account-store.proto` | Merge: import searches |
| `box/server/persistence/protos/ProtoAccountDataSerializer.java` | Merge |

### Stored searches -- client (Phase 2)

| Wiab.pro file | Action |
|--------------|--------|
| `box/webclient/search/SearchesService.java` | Port new file |
| `box/webclient/search/RemoteSearchesService.java` | Port new file |
| `box/webclient/search/SearchesEditorPopup.java` | Port new file |
| `box/webclient/search/SearchesEditorPopup.css` | Port new file |
| `box/webclient/search/SearchesItemEditorPopup.java` | Port new file |
| `box/webclient/search/SearchesItemEditorPopup.ui.xml` | Port new file |
| `box/webclient/search/i18n/SearchesEditorMessages.java` | Port new file |
| `box/webclient/search/i18n/SearchesItemEditorMessages.java` | Port new file |
| `box/webclient/client/events/SearchesModifyEvent.java` | Port new file |
| `box/webclient/client/events/SearchesModifyEventHandler.java` | Port new file |
| `box/webclient/search/SearchPresenter.java` | Merge: add multi-search support |
