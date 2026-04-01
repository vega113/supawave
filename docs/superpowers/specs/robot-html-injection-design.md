# Robot HTML Injection Design

Status: investigation draft for architectural review
Date: 2026-04-02
Scope: replace legacy OpenSocial gadgets with robot-managed HTML in normal Wave blips, rendered safely in the current GWT client and survivable in a future J2CL migration

## 1. Recommendation

Do not revive or extend the OpenSocial gadget stack.

Add a new first-class Wave document element for robot-rendered HTML blocks, exposed through an explicit robot operation and rendered by a dedicated client doodad. In v1, the payload is sanitized server-side, stored in canonical form on the blip, and rendered inside a tightly sandboxed iframe with no script execution.

This is the recommended shape:

- storage model: normal blips containing a new block element such as `<robot-html ... />`
- robot API: a new explicit operation, not reuse of `document.appendMarkup`
- safety model: server-side sanitizer plus iframe sandbox plus iframe-local CSP
- interactivity model in v1: display-first; use existing Wave form elements and robot events for actions
- migration target: retire gadgets for SupaWave deployment rather than translating OpenSocial semantics one-for-one

The key architectural decision is that this should be a new document element inside an existing blip, not a new blip type. The current client already extends rendering through element handlers and doodads; that is the narrowest correct seam.

## 2. What Exists Today

### 2.1 Gadget Architecture

The gadget surface is still present in the source tree, but it is already strategically weak and partially retired in runtime wiring.

Server-side:

- The Jakarta runtime registers `/gadget/gadgetlist` through `GadgetProviderServlet`, which serves cached contents of `jsongadgets.json`.
- The legacy main-tree `ServerMain` still contains `/gadgets/*` transparent proxy wiring to an external gadget server, but the Jakarta runtime tree does not expose that proxy path.
- Current runtime evidence therefore points to a reduced gadget surface: local gadget catalog remains, external proxy path is no longer part of the active Jakarta server path.

Client-side:

- `org.waveprotocol.wave.client.gadget.Gadget` installs the gadget doodad into the per-blip registries.
- `GadgetRenderer` registers a rendering mutation handler for the `gadget` XML tag.
- `GadgetWidget` is the real container. It:
  - loads `/gadgets/js/core:rpc.js`
  - fetches metadata from `/gadgets/metadata`
  - builds an iframe URL with `rpctoken`, `st`, locale, `parent`, and `waveId`
  - stores gadget state and prefs back into the Wave document
  - stores private gadget state in the per-user supplement
- `GadgetDataStoreImpl` explicitly documents that the security token is currently unused and cached as `null`.

Model/storage:

- Gadgets are stored as Wave XML `<gadget>` elements with attributes and child tags for `title`, `thumbnail`, `category`, `state`, and `pref`.
- `ConversationSchemas` allows `gadget` in line containers and a handful of other places.
- `DocumentModifyService` can insert gadgets and update existing gadget element properties.
- Passive robot event generation emits `GadgetStateChangedEvent`.

Deployment/product signals:

- `EditToolbar` now contains an explicit comment that the gadget button was removed because gadgets are not supported in the current deployment.
- The runtime mismatch between the old client expectations (`/gadgets/metadata`, RPC relay, `st`) and the Jakarta server wiring is further evidence that gadgets are already on the retirement path.

Conclusion:

- gadgets are not a healthy foundation for new work
- the useful part to keep is the element-renderer seam, not the OpenSocial transport, token, or RPC stack

### 2.2 Robot API Capabilities

Robots already do more than plain text or raw OT replay.

Transport and auth:

- Active robot API requests terminate in the Jakarta `ActiveApiServlet`
- Data API requests terminate in the Jakarta `DataApiServlet`
- both route into the shared Jakarta `BaseApiServlet`
- `BaseApiServlet` deserializes JSON-RPC operations, enforces per-operation scope checks, executes them through `OperationUtil`, then submits deltas

Operation surface currently registered in both Active API and Data API registries:

- wavelet operations:
  - append blip
  - add/remove participant
  - set title
  - create wavelet
  - folder action
- blip/thread operations:
  - create child
  - continue thread
  - delete blip
  - insert inline blip
- document operations:
  - `document.modify`
  - `document.appendMarkup`
  - inline blip insertion helpers
- read/export/import operations:
  - fetch wave
  - search
  - fetch profiles
  - export snapshot/deltas/attachment
  - import deltas/attachment

Important capability observations:

- `Blip.append(BlipContent)` already supports appending `Element`, `FormElement`, `Gadget`, or other `BlipContent`
- `Blip.appendMarkup(String)` already exists and maps to `document.appendMarkup`
- `document.appendMarkup` is not browser HTML injection. It appends XML parsed through `XmlStringBuilder.createFromXmlString(...)` into the Wave document model
- `DocumentModifyService` already inserts:
  - gadgets
  - form elements
  - text
- `DocumentModifyService` only updates gadgets today; it does not have a generic rich-element update path

Existing robot form support is real:

