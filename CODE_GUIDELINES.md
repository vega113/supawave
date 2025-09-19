# Apache Wave Code Guidelines

Scope: This document captures the practical coding standards used across this repository (wave and pst modules). It is based on patterns in the codebase and build tooling and is intended to be concise and actionable for day‑to‑day work.


## 1. Licensing
- Every new source file must include the Apache Software Foundation (ASF) Apache 2.0 license header.
- Keep the header format consistent with the file type and match existing files.

Java (/* */ style):
```
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
```

Proto, Gradle/Groovy, Properties (line comments):
```proto
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
```

Shell/INI (hash comments):
```sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# ... same text as above using '#'
```


## 2. Language levels and build
- Java 17 (source and target) is used (see wave/build.gradle and pst/build.gradle).
- Protobuf is built via the Gradle protobuf plugin; do not edit generated sources under generated/.
- Run local checks before pushing:
  - ./gradlew clean build
  - ./gradlew check (includes tests, Checkstyle)
  - Optional: run Sonar locally if configured; sonar-project.properties exists.


## 3. Formatting and style
- Use the provided Eclipse formatter profile: eclipse-formatter-style.xml.
- Max line length: 100 characters.
- Indentation: 2 spaces; never tabs.
- Braces/spacing: Follow the formatter; do not hand‑format differently.
- Organize imports via your IDE (keep them minimal; avoid unused imports).
- File encoding: UTF‑8; end files with a newline.
- Don't use FQN inside classes unless absolutely necessary.
- Always put a new line after "{" and before "}". Example: do not use the style `if (foo) { bar(); }`

Naming (Java):
- Packages: lower.case.names.
- Classes/Interfaces/Enums: UpperCamelCase.
- Methods/Fields: lowerCamelCase.
- Constants: UPPER_SNAKE_CASE.

Javadoc & comments:
- Public classes and methods must have concise Javadoc describing intent, params, and behavior.
- Prefer self‑documenting code; use comments to explain non‑obvious reasoning, invariants, and edge cases.


### Java code style (WaveStyle)
Apply the rules when writing or editing Java code:

- Indentation
    - Use spaces only; no tabs. Tab width = 2, indent size = 2.
    - Continuation indent = +2 indents (i.e., +4 spaces on wrapped lines).
- Line length
    - Code lines: 100 chars max. Prefer wrapping before binary operators.
    - Javadoc/comments: wrap at 80 chars.
- Braces and layout
    - K&R style: opening brace on the same line for types, methods, blocks, constructors, enums, switch, etc.
    - Always put one space before an opening brace: `if (x) {`.
    - Do not keep then/else on the same line; prefer `} else if (...) {}` for chained conditions.
- Keyword and parentheses spacing
    - Control flow keywords followed by a space before `(`: `if (..)`, `for (..)`, `while (..)`, `switch (..)`, `try (..)`, `catch (..)`, `synchronized (..)`.
    - Methods: no space before `(` in declarations or invocations: `void foo(int x)` and `foo(x)`.
    - No extra spaces just inside parentheses: `method(a, b)`, not `method( a, b )`.
- Operators and punctuation
    - Put spaces around binary and assignment operators: `a + b`, `x = y`.
    - No spaces for unary prefix/postfix operators: `i++`, `--i`, `!flag`.
    - Commas: space after, none before: `f(a, b)`.
    - Ternary: spaces around `?` and `:`: `cond ? a : b`.
- Generics, arrays, casts
    - No spaces inside `<...>` and around wildcards: `List<? extends T>`.
    - Array brackets have no inner spaces: `int[] a`, `a[0]`.
    - Array initializers use a space after `{` and before `}` when on one line: `{ 1, 2 }`.
    - Casts have no inner space: `(Type) value`.
- Blank lines and structure
    - 1 blank line after `package`.
    - 1 blank line before imports; 1 after imports; keep 1 between import groups.
    - 0 blank lines before fields; 1 blank line before methods; 1 before member types; 1 between top-level types.
- Switch formatting
    - `case` labels align with `switch`. Statements under a `case` are indented. `break` aligns with those statements.
- Comments and Javadoc
    - Format Javadoc, insert new lines at boundaries, and indent parameter descriptions. Use `@param`/`@return` each on its own line.


## 4. Logging
- Use org.waveprotocol.wave.util.logging.Log; do not use System.out/err.
- Create a logger per class: `private static final Log LOG = Log.get(MyClass.class);`
- Log levels: severe (fatal errors), warning (recoverable problems), info (high‑level events), fine/finer/finest (debug/trace).
- Guard expensive debug logs: `if (LOG.isFineLoggable()) { ... }`.
- Include contextual data (ids, sizes, state); avoid PII, tokens, or secrets.
- Log exceptions with stack trace: `LOG.warning("message", e);` or `LOG.severe("message", e);`
- Do not swallow exceptions silently; either handle with remediation or propagate.

