# GXP Replacement Plan — Unified

> Consensus from three independent investigations (Opus, Sonnet, Codex)

## Decision: Replace GXP with Java String Builders + Modern CSS

All three investigators recommend the same approach: replace GXP-generated classes with hand-written Java using `StringBuilder` and text blocks. No new template engine.

## Rationale

- 9 templates total, all trivial (variable substitution + 1-2 conditionals, zero loops)
- Eliminates `generateGXP` build step entirely
- Removes `google-gxp:0.2.4-beta` dependency (unmaintained since 2012)
- Zero new dependencies (use Guava's existing `HtmlEscapers` for escaping)
- Login/register pages get a modern card-based CSS redesign as a bonus
- Also removes unused Velocity 1.7 zombie dependency

## Scope

### 9 GXP Templates to Replace

| Template | Purpose | Complexity |
|---|---|---|
| `AuthenticationPage.gxp` | Login page | Medium (1 conditional, inline JS) |
| `UserRegistrationPage.gxp` | Registration | Medium (1 conditional, JS validation) |
| `WaveClientPage.gxp` | GWT app shell | Medium (embeds session JSON, TopBar) |
| `TopBar.gxp` | Navigation bar | Simple (null check) |
| `AnalyticsFragment.gxp` | GA snippet | Simple (1 conditional) |
| `RobotRegistrationPage.gxp` | Robot registration | Simple |
| `RobotRegistrationSuccessPage.gxp` | Robot success | Very simple |
| `OAuthAuthorizeTokenPage.gxp` | OAuth authorize | Simple |
| `OAuthAuthorizationCodePage.gxp` | OAuth code display | Very simple |

### Files to Change

| Action | File |
|---|---|
| **Create** | `wave/src/jakarta-overrides/java/.../rpc/HtmlRenderer.java` — all 9 templates as static methods |
| **Modify** | `AuthenticationServlet.java` (jakarta-overrides) — remove GxpContext, use HtmlRenderer |
| **Modify** | `WaveClientServlet.java` (jakarta-overrides) — remove GxpContext/TopBar, use HtmlRenderer |
| **Modify** | `UserRegistrationServlet.java` (jakarta-overrides) — remove GxpContext, use HtmlRenderer |
| **Modify** | `RobotRegistrationServlet.java` (jakarta-overrides) — remove GxpContext, use HtmlRenderer |
| **Modify** | `DataApiOAuthServlet.java` (jakarta-overrides) — remove GxpContext, use HtmlRenderer |
| **Modify** | `SimpleSearchProviderImplTest.java` — fix shaded Guava import |
| **Modify** | `wave/build.gradle` — remove generateGXP task, config, deps |
| **Modify** | `build.sbt` — remove generateGxp task, google-gxp dep |
| **Delete** | `wave/src/main/gxp/` (9 .gxp files) |
| **Delete** | `wave/generated/main/java/.../gxp/` (9 generated .java files) |

## Tasks

### Task 1: Create HtmlRenderer + update servlets (Jakarta path)
- Create `HtmlRenderer.java` with all 9 render methods + escaping helpers
- Modern CSS for auth pages (centered card layout, clean typography)
- Update 5 Jakarta-override servlets to use HtmlRenderer
- **Parallelizable**: Yes (one file + 5 servlet updates)

### Task 2: Build system cleanup
- Remove generateGXP from Gradle and SBT
- Remove google-gxp dependency
- Remove unused Velocity dependency
- Fix shaded Guava import in test
- **Depends on**: Task 1

### Task 3: Delete GXP files
- Delete wave/src/main/gxp/ directory
- Delete wave/generated/main/java/.../gxp/ directory
- **Depends on**: Task 1, Task 2

### Task 4: Test and verify
- Build locally
- Start server, verify login/register/wave-client pages render correctly
- Verify no GXP references remain in codebase
- **Depends on**: Task 3
