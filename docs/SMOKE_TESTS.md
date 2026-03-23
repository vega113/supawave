# Smoke Tests Summary

Date: 2026-03-23
Environment:
- OS: macOS
- Shell: zsh
- Java: JDK 17 (per local JAVA_HOME)
- Gradle: Wrapper 8.7
- Server profile: Jakarta-only (Jetty 12 EE10)

## Automated smoke script

The preferred smoke path uses `scripts/wave-smoke.sh` against the installed
distribution:

1. Build: `./gradlew :wave:installDist`
2. Start: `bash scripts/wave-smoke.sh start` (waits for HTTP readiness)
3. Check: `bash scripts/wave-smoke.sh check`
   - Expected: `ROOT_STATUS=302`, `WEBCLIENT_STATUS=200`
4. Stop: `bash scripts/wave-smoke.sh stop`

## Gradle-level smoke

- `./gradlew -q :wave:compileJava` -- passes.
- `./gradlew -q :wave:smokeUi` -- passes (ROOT=302, WEBCLIENT=200).
- `./gradlew -q :wave:test` -- still blocked at `:wave:compileTestJava` by
  legacy test debt (see `docs/current-state.md` for details).

## Build artifacts

- PST JARs under `pst/build/libs`
- Wave JAR under `wave/build/libs`; `installDist` produces a runnable
  distribution at `wave/build/install/wave/`

## Docker

Build the image (multi-stage, Java 17, Jakarta-only):

    docker build -t wave:dev .

Run (HTTP on 9898):

    docker run --rm -p 9898:9898 wave:dev

## Previous results

Commands executed:
- `./gradlew --no-daemon --warning-mode all :pst:build :wave:build` -- SUCCESS
- `./gradlew :wave:installDist` -- SUCCESS
- `scripts/wave-smoke.sh start && check && stop` -- ROOT=302, WEBCLIENT=200

Notable Gradle deprecation warnings (tracked for Gradle 9 cleanup):
- In pst/build.gradle:
  - Deprecated: org.gradle.api.plugins.Convention
  - Deprecated: org.gradle.api.plugins.JavaPluginConvention
  - Deprecated: org.gradle.util.ConfigureUtil
- In wave/build.gradle:
  - Deprecated: ApplicationPluginConvention (due to applicationDefaultJvmArgs usage)
  - Deprecated: Relying on Test.classpath convention in custom Test task (testGwt)