Mapped diagnostic context:
- Use `LOG.putContext(key, value)` to add request/thread context when helpful; remove with `LOG.removeContext(key)` when done.


## 5. Error handling
- Fail fast on invalid state; validate inputs early (null checks, range checks).
- Use precise exception types; wrap low‑level exceptions with context preserving the cause.
- Never catch broad Exception just to ignore it; at minimum, log with context.
- For servlets and RPCs, set appropriate HTTP status codes and content type; do not leak internal details in responses; log the details server‑side.


## 6. Concurrency and thread‑safety
- Be aware of shared mutable state. Prefer immutability (e.g., Guava immutable collections) and final fields.
- Use java.util.concurrent primitives and concurrent collections where needed.
- Avoid static mutable state; if required, synchronize access and document invariants.
- When adding background tasks or callbacks, document threading expectations and memory visibility.


## 7. Performance and efficiency
- Choose appropriate data structures; avoid unnecessary copying and boxing.
- Avoid work in hot paths (e.g., logging string building) unless enabled; defer with guards.
- Stream or iterate lazily when dealing with large collections.
- Cache computed values when beneficial and safe; document cache invalidation policy.
- Keep algorithmic complexity in mind (prefer linear or better in hot paths).


## 8. Protocol Buffers (.proto)
- Use proto2 syntax (as in the repository).
- Maintain backward and forward compatibility:
  - Never reuse field numbers; mark fields as deprecated rather than deleting.
  - Only add optional/repeated fields; avoid making required changes to existing fields.
  - Preserve package and Java options (java_package, java_outer_classname, java_generic_services) consistent with existing files.
- Field names: lower_snake_case; add clear comments for each message and field.
- Regenerate code via Gradle; do not edit generated classes.


## 9. Configuration (HOCON)
- Default configuration lives in wave/config/reference.conf. Do not hard‑code config in code.
- Override defaults via wave/config/application.conf and/or system properties.
- Document new configuration keys in reference.conf with clear comments and sensible defaults.
- Avoid parsing user input directly; validate and normalize.


## 10. API and design principles
- Single Responsibility: each class/method does one thing well.
- DRY: extract shared logic; avoid copy‑paste. Prefer utility methods or well‑placed components.
- Favor composition over inheritance.
- Keep methods short and cohesive; return early on guard conditions.
- Clearly define public vs internal APIs; keep visibility as low as practical.


## 11. Client and server specifics
Server (servlets/RPC):
- Always set response content type explicitly and add security headers where applicable (e.g., X‑Content‑Type‑Options: nosniff).
- Validate authentication/authorization early and consistently.
- Log important decisions and outcomes at appropriate levels; do not log secrets.

Client/websocket paths:
- Maintain compatibility with older servers/clients when adding optional fields; handle absence of fields defensively.
- Do not block UI threads; keep callbacks lightweight and dispatch heavy work appropriately.


## 12. Security
- Treat all inputs as untrusted. Validate and sanitize.
- Do not log credentials, tokens, or PII.
- Prefer safe builders/encoders for URLs and JSON; escape user‑controlled data.
- Keep dependencies up‑to‑date; avoid using vulnerable versions.


## 13. Testing
- Place unit tests under wave/src/test/java (or pst/src/test/java for pst).
- Write tests for new features and bug fixes (happy path, edge cases, error handling).
- Keep tests deterministic and independent. Avoid relying on external services when possible.
- Run `./gradlew test` before committing. For broader checks, run `./gradlew check`.


## 14. Dependencies and third‑party code
- Prefer well‑known, maintained libraries. Justify new dependencies.
- Avoid duplicating functionality available in the JDK or existing project libraries.
- Ensure licenses of third‑party code are compatible with Apache 2.0.


## 15. Contribution checklist
Before submitting a PR:
- License headers present on all new/modified files.
- Code is formatted with the Eclipse profile; no unused imports; line length ≤ 100.
- Logging and error handling follow these guidelines; no swallowed exceptions.
- Thread‑safety considerations documented; no unintended shared mutable state.
- Tests added/updated and passing locally (`./gradlew clean check`).
- Configuration keys documented with defaults; no hard‑coded environment specifics.
- No secrets/PII checked in; no debug prints.
- Commit messages are clear and reference issues where applicable.