- `FormElement` covers `BUTTON`, `CHECK`, `INPUT`, `PASSWORD`, `LABEL`, `RADIO_BUTTON`, `RADIO_BUTTON_GROUP`, and `TEXTAREA`
- `SimpleLoginFormHandler` creates a login wavelet with a link annotation and a button form element
- client doodads render button/input/check/radio/password/textarea elements
- button clicks are represented in-document by `<events><click ... /></events>`
- passive event generation turns those click inserts into `FormButtonClickedEvent`
- `AbstractRobot` already dispatches `FORM_BUTTON_CLICKED` and `GADGET_STATE_CHANGED`

Implication:

- the current robot API already supports structured non-text document content
- what it does not support is a dedicated, typed, audited way to inject sanitized HTML as a new renderable content block

### 2.3 Blip Rendering Pipeline

The current client does not have a `BlipPresenter` class in this tree. The real seams are:

- `StageTwo`
- `WaveDocuments<LazyContentDocument>`
- `ContentDocument`
- `AnnotationPainter`
- `ElementHandlerRegistry`
- `BlipView` / `BlipViewDomImpl`

Flow:

1. `StageTwo#createDocumentRegistry()` creates `LazyContentDocument` instances for blip docs.
2. `LazyContentDocument` materializes a `ContentDocument` on demand.
3. `ContentDocument` holds:
   - indexed document model
   - rendered content view
   - persistent content view
   - HTML view
   - local annotations
4. `AnnotationPainter` repaints annotation-driven presentation.
5. Element renderers and mutation handlers come from `ElementHandlerRegistry`.
6. `StageTwo#createBlipQueueRenderer()` installs blip-specific doodads, including gadgets.
7. `BlipView` and `BlipViewDomImpl` represent the outer blip shell, metadata row, anchors, and nested conversation containers, not arbitrary rich inner HTML.

Existing extension points:

- annotation-based rendering
  - style annotations go through `StyleAnnotationHandler` and `AnnotationPainter`
  - link handling is annotation-driven
- element-based rendering
  - elements are rendered by tag name through `ElementHandlerRegistry`
  - form doodads are globally registered in `Editors.initRootRegistries()`
  - gadget rendering is installed as a blip doodad via `Gadget.install(...)`
- server-side read-only rendering
  - `ServerHtmlRenderer` renders read-only HTML for SSR/public/history style outputs
  - `PublicWaveBlipRenderer` renders public blip content with escaping and link sanitization
  - `robot-html` elements are re-sanitized at render time (see section 4.5a) using the same sanitization pipeline as insertion time

Important distinction:

- annotations are good for styling and link semantics over existing text
- element handlers are the correct seam for a new embedded block type

Implication:

- robot HTML should be implemented as a new document element with a dedicated renderer
- it should not try to become a new blip class and it should not be tunneled through annotation text

### 2.4 Security Model in the Current Codebase

The codebase has several targeted escaping and header-hardening improvements, but it does not have a general-purpose trusted rich-HTML ingestion pipeline.

What exists:

- response-level CSP through `SecurityHeadersFilter`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy`
- server-side escaping helpers such as `HtmlRenderer.escapeHtml(...)` and JSON escaping
- read-only renderers that escape text and sanitize link schemes
- gadget isolation through an iframe boundary
- form safety through a fixed XML schema and fixed client doodads

Relevant security observations from the current tree:

- the default CSP still allows `'unsafe-inline'` and `'unsafe-eval'` for the app shell, so CSP alone is not a complete answer for injected rich content
- `ServerHtmlRenderer` and `PublicWaveBlipRenderer` sanitize links by scheme and escape text, but they are read-only renderers, not a stored arbitrary-HTML sanitizer
- there is no `DOMPurify` usage in the repo
- there is no shadow-DOM based containment model in the repo
- there is no existing generic `SafeHtml`-backed arbitrary rich fragment ingestion path for blip content

Implication:

- direct insertion of robot-supplied HTML into the main DOM would require brand-new sanitizer and trust-boundary work
- the safest narrow path is defense in depth:
  - canonical server-side sanitization
  - isolated rendering container
  - no script execution in the rendered fragment

### 2.4a Security Model for Robot HTML

The proposed robot HTML design applies defense-in-depth across multiple layers:

| Defense Layer | Client Iframe | ServerHtmlRenderer | Notes |
|---|---|---|---|
| **Insertion-time Sanitization** | Yes (section 4.4) | Yes (section 4.5a) | Attribute whitelist, URL protocol allowlist, CSS property whitelist |
| **Render-time Sanitization** | Yes (iframe auto-sanitizes) | Yes (re-sanitization) | Re-apply same sanitization pipeline to detect corruption or bugs |
| **Iframe Sandbox** | Yes (`sandbox` attribute, no `allow-same-origin`) | N/A | Process-level isolation; no access to parent DOM, storage, or APIs |
| **CSP in Iframe** | Yes (frame-level CSP) | N/A | Prevent script execution, inline styles, etc. even if injected |
| **No Script Execution** | Yes (verified by iframe) | Yes (SSR context has no JS) | Neither client iframe nor SSR can run JavaScript from injected content |

**Defense-in-depth justification:**

- Sanitization at insertion time prevents bad data from entering storage
- Re-sanitization at render time protects against:
  - Insertion-time sanitizer bugs or incomplete implementation  
  - Data corruption or tampering (accidental or malicious)
  - Future changes to security policy that should apply retroactively
- Iframe sandboxing (client only) provides a secondary isolation boundary even if sanitization fails
- CSP (client only) prevents script execution patterns even if they slip through sanitization
- SSR has no iframe because public waves are read-only and do not execute JavaScript

This matches the pattern: **trust but verify**. We sanitize HTML before storing it, then verify it again before rendering it, regardless of path.

### 2.5 Prior Art

There are three useful precedents.

1. Native robot forms

- already support robot-managed interactivity with typed elements and click events
- provide a safe action model we can reuse instead of allowing arbitrary JS in injected HTML

2. Gadgets

- show how Wave already embeds isolated content as element-based blip decorations
- also show the downsides we should not repeat:
  - external dependency on `/gadgets/*`
  - JSNI-heavy client stack
  - token/RPC complexity
  - OpenSocial coupling

3. Experimental HTML template doodad

- `org.waveprotocol.wave.client.doodad.experimental.htmltemplate.HtmlTemplate`
- intended to embed HTML-template-based applications backed by Caja/cajoling services
- not installed in the production StageTwo doodad set
- JSNI-heavy and explicitly called out as part of the J2CL blocker surface

There is also a schema curiosity:

- `ConversationSchemas` still includes `body/html/data` and `body/experimental`
- those are not exposed through the public robot API as first-class typed elements
- there is no installed production renderer path that makes them a viable solution for robot HTML today

Conclusion:

- the prior art says "element renderer" is the right seam
- the prior art does not justify reusing the old experimental htmltemplate/Caja path

### 2.6 J2CL / GWT 3 Relevance

The J2CL decision memo matters here because gadgets and experimental HTML are already in the highest-risk migration bucket.

Current migration constraints from the repo docs:

- full-app J2CL migration is currently a no-go
- the client still depends heavily on:
  - JSNI
  - `JavaScriptObject`
  - UiBinder
  - `GWT.create(...)`
- gadget renderer and html-template code are explicitly listed as high-risk JSNI/browser-interop clusters
- there is no JsInterop or Elemental2 bridge seam yet

Implication for this design:

- do not add new JSNI
- do not add new OpenSocial-style browser RPC surfaces
- do not couple the new robot HTML feature to UiBinder-heavy or deferred-binding-heavy infrastructure
- keep the wire format pure data
- keep the client renderer behind one narrow element-renderer seam so it can be rewritten later without changing stored document data

This favors:

- a small, explicit document element
- a plain renderer/widget using standard iframe attributes and DOM events
- no external gadget server

## 3. Options Considered

### Option A: Reuse `document.appendMarkup`

Summary:

- robots would send XML strings through existing `document.appendMarkup`
- the client would rely on schema-valid markup already accepted by the document layer

Why this is not recommended:

- `document.appendMarkup` is too broad and not self-documenting
- it treats the payload as Wave XML, not as a typed "robot HTML block"
- it has no dedicated policy boundary, audit semantics, or scope classification
- it would encourage mixing raw XML storage concerns with product-level HTML policy
- it does not solve rendering or safety on its own

Decision:

- reject as the primary design

### Option B: Sanitized direct DOM rendering

Summary:

- server sanitizes robot HTML
- client inserts trusted HTML directly into the blip DOM

Pros:

- best layout integration
- easiest sizing
- easiest text selection and copy behavior
- simpler than iframes once trust is established

Cons:

- a sanitizer mistake becomes stored-XSS in the main app DOM
- the repo has no existing trusted arbitrary-HTML pipeline
- the current app CSP is not strict enough to be the only backstop

Decision:

- plausible later optimization
- not recommended for v1

### Option C: Sanitized HTML rendered in a sandboxed iframe

Summary:

- server sanitizes robot HTML
- client renders a sanitized fragment in `iframe[srcdoc]` with sandboxing and iframe-local CSP

Pros:

- strongest defense in depth
- closest safety analogue to what gadgets were trying to achieve
- avoids OpenSocial and JSNI while keeping iframe isolation
- keeps malicious styling and markup from touching the host DOM

Cons:

- more layout complexity
- more accessibility and copy/selection edge cases
- host/frame link handling and sizing need deliberate design

Decision:

- recommended for v1

## 4. Proposed Design

## 4.1 Product Shape

Robots can inject a new block element into a normal blip. The payload is sanitized HTML meant for rich presentation, not arbitrary script execution.

In v1:

- payload is display-oriented HTML
- no script execution is allowed
- robot actions use existing Wave form elements or ordinary links, not a gadget-style JS bridge

This intentionally does not attempt to reproduce the full OpenSocial gadget execution model.

## 4.2 New Stored Element

Introduce a new document element tag with `<data>` child for payload storage:

```xml
<robot-html
    key="build-card-123"
    v="1"
    policy="sandbox-v1"
    h="220"
    w="full"
    title="Build status">
  <data>&lt;section class="card"&gt;&lt;h2&gt;Build passing&lt;/h2&gt;&lt;/section&gt;</data>
</robot-html>
```

Recommended storage rules:

- **Metadata attributes:**
  - `key` is a robot-chosen stable identifier for targeted updates
  - `v` is the storage/version marker (start with `v="1"`)
  - `policy` pins the rendering mode; start with `sandbox-v1`
  - `h` is preferred height in CSS pixels
  - `w` is rendering width policy such as `full` or `inline`
  - `title` is optional accessibility/placeholder text

- **Payload in `<data>` child element:**
  - Contains sanitized canonical HTML fragment as XML-escaped text
  - No Base64 encoding (text child elements are standard Wave pattern)
  - XML escaping is handled by Wave SDK during serialization/deserialization

Why `<data>` child instead of Base64 attribute:

- Fits Wave OT model naturally; text children are standard for structured elements
- XML escaping by Wave SDK avoids manual encoding/decoding overhead
- Serializer integration is simpler (reuses `<data>` element type already in schema)
- Wire format is more human-readable for debugging

Trade-off:

- Attributes are more compact than text children with XML escaping
- However, text child elements maintain OT semantics better and integrate with Wave's existing infrastructure
- v1 should impose a strict size cap, for example 64 KB sanitized payload
- Larger payloads can migrate to dedicated data documents keyed by element id in v2

**Backwards compatibility:**

- If v0 used Base64 attribute, v1 migration code should detect version and convert:
  - v0: `<robot-html v="0" html="BASE64DATA" .../>`
  - v1: `<robot-html v="1" ...><data>XML-ESCAPED HTML</data></robot-html>`

## 4.3 Robot Wire Format

Add explicit robot operations instead of overloading raw markup:

- `document.insertRobotHtml`
- `document.updateRobotHtml`

Optional later:

- `document.deleteRobotHtml`

Example request:

```json
[
  {
    "id": "op1",
    "method": "document.insertRobotHtml",
    "waveId": "example.com!w+abc",
    "waveletId": "example.com!conv+root",
    "blipId": "b+123",
    "params": {
      "index": 57,
      "key": "build-card-123",
      "title": "Build status",
      "preferredHeight": 220,
      "widthPolicy": "full",
      "html": "<section class=\"card\"><h2>Build passing</h2><p>main is green</p></section>"
    }
  }
]
```

Recommended API contract:

- robot sends plain HTML, not Wave XML and not base64
- server owns sanitization, canonicalization, hashing, and storage encoding
- update operation addresses the element by `key` within the blip

Why a new operation is worth it:

- clearer audit trail than `document.appendMarkup`
- explicit scope mapping
- dedicated validation and error messages
- easy to block/roll out by feature flag

SDK additions:

- `ElementType.ROBOT_HTML`
- `RobotHtmlElement extends Element`
- `Blip.appendRobotHtml(...)`
- `BlipContentRefs.robotHtml(...)`
- `OperationQueue.insertRobotHtml(...)`

## 4.4 Server-Side Validation and Canonicalization

Introduce a dedicated server sanitizer service. The core rule is:

- store sanitized canonical HTML, never raw robot HTML

Recommended sanitizer policy for v1:

- allow structural tags such as:
  - `div`, `section`, `article`, `header`, `footer`
  - `p`, `span`, `blockquote`, `pre`, `code`
  - `h1` to `h6`
  - `ul`, `ol`, `li`
  - `table`, `thead`, `tbody`, `tr`, `th`, `td`
  - `a`
  - `img`
  - `strong`, `em`, `b`, `i`, `u`
  - `br`, `hr`
- disallow active-content tags:
  - `script`
  - `iframe`
  - `object`
  - `embed`
  - `applet`
  - `form`
  - `input`
  - `button`
  - `textarea`
  - `select`
  - `svg`
  - `math`
- strip all `on*` event attributes

**URL sanitization (protocol allowlist, not blacklist):**
  - **href attributes:** Allow only `http://`, `https://`, relative URLs (`/`, `.`). Block all other protocols including `javascript:`, `data:`, `vbscript:`, etc.
  - **src attributes:** Allow `http://`, `https://`, relative URLs, and `data:image/*` patterns only (PNG, JPEG, GIF, WebP, SVG). Block `data:text/html`, `javascript:`, etc.
  - **Normalization:** Before protocol check, trim whitespace and unescape HTML entities (e.g., `javascript%3a` → `javascript:`)
  - **Rationale:** Blacklist-based checks (`reject "javascript:"`) are bypassable via encoding; allowlist approach is safer

**CSS sanitization (property whitelist):**
  - **style attribute:** If present, apply a CSS property whitelist. Only allow safe properties: `color`, `background-color`, `font-size`, `font-family`, `font-weight`, `margin*`, `padding*`, `text-align`, `text-decoration`, `line-height`, `border`, `border-radius`
  - **Dangerous values:** Reject `expression()`, `javascript:`, `behavior:`, `url(javascript:)` even in whitelisted properties
  - **Rationale:** CSS can be an XSS vector through properties like `behavior:` (IE) or `url()` with JS handlers; property whitelist prevents misuse

**Attribute whitelist handling (fix for glob pattern matching):**
  - **Do NOT use glob patterns like `data-*` in Set.contains(attribute)** — glob patterns don't work with Set membership checks
  - **Allowed attributes:** Enumerate specific attributes only:
    - Global: `id`, `class`, `title`
    - Specific data attributes (if needed): `data-toggle`, `data-target`, `data-animation` (explicit list only)
    - Link/img: `href`, `src`, `alt`
    - Sizing: `width`, `height`
    - Table: `colspan`, `rowspan`
  - **If custom data-* attributes are needed:** Implement a prefix check (e.g., `attr.startsWith("data-") && isWhitelistedDataAttribute(attr)`) instead of relying on glob matching
  - **Rationale:** Set.contains() is exact-match only; to support `data-*` patterns, either enumerate specific attributes or implement custom prefix logic

- clamp payload size before and after sanitization
- canonicalize the sanitized output so equal input produces equal stored output

Important design choice:

- v1 should not allow embedded forms inside robot HTML
- interactive actions should use existing Wave form elements outside the iframe

That gives a clean split:

- robot HTML handles rich presentation
- native Wave form doodads handle user actions and robot callbacks

## 4.5 Client Rendering Pipeline

Rendering should be implemented as a new element doodad, parallel to gadgets and forms.

Recommended client path:

1. `ConversationSchemas` permits `<robot-html>` in the body and line-container contexts.
2. `ElementSerializer` can serialize and deserialize the new element in API fetch/export paths.
3. `StageTwo` or `Editors.initRootRegistries()` registers a `RobotHtmlRenderer`.
4. The renderer extracts the sanitized HTML from the `<data>` child element.
5. The widget renders the fragment into an iframe:
   - `srcdoc` = wrapper HTML document containing the sanitized fragment
   - `sandbox` = no `allow-same-origin` (robot HTML is display-only and does not need parent API access)
   - no `allow-scripts`
   - no top-navigation privileges
6. The iframe wrapper document sets a strict local CSP, for example:
   - `default-src 'none'`
   - `img-src data: https:`
   - `style-src 'unsafe-inline'`
   - `font-src data: https:`
7. Host-side code sizes the iframe using:
   - preferred height from element metadata as initial layout
   - optional post-load measurement using `postMessage` API if content introspection is needed

Why `allow-same-origin` is NOT used:

- Robot HTML is display-only content with no need for parent API access (localStorage, cookies, parent DOM)
- Using `srcdoc` creates a unique null origin per iframe, providing process-level isolation
- Removing `allow-same-origin` eliminates a potential attack surface if sanitization fails
- Fallback height measurement can use standard `postMessage` API without same-origin access
- If sanitizer fails, script still does not execute because `allow-scripts` is absent, but removing same-origin adds defense in depth

Recommended rendering behavior:

- show a placeholder card while decoding/loading
- show a visible fallback for invalid payload/version mismatch
- respect `h` and clamp final height to a safe range
- keep the outer host element selectable/focusable even if the frame content is not editable

What not to do:

- do not inject the robot HTML directly into the host blip DOM in v1
- do not add a gadget-style JS RPC bridge
- do not require `/gadgets/*`, `st`, or external metadata endpoints

## 4.5a Server-Side Rendering (ServerHtmlRenderer)

**Scope:** Public wave exports, read-only SSR, history views, and wave snapshots require rendering of `<robot-html>` elements.

**Recommendation:** Render as **re-sanitized HTML** using the same sanitization pipeline as insertion time.

**Implementation approach:**

Register a `RobotHtmlRenderer` in `ServerHtmlRenderer` to handle `<robot-html>` elements:

```
When rendering a <robot-html> element:
  1. Extract the <data> child element containing XML-escaped HTML
  2. Apply full sanitization pipeline (attribute whitelist, URL protocol allowlist, CSS property whitelist)
     - This is the SAME sanitization applied at insertion time (see section 4.4)
  3. Wrap the sanitized content in a styled <div> container with metadata attributes
  4. Output sanitized HTML
  
  Output example:
  <div class="robot-html-container" data-robot-key="build-card-123" data-title="Build status">
    <section class="card"><h2>Build passing</h2></section>
  </div>
```

**Rationale:**

- **Defense-in-depth:** Sanitization is applied at insertion time AND re-applied at render time. This provides protection against:
  - Insertion-time sanitizer bugs or incomplete implementation
  - Data corruption or tampering of stored HTML content
  - Future changes to the sanitization policy that should apply to all render paths
- Public waves do not run JavaScript, so no `<script>` or event handlers will execute (if somehow present after sanitization)
- Wrapping in a div allows CSS styling in public/SSR views
- Metadata attributes are preserved for debugging and audit purposes
- No iframe is needed in static/read-only contexts; direct HTML is appropriate
- Re-sanitization ensures consistency: all render paths (client iframe + SSR) use identical safety guarantees

**CSS guidance for static rendering:**

```css
.robot-html-container {
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 12px;
  margin: 8px 0;
  background-color: #f9f9f9;
}
```

## 4.6 Why This Should Not Be an Annotation

Annotations are the wrong tool here.

Current annotation rendering is meant for:

- styles
- links
- selection
- diff rendering

Robot HTML needs:

- block-level placement
- storage metadata
- versioned rendering policy
- possibly future update-by-key semantics

That maps naturally to an element, not to annotated text.

## 4.7 Why This Should Not Be a New Blip Type

The conversation and view stack do not have a clean "special blip subclass" seam for this.

The current extensibility mechanism is:

- a normal blip document
- custom elements inside that document
- element handlers/doodads for rendering

Using a new inner element avoids broad changes to:

- conversation model
- `BlipView` shell
- scrolling/paging
- focus traversal
- editor lifecycle

## 5. Migration Path from OpenSocial Gadgets

Treat this as a product migration, not as a compatibility layer.

### 5.1 What Maps Cleanly

These gadget classes can move to robot HTML:

- status cards
- dashboards
- link launchers
- preview panels
- report/embed views that mostly display structured content

Recommended migration pattern:

1. robot fetches remote data or internal state
2. robot renders a sanitized HTML card
3. robot inserts `<robot-html>`
4. if user action is needed, robot also inserts native Wave form elements nearby

### 5.2 What Does Not Map Cleanly

These gadget classes should not be treated as v1 scope:

- arbitrary JS applications
- OpenSocial RPC-heavy apps
- applications depending on gadget private state semantics
- apps expecting to talk to an external gadget server

For those, the migration answer is:

- unsupported in v1
- redesign the user flow around robot HTML plus native Wave controls

### 5.3 Data and State Migration

Gadget state today lives in two places:

- gadget element state/prefs in the document
- private gadget state in the supplement

Recommended v1 replacement:

- persistent rendering payload and render metadata in `<robot-html>`
- any robot-owned durable app state in data documents or robot-side storage
- any user action state through native Wave form values and robot callbacks

This is intentionally simpler than preserving gadget private-state semantics.

### 5.4 Operational Migration Sequence

Recommended rollout:

1. add new robot HTML element and renderer behind a feature flag
2. build one robot-driven card end-to-end
3. add a scanner/tooling pass to inventory existing `<gadget>` usage
4. create URL-based migration adapters for the small number of known gadget URLs still worth preserving
5. remove remaining gadget UI affordances and runtime dependencies once the replacement paths exist

## 6. Required Code Changes

## 6.1 Robot API

Add or change:

- `wave/src/main/java/com/google/wave/api/OperationType.java`
  - add `DOCUMENT_INSERT_ROBOT_HTML`
  - add `DOCUMENT_UPDATE_ROBOT_HTML`
- `wave/src/main/java/com/google/wave/api/OperationQueue.java`
  - helper methods for enqueueing robot HTML operations
- `wave/src/main/java/com/google/wave/api/Blip.java`
  - convenience helpers
- `wave/src/main/java/com/google/wave/api/ElementType.java`
  - new `ROBOT_HTML`
- `wave/src/main/java/com/google/wave/api/Element.java`
  - helper like `isRobotHtml()`
- `wave/src/main/java/com/google/wave/api/BlipData.java`
  - deep-copy support for the new element type
- `wave/src/main/java/com/google/wave/api/data/ElementSerializer.java`
  - serialize/deserialize `<robot-html>`

Server operation wiring:

- `wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiOperationServiceRegistry.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOperationServiceRegistry.java`
- new operation service class under `wave/src/main/java/org/waveprotocol/box/server/robots/operations/`

Jakarta scope enforcement:

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`
  - classify the new operations as write operations

## 6.2 Server-Side Blip Model and Validation

### 6.2.1 Schema and Model

Add or change:

- `wave/src/main/java/org/waveprotocol/wave/model/schema/conversation/ConversationSchemas.java`
  - allow the new tag `<robot-html>` in `body` and line-container contexts
  - register `<data>` as a valid child element of `<robot-html>`

### 6.2.2 ElementSerializer Registration

**Scope:** Wave protocol serialization and deserialization of `<robot-html>` elements in API fetch/export paths.

**Implementation:**

Create `RobotHtmlElementSerializer` implementing the standard `ElementSerializer` interface:

```
public class RobotHtmlElementSerializer implements ElementSerializer {
  
  public Element deserialize(XmlElement xmlElem) {
    // Reconstruct Element from stored XML
    Element elem = new Element("robot-html");
    
    // Copy metadata attributes
    elem.setAttribute("key", xmlElem.getAttribute("key"));
    elem.setAttribute("v", xmlElem.getAttribute("v"));
    elem.setAttribute("policy", xmlElem.getAttribute("policy"));
    elem.setAttribute("h", xmlElem.getAttribute("h"));
    elem.setAttribute("w", xmlElem.getAttribute("w"));
    elem.setAttribute("title", xmlElem.getAttribute("title"));
    
    // Copy <data> child element containing XML-escaped HTML
    XmlElement dataXml = xmlElem.getFirstChild("data");
    if (dataXml != null) {
      Element dataElem = new Element("data");
      dataElem.appendText(dataXml.getText());
      elem.appendChild(dataElem);
    }
    
    return elem;
  }
  
  public XmlElement serialize(Element elem) {
    // Serialize Element to stored XML
    XmlElement xml = XmlElement.create("robot-html");
    
    // Copy metadata attributes
    xml.setAttribute("key", elem.getAttribute("key"));
    xml.setAttribute("v", elem.getAttribute("v"));
    xml.setAttribute("policy", elem.getAttribute("policy"));
    xml.setAttribute("h", elem.getAttribute("h"));
    xml.setAttribute("w", elem.getAttribute("w"));
    xml.setAttribute("title", elem.getAttribute("title"));
    
    // Serialize <data> child
    Element dataElem = elem.getFirstChild("data");
    if (dataElem != null) {
      XmlElement dataXml = XmlElement.create("data");
      dataXml.setText(dataElem.getTextContent());
      xml.appendChild(dataXml);
    }
    
    return xml;
  }
}
```

**Registration in module initialization:**

```
ElementSerializerRegistry.register("robot-html", new RobotHtmlElementSerializer());
```

### 6.2.3 DocumentModifyService Integration

**Operation dispatch flow:**

```
Robot API Request
  ↓
DocumentModifyService.execute(OperationRequest)
  ↓
RouteByOperationType:
  - if type == "document.insertRobotHtml": dispatch to RobotHtmlInsertService
  - if type == "document.updateRobotHtml": dispatch to RobotHtmlUpdateService
  - if type == "document.deleteRobotHtml": dispatch to RobotHtmlDeleteService
  ↓
RobotHtmlInsertService:
  1. Validate robot authentication and "wave.robot" scope
  2. Extract HTML payload from request
  3. HtmlSanitizer.sanitize(html) → sanitized HTML
  4. ElementSerializer creates <robot-html><data>...</data></robot-html> element
  5. Document.insertElement(index, element)
  6. Return operation result with element key/id
  ↓
Wave document updated + broadcast to all clients
  ↓
Client: ElementHandlerRegistry finds RobotHtmlRenderer for "robot-html" tag
Client: Renderer extracts <data> child and renders in iframe
```

### 6.2.4 Sanitizer Service

Add or change:

- new sanitizer service in server code
  - keep it independent of servlet auth code
  - unit-test it aggressively
  - implement CSS property whitelist (see section 4.4)
  - implement URL protocol allowlist with normalization (see section 4.4)

Potential implementation split:

- `RobotHtmlSanitizer`
- `RobotHtmlCanonicalizer`
- `RobotHtmlOperationService`

Optional later:

- text extractor for search/snippets from sanitized robot HTML

## 6.3 GWT Client Renderer

Add or change:

- new doodad package, for example:
  - `wave/src/main/java/org/waveprotocol/wave/client/doodad/robothtml/`
- renderer class using the existing element-handler seam
- widget/container class for the sandboxed iframe
- registry install point:
  - either `Editors.initRootRegistries()` if globally available
  - or `StageTwo.installDoodads()` if rollout needs stage-level control

Do not require changes to:

- `BlipView`
- `BlipViewDomImpl`
- core conversation model

except possibly minor CSS/support hooks for host spacing.

## 6.4 Tests

Add:

- serializer round-trip tests
- sanitizer tests
- operation service tests
- GWT client renderer tests for decode/render/fallback behavior
- regression tests proving scripts and event handlers do not execute
- focused integration tests around adjacent form controls if that migration path is used

## 7. Open Questions and Risks

1. Payload size

- attribute storage is simple but not ideal for large fragments
- if real usage exceeds the cap, move payload to a data document keyed by element id

2. Auto-height

- iframe sizing is manageable but not free
- if same-origin measurement turns out awkward in practice, require explicit preferred height in v1

3. Accessibility

- iframe content can complicate screen-reader flow and text selection
- we may need an accessible plaintext fallback or host-side `title`/summary text

4. Search and snippets

- search should likely index sanitized text extracted from the payload, not raw base64
- this needs explicit implementation if robot HTML becomes common

5. Link policy

- allowing external links is useful, but click behavior inside sandboxed iframes needs a deliberate host policy

6. Existing `document.appendMarkup`

- the current raw markup seam already exists
- if robot HTML becomes first-class, consider whether `document.appendMarkup` should remain broadly available or be documented as legacy/internal

7. Migration scope

- some gadgets were real applications, not display cards
- those will need product redesign, not a mechanical data conversion

8. J2CL readiness

- this design avoids adding new JSNI, but the renderer still lives in the legacy GWT client today
- keep the browser interop narrow so it becomes a good candidate for a future JsInterop bridge

9. Defense-in-depth Sanitization Overhead

- sanitization is applied at insertion time AND re-applied at render time
- this provides defense-in-depth against insertion-time bugs or data corruption
- cost is minimal: render-time sanitization is a linear pass through the stored HTML
- benefit is substantial: eliminates single point of failure if insertion-time sanitizer has bugs
- this is consistent with security best practice: trust but verify

## 7.9 Revision History: Opus Architectural Review Fixes

This section documents revisions made to address critical architectural feedback from Opus review (2026-04-02).

**ISSUE 1 (Critical - Safety):** `allow-same-origin` on sandbox defeats sandboxing.
  - **Fix:** Removed `allow-same-origin` from section 4.5. Robot HTML is display-only and does not need same-origin API access. Using `srcdoc` with null origin provides process-level isolation.
  - **Status:** ✅ RESOLVED

**ISSUE 2 (Critical - Safety):** `style` attribute has no CSS sanitization.
  - **Fix:** Added CSS property whitelist in section 4.4. Only safe properties allowed (`color`, `font-size`, `margin`, `padding`, `text-align`, etc.). Dangerous values like `expression()`, `javascript:`, `behavior:` are rejected.
  - **Status:** ✅ RESOLVED

**ISSUE 3 (Critical - Safety):** URL sanitization via `startsWith("javascript:")` is bypassable.
  - **Fix:** Replaced with protocol allowlist in section 4.4. URLs are normalized (trim, unescape HTML entities) before protocol check. href allows `http://`, `https://`, relative only. src allows those plus `data:image/*` only (PNG, JPEG, GIF, WebP, SVG).
  - **Status:** ✅ RESOLVED

**ISSUE 4 (Critical - Correctness):** `<html>` tag collides with existing schema.
  - **Fix:** Document already uses `<robot-html>` as distinct tag name (see section 4.2). No schema collision. Choice (a) already implemented.
  - **Status:** ✅ RESOLVED

**ISSUE 5 (Critical - Correctness):** Base64 in attribute doesn't fit OT model.
  - **Fix:** Revised section 4.2 to store sanitized HTML in `<data>` child element instead of Base64 attribute. XML-escaped text fits Wave OT model naturally. Serializer uses standard `<data>` element type.
  - **Status:** ✅ RESOLVED

**ISSUE 6 (Medium - Completeness):** ServerHtmlRenderer handling not documented.
  - **Fix:** Added new section 4.5a describing server-side rendering. Recommendation: render as sanitized HTML in public waves (no iframe needed in static contexts). Wrap in styled div with metadata attributes.
  - **Status:** ✅ RESOLVED

**ISSUE 7 (Medium - Completeness):** ElementSerializer and DocumentModifyService integration not documented.
  - **Fix:** Added section 6.2 with pseudo-code for ElementSerializer registration, operation dispatch flow, and DocumentModifyService routing. Shows how `<robot-html><data>` elements are serialized/deserialized and how operations are routed.
  - **Status:** ✅ RESOLVED

**ISSUE 8 (Low - Safety):** Glob pattern matching for `data-*` doesn't work with Set.
  - **Fix:** Added guidance in section 4.4 (attribute whitelist). Do NOT use `data-*` in Set.contains(); enumerate specific attributes instead, or implement prefix checks if custom data- attributes needed.
  - **Status:** ✅ RESOLVED

**ISSUE 9 (Critical - Defense-in-Depth):** ServerHtmlRenderer outputs content "as-is" without re-sanitization, creating a single point of failure.
  - **Fix:** Updated section 4.5a to require re-sanitization at render time using the same sanitization pipeline as insertion time. This provides defense-in-depth against insertion-time bugs or data corruption.
  - **Changes:**
    - Updated section 4.5a pseudo-code to show sanitization being called again before output
    - Updated section 2.3 (Blip Rendering Pipeline) to clarify that SSR re-sanitizes at render time
    - Added new section 2.4a (Security Model for Robot HTML) with a defense-in-depth table showing both layers
    - Added risk #9 in section 7 documenting defense-in-depth sanitization overhead and justification
    - Updated Final Recommendation section 8 language to emphasize sanitization on all render paths
  - **Status:** ✅ RESOLVED

## 8. Final Recommendation

Implement robot HTML as a new typed block element inside normal blips, exposed through a new robot operation and rendered by a dedicated sandboxed iframe doodad.

Do not:

- extend OpenSocial gadget infrastructure
- reuse raw `document.appendMarkup` as the public contract
- inject robot HTML directly into the host DOM in v1
- allow arbitrary JS inside injected content

Do:

- reuse the existing element-renderer seam
- pair robot HTML with existing Wave form elements for actions
- keep the wire format explicit and versioned
- use server-side sanitization plus iframe sandboxing for defense in depth
- **sanitize HTML at insertion time AND re-sanitize at all render times** (client iframe and ServerHtmlRenderer) for defense-in-depth protection
- treat gadget migration as a strategic retirement path, not as compatibility preservation

This gives SupaWave a realistic way to replace gadgets with robot-managed rich content while staying aligned with the current client architecture and not making the future J2CL situation worse.
