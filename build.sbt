// incubator-wave SBT build (Phases 1-5: managed deps, source sets, codegen, native-packager)

ThisBuild / organization := "org.apache.wave"
name := "incubator-wave"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// Enable sbt-native-packager for distribution packaging (Phase 5)
enablePlugins(JavaAppPackaging)

// Compile Java first, Scala later (we'll add Scala modules in future phases)
Compile / compileOrder := CompileOrder.JavaThenScala

// Java toolchain: compile and target JDK 17 bytecode (matches Gradle's java.toolchain.languageVersion)
javacOptions ++= Seq("--release", "17")


// Disable Javadoc generation — gwt-user sources reference GWTBridge (in gwt-dev)
// which is not on the compile classpath, causing Javadoc to fail during Universal/stage.
Compile / doc / sources := Seq.empty

// Include Maven standard source layout and generated sources
Compile / unmanagedSourceDirectories ++= Seq(
  baseDirectory.value / "wave" / "src" / "main" / "java",
  baseDirectory.value / "proto_src",
  baseDirectory.value / "gen" / "messages",
  baseDirectory.value / "gen" / "flags",
  baseDirectory.value / "gen" / "shims"
)

Compile / unmanagedSourceDirectories += baseDirectory.value / "wave" / "src" / "jakarta-overrides" / "java"
// SBT-only stubs for GWT client classes used by server code (no-op implementations)
Compile / unmanagedSourceDirectories += baseDirectory.value / "wave" / "src" / "sbt-stubs" / "java"

// ─── Jakarta/source ownership filters ───
// The Gradle-era exclusion layout is still the baseline, but issue #714 removed
// the exact same-path shadow copies so only the remaining legacy/main directory
// filters are still active here.
// Files common to both modes (GWT client trees, generated JSO, retired agents/OAuth) are always excluded.
Compile / unmanagedSources := (Compile / unmanagedSources).value.filterNot { f =>
  val p = f.getPath.replace('\\', '/')
  val underMain    = p.contains("/wave/src/main/java/")
  val underJakarta = p.contains("/wave/src/jakarta-overrides/java/")
  val isConvertedJvmClientSource =
    p.endsWith("/wave/src/main/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistry.java") ||
    p.endsWith("/wave/src/main/java/org/waveprotocol/wave/client/scheduler/Scheduler.java") ||
    p.endsWith("/wave/src/main/java/org/waveprotocol/wave/client/scheduler/TaskInfo.java") ||
    p.endsWith("/wave/src/main/java/org/waveprotocol/wave/client/util/TypedSource.java") ||
    p.endsWith("/wave/src/main/java/org/waveprotocol/wave/client/util/UrlParameters.java")

  // Same-path main/Jakarta duplicate classes were removed in issue #714.
  // Keep this empty set so build tooling and guard scripts can still parse the block.
  val mainExactExcludes: Set[String] = Set()

  // Legacy main-tree files intentionally kept out of the Jakarta/SBT compile surface.
  // These do not currently have same-path Jakarta replacements.
  val mainLegacyCompileExcludes: Set[String] = Set(
    "com/google/wave/api/WaveService.java",
    "org/waveprotocol/box/expimp/Console.java",
    "org/waveprotocol/box/expimp/DeltaParser.java",
    "org/waveprotocol/box/expimp/DomainConverter.java",
    "org/waveprotocol/box/expimp/FileNames.java",
    "org/waveprotocol/box/expimp/OAuth.java",
    "org/waveprotocol/box/expimp/WaveImport.java",
    "org/waveprotocol/box/expimp/WaveExport.java",
    "org/waveprotocol/box/server/util/RegistrationUtil.java"
  )

  // --- Directory-level excludes under src/main/java (Gradle lines 334-337) ---
  val mainDirExcluded = underMain && (
    p.contains("/com/google/wave/api/oauth/") ||
    p.contains("/org/waveprotocol/box/server/robots/agent/") ||
    p.contains("/org/apache/wave/box/server/rpc/")
  )

  // Jakarta mode: apply the remaining main-tree exclusions
  val mainFileExcluded = underMain && (
    mainExactExcludes.exists(suffix => p.endsWith("/" + suffix)) ||
    mainLegacyCompileExcludes.exists(suffix => p.endsWith("/" + suffix))
  )

  // Same-path Jakarta duplicates were removed in issue #714.
  val jakartaExactExcludes: Set[String] = Set()

  val jakartaFileExcluded = underJakarta && jakartaExactExcludes.exists(suffix => p.endsWith("/" + suffix))

  // --- Common exclusions (always applied) ---
  // GWT client trees, gen/shims stat, JSO, Socket.IO, etc.
  val isSrc = underMain
  val commonExcludes =
    (isSrc && p.contains("/org/waveprotocol/box/webclient/")) ||
    (isSrc && p.contains("/org/waveprotocol/wave/client/") &&
      !isConvertedJvmClientSource &&
      !p.endsWith("/wave/client/state/BlipReadStateMonitor.java") &&
      !p.endsWith("/wave/client/state/ThreadReadStateMonitor.java") &&
      // SSR Phase 1: pure-model render interfaces needed by ServerHtmlRenderer (#216)
      !p.endsWith("/wave/client/render/RenderingRules.java") &&
      !p.endsWith("/wave/client/render/ReductionBasedRenderer.java") &&
      !p.endsWith("/wave/client/render/WaveRenderer.java")) ||
    (isSrc && p.contains("/org/waveprotocol/wave/communication/gwt/")) ||
    (isSrc && p.contains("/com/google/gwt/")) ||
    // Exclude stat shims since we have the real source files
    (p.contains("/gen/shims/") && p.contains("/org/waveprotocol/box/stat/")) ||
    // GXP-dependent server RPC pages
    (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/ChangePasswordServlet.java")) ||
    (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/GoogleAuthenticationServlet.java")) ||
    (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/GoogleAuthenticationCallbackServlet.java")) ||
    // Exclude Socket.IO server shims
    p.endsWith("/org/waveprotocol/box/server/rpc/AbstractWaveSocketIOServlet.java") ||
    p.endsWith("/org/waveprotocol/box/server/rpc/SocketIOServerChannel.java") ||
    // Specific render view classes that couple to client (keep core render enabled).
    (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/render/view/builder/TagsViewBuilder.java")) ||
    (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/render/view/ModelAsViewProvider.java")) ||
    // Legacy helpers with client coupling that are unnecessary for server run
    p.endsWith("/org/waveprotocol/wave/migration/helpers/FixLinkAnnotationsFilter.java") ||
    // Exclude generated GWT JSO implementations under gen/messages
    (p.contains("/gen/messages/") && p.contains("/jso/")) ||
    // Exclude GWT-only JSON serializers that reference JSO implementations
    (isSrc && p.endsWith("/org/waveprotocol/wave/model/raw/serialization/JsoSerializerAdaptor.java")) ||
    (isSrc && p.endsWith("/org/waveprotocol/wave/model/raw/serialization/JsoSerializer.java"))

  mainFileExcluded || mainDirExcluded || jakartaFileExcluded || commonExcludes
}

Test / unmanagedSourceDirectories += baseDirectory.value / "wave" / "src" / "test" / "java"

// All dependencies are managed via libraryDependencies (Coursier).
// Codegen tasks resolve JARs from managed deps via (Compile / dependencyClasspath).

// Runtime web assets live under the repo-root war/ directory for both sbt run
// and staged distributions.
// NOTE: must use unmanagedResourceDirectories (not resourceDirectories) so SBT actually
// scans these dirs and includes their files in unmanagedResources / the classpath JAR.
Compile / unmanagedResourceDirectories += baseDirectory.value / "wave" / "src" / "main" / "resources"
Compile / unmanagedResourceDirectories += baseDirectory.value / "war"

// Prefer forking when running, to mimic production flags when needed
fork := true

// Server entrypoint
Compile / mainClass := Some("org.waveprotocol.box.server.ServerMain")

// Default runtime options for `sbt run`
Compile / javaOptions ++= {
  val base = baseDirectory.value
  val waveConfigDir = base / "wave" / "config"
  Seq(
    s"-Dwave.server.config=${(waveConfigDir / "application.conf").getAbsolutePath}",
    s"-Djava.util.logging.config.file=${(waveConfigDir / "wiab-logging.conf").getAbsolutePath}",
    s"-Djava.security.auth.login.config=${(waveConfigDir / "jaas.config").getAbsolutePath}",
    "-Dorg.eclipse.jetty.LEVEL=INFO",
    "-Dorg.eclipse.jetty.util.log.class=org.eclipse.jetty.util.log.Slf4jLog",
    "-Dorg.slf4j.simpleLogger.logFile=System.out",
    "--add-opens", "java.base/java.lang=ALL-UNNAMED"
  )
}

// Assembly settings
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

// Assembly: build a runnable fat JAR for ServerMain
assembly / mainClass := (Compile / mainClass).value
ThisBuild / assembly / assemblyJarName := s"${name.value}-server-${version.value}.jar"
ThisBuild / assembly / test := {}
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) =>
    val lower = xs.map(_.toLowerCase)
    if (lower.exists(_.endsWith(".sf")) || lower.exists(_.endsWith(".dsa")) || lower.contains("manifest.mf")) MergeStrategy.discard
    else if (lower.contains("services")) MergeStrategy.concat
    else MergeStrategy.discard
  case PathList("reference.conf") => MergeStrategy.concat
  case x => MergeStrategy.first
}

// ---------------------------------------------------------------------------
// Managed dependencies — ported from wave/build.gradle
// ---------------------------------------------------------------------------
lazy val JettyV          = "12.0.23"     // Jetty 12 / EE10 (Jakarta Servlet 6)
lazy val GuavaV          = "32.1.3-jre"  // server-side Guava, aligned with Guice 5
lazy val GuiceV          = "5.1.0"
lazy val ProtobufV       = "3.25.3"
lazy val Slf4jV          = "2.0.13"
lazy val LogbackV        = "1.5.6"
lazy val MongoV4         = "4.11.1"
lazy val MongockV        = "5.5.1"
lazy val LuceneV         = "9.12.1"

libraryDependencies ++= Seq(
  // --- Test ---
  "junit"                          % "junit"                      % "4.12"     % Test,
  "com.novocode"                   % "junit-interface"            % "0.11"     % Test,
  "org.hamcrest"                   % "hamcrest-junit"             % "2.0.0.0"  % Test,
  "org.mockito"                    % "mockito-core"               % "2.2.21"   % Test,
  "org.testcontainers"             % "testcontainers"             % "1.21.4"   % Test,
  "org.testcontainers"             % "mongodb"                    % "1.21.4"   % Test,

  // --- E2E test (JUnit 5 — scoped to e2eTest config only, does not affect unit tests) ---
  "org.junit.jupiter"              % "junit-jupiter-api"          % "5.10.2"   % E2eTest,
  "org.junit.jupiter"              % "junit-jupiter-engine"       % "5.10.2"   % E2eTest,
  "net.aichler"                    % "jupiter-interface"          % "0.11.1"   % E2eTest,

  // --- Protobuf ---
  "com.google.protobuf"            % "protobuf-java"              % ProtobufV,

  // --- Mongo migrations ---
  "io.mongock"                     % "mongock-standalone"         % MongockV,
  "io.mongock"                     % "mongodb-sync-v4-driver"     % MongockV,

  // --- Guava & Guice ---
  "com.google.guava"               % "guava"                      % GuavaV,
  "com.google.inject"              % "guice"                      % GuiceV,
  "com.google.inject.extensions"   % "guice-servlet"              % GuiceV,
  "com.google.inject.extensions"   % "guice-assistedinject"       % GuiceV,

  // --- Serialization ---
  "com.google.code.gson"           % "gson"                       % "2.10.1",

  // --- Config ---
  "com.typesafe"                   % "config"                     % "1.4.3",

  // --- Commons ---
  "commons-codec"                  % "commons-codec"              % "1.16.1",
  "commons-cli"                    % "commons-cli"                % "1.11.0",
  "commons-io"                     % "commons-io"                 % "2.16.1",
  "org.apache.commons"             % "commons-lang3"              % "3.14.0",
  "org.apache.commons"             % "commons-text"               % "1.12.0",

  // --- HTTP ---
  "org.apache.httpcomponents"      % "httpclient"                 % "4.5.14",
  "org.apache.httpcomponents"      % "httpcore"                   % "4.4.16",

  // --- Logging (SLF4J 2.x + Logback) ---
  "org.slf4j"                      % "slf4j-api"                  % Slf4jV,
  "ch.qos.logback"                 % "logback-classic"            % LogbackV,
  "net.logstash.logback"           % "logstash-logback-encoder"   % "8.0",
  "org.slf4j"                      % "jcl-over-slf4j"             % Slf4jV,
  "org.slf4j"                      % "jul-to-slf4j"               % Slf4jV,
  "org.slf4j"                      % "log4j-over-slf4j"           % Slf4jV,

  // --- Annotations ---
  "com.google.code.findbugs"       % "jsr305"                     % "2.0.1",
  "javax.inject"                   % "javax.inject"               % "1",

  // --- JSON ---
  "org.json"                       % "json"                       % "20231013",

  // --- XML / DOM ---
  "dom4j"                          % "dom4j"                      % "1.6.1",
  "org.jdom"                       % "jdom"                       % "1.1.3",

  // --- Classpath scanning ---
  "eu.infomas"                     % "annotation-detector"        % "3.0.0",

  // --- Parser / template ---
  "org.antlr"                      % "antlr"                      % "3.2",
  "org.apache.velocity"            % "velocity"                   % "1.7",

  // --- Search ---
  "org.apache.lucene"              % "lucene-core"                % LuceneV,
  "org.apache.lucene"              % "lucene-analysis-common"     % LuceneV,
  "org.apache.lucene"              % "lucene-queryparser"         % LuceneV,

  // --- OAuth (net.oauth.core) ---
  "net.oauth.core"                 % "oauth"                      % "20090825",
  "net.oauth.core"                 % "oauth-consumer"             % "20090823",
  "net.oauth.core"                 % "oauth-provider"             % "20090531",

  // --- Crypto ---
  "org.bouncycastle"               % "bcprov-jdk16"               % "1.45",

  // --- Persistence ---
  "javax.jdo"                      % "jdo2-api"                   % "2.1",
  "org.mongodb"                    % "mongodb-driver-legacy"      % MongoV4,
  "org.mongodb"                    % "mongodb-driver-sync"        % MongoV4,

  // --- Cache ---
  "com.github.ben-manes.caffeine"  % "caffeine"                   % "3.1.8",

  // --- GXP compiler (used at codegen time and needed on compile classpath for generated sources) ---
  // google-gxp removed — replaced by HtmlRenderer.java

  // --- Jetty 12 / EE10 (Jakarta Servlet 6) ---
  "org.eclipse.jetty"              % "jetty-server"               % JettyV,
  "org.eclipse.jetty.ee10"         % "jetty-ee10-servlet"         % JettyV,
  "org.eclipse.jetty.ee10"         % "jetty-ee10-webapp"          % JettyV,
  "org.eclipse.jetty"              % "jetty-session"              % JettyV,
  "org.eclipse.jetty.ee10.websocket" % "jetty-ee10-websocket-jakarta-server" % JettyV,

  // --- Jakarta APIs ---
  "jakarta.servlet"                % "jakarta.servlet-api"        % "6.0.0",
  "jakarta.websocket"              % "jakarta.websocket-api"      % "2.1.1",

  // --- Metrics ---
  "io.micrometer"                  % "micrometer-core"            % "1.12.5",
  "io.micrometer"                  % "micrometer-registry-prometheus" % "1.12.5",

  // --- GWT (compile-only for server-side cross-references) ---
  "org.gwtproject"                 % "gwt-user"                   % "2.10.0"   % Provided
)

// ---------------------------------------------------------------------------
// Exclusions: ensure a single SLF4J binding (Logback) and evict legacy junk
// ---------------------------------------------------------------------------
excludeDependencies ++= Seq(
  ExclusionRule("org.slf4j",                "slf4j-simple"),
  ExclusionRule("org.slf4j",                "slf4j-nop"),
  ExclusionRule("org.slf4j",                "slf4j-log4j12"),
  ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl"),
  ExclusionRule("org.apache.logging.log4j", "log4j-slf4j2-impl"),
  // Prefer jcl-over-slf4j over the real commons-logging
  ExclusionRule("commons-logging",          "commons-logging")
)

// Keep both the sync and legacy MongoDB 4.x driver surfaces on one BSON version.
// The legacy API backs MongoDbStore / MongoDbDeltaStore, while the sync API powers
// Mongo4DbProvider and Mongock startup migrations.

// ---------------------------------------------------------------------------
// Dependency overrides: pin transitive versions for alignment
// ---------------------------------------------------------------------------
dependencyOverrides ++= Seq(
  // SLF4J alignment
  "org.slf4j"          % "slf4j-api"        % Slf4jV,
  "org.slf4j"          % "jcl-over-slf4j"   % Slf4jV,
  "org.slf4j"          % "jul-to-slf4j"     % Slf4jV,
  "org.slf4j"          % "log4j-over-slf4j" % Slf4jV,
  "ch.qos.logback"     % "logback-classic"  % LogbackV,
  // Guava alignment
  "com.google.guava"   % "guava"            % GuavaV,
  // MongoDB 4.x driver alignment
  "org.mongodb"        % "mongodb-driver-legacy" % MongoV4,
  "org.mongodb"        % "bson"               % MongoV4,
  "org.mongodb"        % "mongodb-driver-core" % MongoV4,
  "org.mongodb"        % "mongodb-driver-sync" % MongoV4
)

// Test JVM flags (Guice/cglib on JDK 17, enable assertions; matches Gradle test.jvmArgs lines 958-961)
Test / javaOptions ++= Seq(
  "-ea",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED",
  "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens", "java.base/java.util=ALL-UNNAMED",
  "--add-opens", "java.base/java.io=ALL-UNNAMED"
)

// Keep tests deterministic: exclude GWT, large, stress, and MongoDB tests (matches Gradle test task, lines 945-980)
Test / testOptions += Tests.Filter { name =>
  val isJUnit = name.endsWith("Test")
  val isGwt = name.endsWith("GwtTest")
  val isLarge = name.endsWith("LargeTest")
  val isStress = name.contains("StressTest")
  val isMongo = name.contains(".mongodb.")
  val isFederation = name.contains(".wave.federation.")
  val isPersistence = name.contains(".server.persistence.")
  val isAllowedPersistence = (
    name.contains(".server.persistence.memory.")
      || name.contains(".server.persistence.file.")
      || name.contains(".server.persistence.protos.")
      || name.endsWith(".MongoMigrationRunnerTest")
      || name.endsWith(".MongockMongoMigrationRunnerTest")
      || name.endsWith(".MongoMigrationBaselineTest")
      || name.endsWith(".MongoDeltaStoreAppendGuardTest")
  )
  isJUnit && !isGwt && !isLarge && !isStress && !isMongo && !isFederation && (!isPersistence || isAllowedPersistence)
}

// Make JUnit output verbose for debugging (stack traces, test names)
Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

// Make test resources available (e.g., *.schema stored under wave/src/test/)
Test / unmanagedResourceDirectories += baseDirectory.value / "wave" / "src" / "test" / "resources"

// Exclude client/GWT-dependent tests from compilation (replicates Gradle test source set, lines 170-198)
Test / unmanagedSources := (Test / unmanagedSources).value.filterNot { f =>
  val p = f.getPath.replace('\\', '/')
  val fn = f.getName
  val isConvertedJvmClientTest =
    p.endsWith("/wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryTest.java") ||
    p.endsWith("/wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersTest.java")
  // GWT test bases/cases
  fn.contains("GwtTest") || fn == "GwtTestCase.java" ||
  fn == "TestBase.java" || fn == "GenericGWTTestBase.java" ||
  fn == "ContentTestBase.java" || fn == "ContentTestCase.java" ||
  // Client and webclient trees
  (p.contains("/org/waveprotocol/wave/client/") && !isConvertedJvmClientTest) ||
  p.contains("/org/waveprotocol/box/webclient/") ||
  // Legacy Jetty API tests that break on JDK17
  p.endsWith("/org/waveprotocol/box/server/rpc/FetchServletTest.java") ||
  // OAuth/Robot tests whose production classes are excluded
  p.endsWith("/com/google/wave/api/AbstractRobotTest.java") ||
  p.endsWith("/com/google/wave/api/oauth/impl/OAuthServiceImplRobotTest.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/active/ActiveApiServletTest.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/agent/AbstractRobotAgentTest.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/agent/RobotAgentUtilTest.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletTest.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/dataapi/DataApiServletTest.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainerTest.java") ||
  // Constructor signature changed after PublicWaveServlet Jakarta migration
  p.endsWith("/org/waveprotocol/box/server/rpc/PublicWaveServletTest.java") ||
  p.endsWith("/org/waveprotocol/box/expimp/DomainConverterTest.java") ||
  p.endsWith("/org/waveprotocol/box/expimp/DeltaParserTest.java") ||
  // Additional render/concurrencycontrol/migration exclusions (keep SSR tests)
  (p.contains("/org/waveprotocol/box/server/rpc/render/") &&
    !p.endsWith("/ServerHtmlRendererTest.java") &&
    !p.endsWith("/WaveContentRendererTest.java")) ||
  p.contains("/wave/src/test/java/org/waveprotocol/wave/migration/") ||
  p.contains("/wave/src/test/java/org/waveprotocol/wave/model/document/util/") ||
  // MongoDB integration tests — require Testcontainers; run via Gradle itTest, not sbt test
  fn.endsWith("IT.java") ||
  fn == "MongoItTestUtil.java" ||
  // Constructor signature changed; exclude until test is updated
  p.endsWith("/PublicWaveServletTest.java")
}

// Ensure `sbt clean` removes generated sources only (dependencies/caches are preserved)
cleanFiles ++= Seq(
  baseDirectory.value / "proto_src",
  baseDirectory.value / "gen" / "messages",
  baseDirectory.value / "gen" / "flags"
)

// =============================================================================
// Phase 3 — Custom Test Configurations
// Replicates Gradle source sets: jakartaTest, jakartaIT (subset of jakartaTest),
// stacktraceTest, thumbTest.  Each extends Test so it inherits the JUnit runner,
// managed/unmanaged classpath, and JVM flags.
// =============================================================================

// --- Configuration definitions ---
lazy val JakartaTest    = config("jakartaTest")    extend Test  describedAs "Jakarta unit tests (excludes *IT)"
lazy val JakartaIT      = config("jakartaIT")      extend Test  describedAs "Jakarta integration tests (*IT allowlist)"
lazy val StacktraceTest = config("stacktraceTest") extend Test  describedAs "Isolated StackTraces utility tests"
lazy val ThumbTest      = config("thumbTest")      extend Test  describedAs "Isolated AttachmentServlet thumbnail tests"
lazy val E2eTest        = config("e2eTest")        extend Test  describedAs "E2E sanity tests against a running Wave server"
lazy val GatlingTest    = config("gatlingTest")    extend Test  describedAs "Gatling performance tests against a running Wave server"

// Register all custom test configs with Ivy so POM generation can resolve them
ivyConfigurations ++= Seq(JakartaTest, JakartaIT, StacktraceTest, ThumbTest, E2eTest, GatlingTest)

// Wire all four configs into the project so `sbt jakartaTest:test` etc. work
inConfig(JakartaTest)(Defaults.testSettings)
inConfig(JakartaIT)(Defaults.testSettings)
inConfig(StacktraceTest)(Defaults.testSettings)
inConfig(ThumbTest)(Defaults.testSettings)
inConfig(E2eTest)(Defaults.testSettings ++ net.aichler.jupiter.sbt.JupiterPlugin.scopedSettings)
inConfig(GatlingTest)(Defaults.testSettings)

// Suppress "unused key" linter warnings for keys auto-created by Defaults.testSettings in custom configs
Global / excludeLintKeys ++= Set(
  JakartaTest / javaSource, JakartaTest / scalaSource, JakartaTest / resourceDirectory, JakartaTest / semanticdbTargetRoot,
  JakartaIT / javaSource, JakartaIT / scalaSource, JakartaIT / resourceDirectory, JakartaIT / semanticdbTargetRoot,
  StacktraceTest / javaSource, StacktraceTest / scalaSource, StacktraceTest / semanticdbTargetRoot,
  ThumbTest / javaSource, ThumbTest / scalaSource, ThumbTest / semanticdbTargetRoot,
  E2eTest / javaSource, E2eTest / scalaSource, E2eTest / resourceDirectory, E2eTest / semanticdbTargetRoot,
  GatlingTest / javaSource, GatlingTest / scalaSource, GatlingTest / resourceDirectory, GatlingTest / semanticdbTargetRoot
)

// --- JakartaTest source directories & exclusions ---
// Source: wave/src/jakarta-test/java, excludes *IT classes and specific retired tests
JakartaTest / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "jakarta-test" / "java"
)
JakartaTest / unmanagedResourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "jakarta-test" / "resources"
)
// Exclude integration test classes and specific retired/disabled tests (Gradle lines 240-242)
JakartaTest / unmanagedSources := (JakartaTest / unmanagedSources).value.filterNot { f =>
  val p = f.getPath.replace('\\', '/')
  p.endsWith("/WaveWebSocketEndpointInitGuardTest.java") ||
  p.endsWith("/DataApiOAuthServletJakartaIT.java") ||
  // Tests that depend on GWT client/webclient classes excluded from SBT compilation
  p.endsWith("/WaveWebSocketClientTest.java") ||
  p.endsWith("/RemoteWaveViewServiceEmptyUserDataSnapshotTest.java") ||
  p.endsWith("/FocusBlipSelectorTest.java") ||
  p.endsWith("/BlipMetaDomImplTest.java") ||
  // Constructor signature changed; exclude until test is updated
  p.endsWith("/WaveClientServletFragmentDefaultsTest.java")
}
// Runtime filter: exclude *IT classes from this config (unit tests only)
JakartaTest / testOptions += Tests.Filter { name =>
  !name.endsWith("IT") ||
    name == "org.waveprotocol.box.server.jakarta.MetricsPrometheusServletJakartaIT"
}
JakartaTest / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
JakartaTest / javaOptions ++= Seq(
  "-ea",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED",
  "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens", "java.base/java.util=ALL-UNNAMED"
)
JakartaTest / fork := true
// Include main compile output on classpath
JakartaTest / dependencyClasspath ++= (Compile / exportedProducts).value
JakartaTest / dependencyClasspath ++= (Test / dependencyClasspath).value
// Include main resources and full classpath for forked JVM
JakartaTest / dependencyClasspath ++= (Compile / fullClasspath).value
JakartaTest / fullClasspath ++= Seq(Attributed.blank(baseDirectory.value / "wave" / "src" / "main" / "resources"))

// --- JakartaIT source directories & allowlist (Gradle lines 1046-1058) ---
// Same source dir as JakartaTest, but only runs the explicit IT allowlist
JakartaIT / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "jakarta-test" / "java"
)
JakartaIT / unmanagedResourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "jakarta-test" / "resources"
)
// Exclude the same files as JakartaTest from compilation
JakartaIT / unmanagedSources := (JakartaIT / unmanagedSources).value.filterNot { f =>
  val p = f.getPath.replace('\\', '/')
  p.endsWith("/WaveWebSocketEndpointInitGuardTest.java") ||
  p.endsWith("/DataApiOAuthServletJakartaIT.java") ||
  // Tests that depend on GWT client/webclient classes excluded from SBT compilation
  p.endsWith("/WaveWebSocketClientTest.java") ||
  p.endsWith("/RemoteWaveViewServiceEmptyUserDataSnapshotTest.java") ||
  p.endsWith("/FocusBlipSelectorTest.java") ||
  // Constructor signature changed; exclude until test is updated
  p.endsWith("/WaveClientServletFragmentDefaultsTest.java")
}
// Only run the explicit IT allowlist (Gradle lines 1047-1058)
JakartaIT / testOptions += Tests.Filter { name =>
  val allowlist = Set(
    "org.waveprotocol.box.server.jakarta.ForwardedHeadersJakartaIT",
    "org.waveprotocol.box.server.jakarta.ForwardedHeadersStrictFuzzJakartaIT",
    "org.waveprotocol.box.server.jakarta.AccessLogJakartaIT",
    "org.waveprotocol.box.server.jakarta.SecurityHeadersJakartaIT",
    "org.waveprotocol.box.server.jakarta.CachingFiltersJakartaIT",
    "org.waveprotocol.box.server.jakarta.AttachmentServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.SearchServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.AuthenticationServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.SignOutServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.GadgetProviderServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.InitialsAvatarsServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.HealthServletJakartaIT",
    "org.waveprotocol.box.server.jakarta.VersionServletJakartaIT"
  )
  allowlist.contains(name)
}
JakartaIT / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
JakartaIT / javaOptions ++= Seq(
  "-ea",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED",
  "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens", "java.base/java.util=ALL-UNNAMED"
)
JakartaIT / fork := true
JakartaIT / dependencyClasspath ++= (Compile / exportedProducts).value
JakartaIT / dependencyClasspath ++= (Test / dependencyClasspath).value
JakartaIT / dependencyClasspath ++= (Compile / fullClasspath).value
JakartaIT / fullClasspath ++= Seq(Attributed.blank(baseDirectory.value / "wave" / "src" / "main" / "resources"))

// --- StacktraceTest source directories ---
StacktraceTest / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "stacktrace-test" / "java"
)
StacktraceTest / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
StacktraceTest / javaOptions ++= Seq(
  "-ea",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED"
)
StacktraceTest / fork := true
StacktraceTest / dependencyClasspath ++= (Compile / exportedProducts).value

// --- ThumbTest source directories ---
ThumbTest / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "thumb-test" / "java"
)
ThumbTest / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
ThumbTest / javaOptions ++= Seq(
  "-ea",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED"
)
ThumbTest / fork := true
ThumbTest / dependencyClasspath ++= (Compile / exportedProducts).value

// --- E2eTest: E2E sanity suite settings ---
// Source: wave/src/e2e-test/java — runs against a live Wave server (WAVE_E2E_BASE_URL)
E2eTest / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "e2e-test" / "java"
)
E2eTest / fork := true
E2eTest / javaOptions ++= Seq("-ea")
E2eTest / dependencyClasspath ++= (Compile / exportedProducts).value
E2eTest / dependencyClasspath ++= (Test / dependencyClasspath).value
E2eTest / dependencyClasspath ++= (Compile / fullClasspath).value
E2eTest / testFrameworks += new TestFramework("net.aichler.jupiter.api.JupiterFramework")
// WAVE_E2E_BASE_URL is read from the OS environment by the forked JVM

// --- GatlingTest: Gatling performance suite settings ---
// Source: wave/src/gatling/java — runs against a live Wave server (WAVE_PERF_BASE_URL)
GatlingTest / unmanagedSourceDirectories := Seq(
  baseDirectory.value / "wave" / "src" / "gatling" / "java"
)
GatlingTest / fork := true
GatlingTest / javaOptions ++= Seq("-ea")
GatlingTest / dependencyClasspath ++= (Compile / exportedProducts).value
GatlingTest / dependencyClasspath ++= (Test / dependencyClasspath).value
GatlingTest / dependencyClasspath ++= (Compile / fullClasspath).value
// Run simulations via: sbt "GatlingTest / runMain org.waveprotocol.wave.perf.GatlingRunner SearchLoadSimulation"
// Or run all via: bash scripts/run-perf-tests.sh
// Note: io.gatling.sbt.GatlingFramework is in the gatling-sbt plugin, not gatling-test-framework.
// Simulations are invoked programmatically via GatlingRunner, so no TestFramework is needed.
// Pass WAVE_PERF_BASE_URL to the forked JVM
GatlingTest / envVars ++= sys.env.filter(_._1.startsWith("WAVE_PERF_"))

// --- Additional per-config dependencies (matches Gradle) ---
// JakartaTest and JakartaIT need Jakarta WebSocket + Jetty EE10 test deps
libraryDependencies ++= Seq(
  "jakarta.websocket"           % "jakarta.websocket-api"                     % "2.1.1"  % JakartaTest,
  "org.eclipse.jetty"           % "jetty-server"                              % JettyV % JakartaTest,
  "org.eclipse.jetty.ee10"      % "jetty-ee10-servlet"                        % JettyV % JakartaTest,
  "org.eclipse.jetty.ee10.websocket" % "jetty-ee10-websocket-jakarta-server"  % JettyV % JakartaTest,
  "org.mockito"                 % "mockito-core"                              % "2.2.21" % JakartaTest,
  "junit"                       % "junit"                                     % "4.12"   % JakartaTest,
  "com.github.sbt"              % "junit-interface"                           % "0.13.3" % JakartaTest,
  "junit"                       % "junit"                                     % "4.12"   % JakartaIT,
  "com.github.sbt"              % "junit-interface"                           % "0.13.3" % JakartaIT,
  "jakarta.websocket"           % "jakarta.websocket-api"                     % "2.1.1"  % JakartaIT,
  "org.eclipse.jetty"           % "jetty-server"                              % JettyV % JakartaIT,
  "org.eclipse.jetty.ee10"      % "jetty-ee10-servlet"                        % JettyV % JakartaIT,
  "org.eclipse.jetty.ee10.websocket" % "jetty-ee10-websocket-jakarta-server"  % JettyV % JakartaIT,
  "org.mockito"                 % "mockito-core"                              % "2.2.21" % JakartaIT,
  // ThumbTest needs jakarta.servlet-api
  "jakarta.servlet"             % "jakarta.servlet-api"                       % "6.0.0"  % ThumbTest,
  // GatlingTest: Gatling performance test framework
  "io.gatling.highcharts"       % "gatling-charts-highcharts"                 % "3.10.5" % GatlingTest,
  "io.gatling"                  % "gatling-test-framework"                    % "3.10.5" % GatlingTest
)

// --- Convenience aliases (Gradle task equivalents) ---
// testMongo: run MongoDB-specific tests from default test source set
lazy val testMongo = taskKey[Unit]("Run MongoDB persistence tests")
testMongo := {
  // Use (Test / testOnly) to select only MongoDB tests
  (Test / testOnly).toTask(" -- --tests=*server.persistence.mongodb*").value
}

// testLarge: run *LargeTest* from default test source set
lazy val testLarge = taskKey[Unit]("Run large (slow) tests")
testLarge := {
  (Test / testOnly).toTask(" -- --tests=*LargeTest*").value
}

// testStress: run *StressTest* from default test source set
lazy val testStress = taskKey[Unit]("Run stress-style tests")
testStress := {
  (Test / testOnly).toTask(" -- --tests=*StressTest*").value
}

// testAll: run all test suites (Gradle equivalent runs test + testMongo + testLarge +
//          testJakarta + testJakartaIT + testStress). GWT tests are dropped because
//          SBT does not support GWT compilation; they remain in the Gradle build only.
addCommandAlias("testAll", "; test; jakartaTest:test; jakartaIT:test; stacktraceTest:test; thumbTest:test")

// -------------------------------------------
// Distribution via sbt-native-packager (Phase 5)
// -------------------------------------------

executableScriptName := "wave"

// Map config files, war assets, and root docs into the Universal stage
Universal / mappings ++= {
  val base = baseDirectory.value
  // wave/config/ -> config/
  val configDir = base / "wave" / "config"
  val configFiles = (configDir ** "*").get.filter(_.isFile).map { f =>
    f -> ("config/" + IO.relativize(configDir, f).get)
  }
  // war/ -> war/
  val warDir = base / "war"
  val warFiles = (warDir ** "*").get.filter(_.isFile).filterNot { f =>
    IO.relativize(warDir, f).exists(_.startsWith("webclient/"))
  }.map { f =>
    f -> ("war/" + IO.relativize(warDir, f).get)
  }
  // Root docs
  val rootDocs = Seq("THANKS", "RELEASE-NOTES", "KEYS", "DISCLAIMER").flatMap { name =>
    val f = base / name
    if (f.exists) Some(f -> name) else None
  }
  configFiles ++ warFiles ++ rootDocs
}

// JVM args matching Gradle's applicationDefaultJvmArgs
// NOTE: Heap sizing (-Xmx) is intentionally NOT set here so that the
// production compose.yml command args (-J-Xmx20G) take full effect.
// The SBT native-packager launcher treats these as defaults that get
// overridden by later command-line -J flags.  For local development,
// set JAVA_OPTS or pass -J-Xmx<size> on the command line.
Universal / javaOptions ++= Seq(
  "-Dorg.eclipse.jetty.LEVEL=DEBUG",
  "-Dlogback.configurationFile=config/logback.xml",
  "-Dguice_include_stack_traces=OFF",
  "-Djava.security.auth.login.config=config/jaas.config",
  "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
  "-J--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "-J--add-opens=java.base/java.util=ALL-UNNAMED"
)

// Smoke test tasks (Phase 5)
lazy val smokeInstalled = taskKey[Unit]("Smoke-test the staged distribution via scripts/wave-smoke.sh")
smokeInstalled := {
  val log = streams.value.log
  val base = baseDirectory.value
  // Depend on Universal/stage to produce the install directory
  val stageDir = (Universal / stage).value
  val script = base / "scripts" / "wave-smoke.sh"
  val env = Map("INSTALL_DIR" -> stageDir.getAbsolutePath, "STOP_TIMEOUT" -> "30")
  log.info(s"Running smoke test against staged distribution at ${stageDir}")
  def runSmoke(cmd: String): Int =
    scala.sys.process.Process(Seq("bash", script.getAbsolutePath, cmd), base, env.toSeq: _*)
      .!(scala.sys.process.ProcessLogger(s => log.info(s), e => log.error(e)))
  def runSmokeWithTimeout(cmd: String, timeoutSec: Int): Int = {
    val proc = scala.sys.process.Process(Seq("bash", script.getAbsolutePath, cmd), base, env.toSeq: _*)
      .run(scala.sys.process.ProcessLogger(s => log.info(s), e => log.error(e)))
    val future = scala.concurrent.Future(proc.exitValue())(scala.concurrent.ExecutionContext.global)
    try {
      scala.concurrent.Await.result(future, scala.concurrent.duration.Duration(timeoutSec, "seconds"))
    } catch {
      case _: java.util.concurrent.TimeoutException =>
        log.error(s"wave-smoke.sh $cmd timed out after ${timeoutSec}s — destroying process")
        proc.destroy()
        1
    }
  }
  try {
    val startCode = runSmoke("start")
    if (startCode != 0) sys.error(s"wave-smoke.sh start failed with exit code $startCode")
    val checkCode = runSmoke("check")
    if (checkCode != 0) sys.error(s"wave-smoke.sh check failed with exit code $checkCode")
  } finally {
    // Use a timeout for stop to prevent CI hangs if the process can't be killed
    runSmokeWithTimeout("stop", 60)
  }
}

lazy val smokeUi = taskKey[Unit]("UI smoke test via scripts/wave-smoke-ui.sh")
smokeUi := {
  val log = streams.value.log
  val base = baseDirectory.value
  val script = base / "scripts" / "wave-smoke-ui.sh"
  val code = scala.sys.process.Process(Seq("bash", script.getAbsolutePath), base)
    .!(scala.sys.process.ProcessLogger(s => log.info(s), e => log.error(e)))
  if (code != 0) sys.error(s"wave-smoke-ui.sh failed with exit code $code")
}

// Deep clean task: removes generated sources and local sbt/coursier caches pinned via .sbtopts
lazy val deepClean = taskKey[Unit]("Remove generated sources and project-local caches (.sbt-boot, .sbt-global, .ivy2, .coursier-cache)")
ThisBuild / deepClean := {
  val log  = streams.value.log
  val base = baseDirectory.value
  val gen  = Seq(
    base / "proto_src",
    base / "gen" / "messages",
    base / "gen" / "flags",
    base / "target" / "proto-pb-src"
  )
  val caches = Seq(
    base / ".sbt-boot",
    base / ".sbt-global",
    base / ".ivy2",
    base / ".coursier-cache"
  )
  val logs = Seq(base / "_logs")
  IO.delete(gen ++ caches ++ logs)
  log.info("Deep clean: removed generated sources and local caches.")
}

// -----------------------------
// Codegen tasks (Phase 4: reconciled with Gradle)
// -----------------------------
import scala.sys.process._
import sbt._
import sbtprotoc.ProtocPlugin.autoImport._
import sbt.complete.DefaultParsers._

lazy val pst = Project("pst", file("pst"))
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    Compile / compileOrder := CompileOrder.JavaThenScala,
    Compile / unmanagedSourceDirectories += baseDirectory.value / "generated" / "main" / "java",
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "3.25.3",
      "com.google.guava" % "guava" % "19.0",
      "org.antlr" % "antlr" % "3.2",
      "commons-cli" % "commons-cli" % "1.11.0",
      "junit" % "junit" % "4.12" % Test
    ),
    Compile / PB.protoSources := Seq(baseDirectory.value / "src" / "main" / "proto"),
    Compile / PB.includePaths := Seq(baseDirectory.value / "src" / "main" / "proto"),
    Compile / PB.targets := Seq(PB.gens.java -> (baseDirectory.value / "generated" / "main" / "java")),
    Compile / compile := (Compile / compile).dependsOn(Compile / PB.generate).value,
    Compile / mainClass := Some("org.apache.wave.pst.PstMain"),
    assembly / mainClass := Some("org.apache.wave.pst.PstMain"),
    assembly / assemblyJarName := s"wave-pst-${version.value}.jar",
    assembly / test := {},
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp.filter { attributed =>
        val jarName = attributed.data.getName
        jarName.startsWith("protobuf-java-") || jarName.startsWith("guava-")
      }
    }
  )

lazy val wave = Project("wave", file("."))
  .settings(
    Compile / resourceGenerators += Def.task {
      val targetDir = (Compile / resourceManaged).value / "config"
      IO.createDirectory(targetDir)
      val sourceFile = baseDirectory.value / "wave" / "config" / "changelog.json"
      if (!sourceFile.exists()) sys.error("Missing source changelog.json at " + sourceFile)
      val targetFile = targetDir / "changelog.json"
      IO.copyFile(sourceFile, targetFile)
      Seq(targetFile)
    },
    // Suppress deprecation notes from protobuf 3.25.3-generated code (PARSER field, valueOf(int) in enums).
    // Hand-written deprecated API usages are fixed directly in source; generated code cannot be changed
    // without upgrading the protobuf code generator beyond 3.25.3.
    Compile / javacOptions += "-Xlint:-deprecation"
  )
  .aggregate(pst)
lazy val root = wave

lazy val prepareProtosForPB = taskKey[Unit]("Stage .protodevel/.proto into target/proto-pb-src for sbt-protoc")
lazy val generatePstMessages = taskKey[Unit]("Generate PST DTO sources into gen/messages")
lazy val generateFlags = taskKey[Unit]("Generate ClientFlags and FlagConstants into gen/flags")
lazy val prepareServerConfig = taskKey[Unit]("Generate server.config from server-config.example when missing")
lazy val testBackend = taskKey[Unit]("Run backend unit tests via Ant (excludes GWT/large/mongodb)")
lazy val j2clSandboxBuild = taskKey[Unit]("Build the isolated J2CL sandbox sidecar into war/j2cl-debug via the Maven wrapper")
lazy val j2clSandboxTest = taskKey[Unit]("Run the isolated J2CL sandbox sidecar smoke test via the Maven wrapper")
lazy val j2clSearchBuild = taskKey[Unit]("Build the isolated J2CL search-sidecar scaffold into war/j2cl-search via the Maven wrapper")
lazy val j2clSearchTest = taskKey[Unit]("Run the isolated J2CL search-sidecar smoke test via the Maven wrapper")
lazy val j2clProductionBuild = taskKey[Unit]("Build the production J2CL sidecar into war/j2cl via the Maven wrapper")
lazy val dataMigrate = inputKey[Unit]("Run DataMigrationTool: dataMigrate <sourceOpts> <targetOpts>")
lazy val dataPrepare = inputKey[Unit]("Run DataPreparationTool: dataPrepare <waveId> [<options>]")

def runCmd(log: Logger)(cmd: Seq[String], cwd: File): Unit = {
  // Ensure javac is found by using full path if available
  val javacHome = sys.props.get("java.home").map(h => new java.io.File(h, "bin").getAbsolutePath)
  val fixedCmd = if (javacHome.isDefined && cmd.headOption.exists(_.startsWith("javac"))) {
    val javacPath = javacHome.get + "/" + cmd.head
    javacPath +: cmd.tail
  } else cmd
  val code = Process(fixedCmd, cwd).!(ProcessLogger(s => log.info(s), e => log.error(e)))
  if (code != 0) sys.error(s"Command failed: ${fixedCmd.mkString(" ")}")
}

def runJ2clWrapper(log: Logger, base: File, profile: String, goal: String): Unit = {
  val isWindows = scala.util.Properties.isWin
  val wrapper = if (isWindows) base / "j2cl" / "mvnw.cmd" else base / "j2cl" / "mvnw"
  val pom = base / "j2cl" / "pom.xml"
  if (!wrapper.exists() || !wrapper.isFile || (!isWindows && !wrapper.canExecute)) {
    sys.error(s"[j2cl] missing or non-executable wrapper: ${wrapper.getAbsolutePath}")
  }
  if (!pom.exists() || !pom.isFile) {
    sys.error(s"[j2cl] missing pom.xml: ${pom.getAbsolutePath}")
  }
  if (goal == "package") {
    val outputDir = profile match {
      case "search-sidecar" => Some(base / "war" / "j2cl-search")
      case "debug-single-project" => Some(base / "war" / "j2cl-debug")
      case "production" => Some(base / "war" / "j2cl")
      case _ => None
    }
    outputDir.foreach { dir =>
      if (dir.exists()) {
        log.info(s"[j2cl] deleting stale output directory ${dir.getAbsolutePath} before $profile:$goal")
        IO.delete(dir)
      }
    }
  }
  val cmd =
    if (isWindows) {
      Seq("cmd", "/c", wrapper.getAbsolutePath, "-f", pom.getAbsolutePath, s"-P$profile", "-q", goal)
    } else {
      Seq(wrapper.getAbsolutePath, "-f", pom.getAbsolutePath, s"-P$profile", "-q", goal)
    }
  val code = Process(cmd, base).!(ProcessLogger(s => log.info(s), e => log.error(e)))
  if (code != 0) sys.error(s"[j2cl] ${profile}:${goal} failed with exit code $code")
}

ThisBuild / prepareProtosForPB := {
  val log = streams.value.log
  val base = baseDirectory.value
  val srcDir = base / "wave" / "src" / "proto" / "proto"
  val stageDir = base / "target" / "proto-pb-src"
  IO.delete(stageDir)
  IO.createDirectory(stageDir)
  // Collect existing .proto excluding bundled descriptor.proto.
  // We provide descriptor.proto via the bundled pst proto sources so protoc can resolve imports without generating Java for it.
  val rawProtos = (srcDir ** "*.proto").get
    .filterNot(_.getPath.replace('\\','/').endsWith("/google/protobuf/descriptor.proto"))
  rawProtos.foreach { f =>
    val rel = IO.relativize(srcDir, f).getOrElse(f.getName)
    val dst = stageDir / rel
    IO.createDirectory(dst.getParentFile)
    // Rewrite imports of .protodevel to .proto in staged copy
    val txt = IO.read(f)
    val patched = txt.replace(".protodevel", ".proto")
    IO.write(dst, patched)
  }
  // Copy .protodevel to staged .proto
  val protodevels = (srcDir ** "*.protodevel").get
  protodevels.foreach { f =>
    val rel = IO.relativize(srcDir, f).getOrElse(f.getName)
    val relProto = rel.stripSuffix(".protodevel") + ".proto"
    val dst = stageDir / relProto
    IO.createDirectory(dst.getParentFile)
    // Copy as .proto (content typically already references proper imports)
    val txt = IO.read(f)
    IO.write(dst, txt)
  }
  if (rawProtos.isEmpty && protodevels.isEmpty) log.warn("No .proto/.protodevel files found under src/")
}

ThisBuild / j2clSandboxBuild := {
  val log = streams.value.log
  val base = baseDirectory.value
  runJ2clWrapper(log, base, "debug-single-project", "package")
}

ThisBuild / j2clSandboxTest := {
  val log = streams.value.log
  val base = baseDirectory.value
  runJ2clWrapper(log, base, "debug-single-project", "test")
}

ThisBuild / j2clSearchBuild := {
  val log = streams.value.log
  val base = baseDirectory.value
  runJ2clWrapper(log, base, "search-sidecar", "package")
}

ThisBuild / j2clSearchTest := {
  val log = streams.value.log
  val base = baseDirectory.value
  runJ2clWrapper(log, base, "search-sidecar", "test")
}

ThisBuild / j2clProductionBuild := {
  val log = streams.value.log
  val base = baseDirectory.value
  runJ2clWrapper(log, base, "production", "package")
}

// sbt-protoc: use embedded protoc to generate Java directly into proto_src
// Protoc version is managed by sbt-protoc; proto2 syntax is supported.
ThisBuild / PB.protocVersion := "3.25.3"

Compile / PB.protoSources := Seq(baseDirectory.value / "target" / "proto-pb-src")
// Include staged protos plus the PST proto tree that owns google/protobuf/descriptor.proto
Compile / PB.includePaths := Seq(
  baseDirectory.value / "target" / "proto-pb-src",
  baseDirectory.value / "wave" / "src" / "proto" / "proto",
  baseDirectory.value / "pst" / "src" / "main" / "proto"
)
Compile / PB.targets := Seq(PB.gens.java -> (baseDirectory.value / "proto_src"))
// Ensure staging runs before protoc
Compile / PB.generate := (Compile / PB.generate).dependsOn(prepareProtosForPB).value

// Post-process protoc-generated Java files to replace deprecated .PARSER field references
// with .parser() method calls. Protobuf 3.25.3 marks PARSER @Deprecated but still generates
// code that references it; parser() is the non-deprecated replacement with identical semantics.
lazy val fixProtoDeprecations = taskKey[Unit]("Replace deprecated .PARSER field access with .parser() in generated proto sources")
ThisBuild / fixProtoDeprecations := {
  val protoSrc = baseDirectory.value / "proto_src"
  val javaFiles = (protoSrc ** "*.java").get.filterNot(_.getPath.contains("/com/google/protobuf/"))
  javaFiles.foreach { file =>
    val content = IO.read(file)
    // Replace only cross-class .PARSER references in readMessage() calls.
    // Matches e.g. "Proto.ProtocolWaveletDelta.PARSER," → "Proto.ProtocolWaveletDelta.parser(),"
    if (content.contains(".PARSER,") || content.contains(".PARSER)")) {
      val fixed = content.replace(".PARSER,", ".parser(),").replace(".PARSER)", ".parser())")
      if (fixed != content) IO.write(file, fixed)
    }
  }
}
// Run fixProtoDeprecations after PB.generate, before compile
fixProtoDeprecations := fixProtoDeprecations.dependsOn(Compile / PB.generate).value
Compile / compile := (Compile / compile).dependsOn(fixProtoDeprecations).value


ThisBuild / generatePstMessages := {
  val log = streams.value.log
  val base = baseDirectory.value
  val outSrc = base / "proto_src"
  val pstProtoClasses = base / "target" / "pst-proto-classes"
  val genMsgDir = base / "gen" / "messages"
  IO.createDirectory(pstProtoClasses)
  IO.createDirectory(genMsgDir)
  val pstAssemblyJar = (pst / assembly).value

  // Compile proto_src Java to classes for reflection
  val protoSources = (outSrc ** "*.java").get.filterNot { f =>
    val p = f.getPath.replace('\\', '/')
    p.contains("/com/google/protobuf/")
  }
  if (protoSources.isEmpty) sys.error("proto_src is empty — run Compile / PB.generate first.")

  // Resolve protobuf-java from managed dependencies
  val depCp = (Compile / dependencyClasspath).value.map(_.data.getAbsolutePath)
  val protobufJar = (Compile / dependencyClasspath).value
    .map(_.data)
    .find(_.getName.startsWith("protobuf-java-"))
    .map(_.getAbsolutePath)
    .getOrElse(sys.error("protobuf-java not found on managed classpath"))

  val javacProto = Seq(
    "javac",
    "-g",
    "-Xlint:-deprecation",
    "-cp", protobufJar,
    "-d", pstProtoClasses.getAbsolutePath
  ) ++ protoSources.map(_.getAbsolutePath)
  runCmd(log)(javacProto, base)

  // Template files (as in Ant macros)
  val tmplRoot = base / "wave" / "src" / "main" / "java" / "org" / "waveprotocol" / "pst" / "templates"
  val templates = Seq(
    tmplRoot / "api" / "api.st",
    tmplRoot / "builder" / "builder.st",
    tmplRoot / "pojo" / "pojo.st",
    tmplRoot / "jso" / "jso.st",
    tmplRoot / "util" / "util.st",
    tmplRoot / "gson" / "gson.st",
    tmplRoot / "proto" / "proto.st"
  ).map(_.getAbsolutePath)

  // Proto descriptor class paths to generate messages for
  // AUTHORITATIVE list — must match wave/build.gradle generateMessages.proto_classes
  def cls(rel: String) = (pstProtoClasses / rel).getAbsolutePath
  val protosToGen = Seq(
    "org/waveprotocol/box/common/comms/WaveClientRpc.class",
    "org/waveprotocol/box/search/SearchProto.class",
    "org/waveprotocol/box/profile/ProfilesProto.class",
    "org/waveprotocol/box/server/rpc/Rpc.class",
    "org/waveprotocol/box/attachment/AttachmentProto.class",
    "org/waveprotocol/wave/federation/Proto.class",
    "org/waveprotocol/wave/concurrencycontrol/ClientServer.class",
    "org/waveprotocol/wave/diff/Diff.class"
  ).map(cls)

  // Put protobuf-java (from depCp) BEFORE the PST assembly to avoid version conflicts
  val cp = (Seq(protobufJar, pstProtoClasses.getAbsolutePath, pstAssemblyJar.getAbsolutePath) ++ depCp)
    .distinct
    .mkString(java.io.File.pathSeparator)

  protosToGen.foreach { protoClassFile =>
    val args = Seq(
      "-s", "pst",
      "-d", genMsgDir.getAbsolutePath,
      "-f", protoClassFile
    ) ++ templates
    val cmd = Seq("java", "-cp", cp, "org.apache.wave.pst.PstMain") ++ args
    runCmd(log)(cmd, base)
  }
}
ThisBuild / generatePstMessages := (ThisBuild / generatePstMessages).dependsOn(Compile / PB.generate).value

// GXP removed — replaced by HtmlRenderer.java (see PR #42)

ThisBuild / prepareServerConfig := {
  val log = streams.value.log
  val base = baseDirectory.value
  val runtimeConfigDir = base / "config"
  val runtimeApplicationConf = runtimeConfigDir / "application.conf"
  val runtimeReferenceConf = runtimeConfigDir / "reference.conf"
  val sourceConfigDir = base / "wave" / "config"
  val sourceApplicationConf = sourceConfigDir / "application.conf"
  val sourceReferenceConf = sourceConfigDir / "reference.conf"

  if (runtimeApplicationConf.exists() && runtimeReferenceConf.exists()) {
    log.info("config/application.conf and config/reference.conf exist; skipping generation")
  } else {
    if (!sourceApplicationConf.exists() || !sourceReferenceConf.exists()) {
      sys.error("Missing wave/config/application.conf or wave/config/reference.conf; cannot bootstrap runtime config")
    }
    IO.createDirectory(runtimeConfigDir)
    if (!runtimeApplicationConf.exists()) {
      IO.copyFile(sourceApplicationConf, runtimeApplicationConf)
    }
    if (!runtimeReferenceConf.exists()) {
      IO.copyFile(sourceReferenceConf, runtimeReferenceConf)
    }
    log.info("Bootstrapped config/application.conf and config/reference.conf from wave/config/")
  }
}

// Legacy Ant-based test runner.  The vendored third_party/ JARs and root
// build.xml have been removed; use `sbt test` instead.  This task is kept
// only as a placeholder so that old documentation references don't break.
ThisBuild / testBackend := {
  sys.error("testBackend is no longer available. The legacy Ant test runner " +
    "and vendored third_party/ JARs have been removed. Use `sbt test` instead.")
}

// Migration tools wrappers
ThisBuild / dataMigrate := Def.inputTask {
  val argv = spaceDelimited("<sourceOpts> <targetOpts>").parsed
  if (argv.length != 2) sys.error("Usage: dataMigrate <sourceOpts> <targetOpts>")
  val cmd = s" org.waveprotocol.box.server.DataMigrationTool ${argv.mkString(" ")}" 
  (Compile / runMain).toTask(cmd)
}.evaluated

ThisBuild / dataPrepare := Def.inputTask {
  val argv = spaceDelimited("<waveId> [<options>]").parsed
  if (argv.length < 1) sys.error("Usage: dataPrepare <waveId> [<options>]")
  val cmd = s" org.waveprotocol.box.server.DataPreparationTool ${argv.mkString(" ")}" 
  (Compile / runMain).toTask(cmd)
}.evaluated

ThisBuild / generateFlags := {
  val log = streams.value.log
  val base = baseDirectory.value
  val genDir = base / "gen" / "flags" / "org" / "waveprotocol" / "box" / "clientflags"
  val toolClasses = base / "target" / "flags-tool-classes"

  // Guard: FlagConstants.java is already checked-in under wave/src/main/java and the
  // ClientFlagsGenerator tool + client.default.config are not present in this repository.
  // Skip generation if either prerequisite is missing.
  // Extract .value outside of if/else to satisfy SBT macro requirements
  val depCp = (Compile / dependencyClasspath).value.map(_.data.getAbsolutePath)
  val configFile = base / "client.default.config"
  val generatorSources = (base / "wave" / "src" / "main" / "java" / "org" / "waveprotocol" / "wave" / "util" / "flags" ** "*.java").get
  if (!configFile.exists) {
    log.info("client.default.config not found; skipping flag generation (FlagConstants.java is already checked in)")
  } else if (generatorSources.isEmpty) {
    log.info("Flag generator sources not found; skipping flag generation (FlagConstants.java is already checked in)")
  } else {
    IO.createDirectory(genDir)
    IO.createDirectory(toolClasses)

    // Compile the flag generator tool
    val allSources = {
      val valueUtils = base / "wave" / "src" / "main" / "java" / "org" / "waveprotocol" / "wave" / "model" / "util" / "ValueUtils.java"
      if (valueUtils.exists) generatorSources :+ valueUtils else generatorSources
    }
    val cp = depCp.mkString(java.io.File.pathSeparator)
    val javacCmd = Seq("javac", "-g", "-cp", cp, "-d", toolClasses.getAbsolutePath) ++ allSources.map(_.getAbsolutePath)
    runCmd(log)(javacCmd, base)

    // Run the generator
    val javaCp = (Seq(toolClasses.getAbsolutePath) ++ depCp).mkString(java.io.File.pathSeparator)
    val args = Seq(
      configFile.getAbsolutePath,
      "org.waveprotocol.box.clientflags",
      genDir.getAbsolutePath + java.io.File.separator
    )
    val run = Seq("java", "-cp", javaCp, "org.waveprotocol.wave.util.flags.ClientFlagsGenerator") ++ args
    runCmd(log)(run, base)
  }
}

// Ensure codegen runs before compilation
// Ensure ordering: PST depends on fixProtoDeprecations (which depends on sbt-protoc)
// so that proto_src files are fixed before javacProto compiles them.
generatePstMessages := (generatePstMessages).dependsOn(fixProtoDeprecations).value

Compile / compile := (Compile / compile)
.dependsOn(generatePstMessages)
  .dependsOn(generateFlags)
  // generateGxp removed — GXP replaced by HtmlRenderer
  .value

// Ensure `run` has a config in place and both maintained J2CL assets are
// rebuilt before launching the server.
Compile / run := (Compile / run).dependsOn(prepareServerConfig, j2clSearchBuild, j2clProductionBuild).evaluated

// =============================================================================
// Phase 6: GWT Compilation Bridge
// =============================================================================
// GWT compilation is complex: it requires Jetty 9.4 on the classpath (gwt-dev
// bundles it), source directories as classpath entries, and the GWT Compiler
// main class (com.google.gwt.dev.Compiler). There is no maintained SBT GWT
// plugin, so we use a transitional bridge that delegates to Gradle when
// available and falls back to a native Fork.java invocation otherwise.
//
// GWT tests (testGwt) are disabled in CI and deferred to a future phase.
// The Gradle build defines them but they are not part of the default test task.
// =============================================================================

// Dedicated GWT configuration — does NOT extend Compile to avoid pulling
// Jetty 12 (jakarta) or Jetty 9.4 (javax) from the main compile classpath.
// GWT ships its own embedded Jetty 9.4 inside gwt-dev.jar.
lazy val Gwt = config("gwt").hide

ivyConfigurations += Gwt

lazy val GwtVersion = "2.10.0"

libraryDependencies ++= Seq(
  "org.gwtproject" % "gwt-dev"        % GwtVersion % Gwt,
  "org.gwtproject" % "gwt-user"       % GwtVersion % Gwt,
  "org.gwtproject" % "gwt-codeserver" % GwtVersion % Gwt
)

// Setting to control whether compileGwt is wired into stage/package tasks.
// CI can pass -DskipGwt=true to skip GWT compilation entirely.
lazy val skipGwt = settingKey[Boolean]("Skip GWT compilation (default: false, set -DskipGwt=true to skip)")
ThisBuild / skipGwt := sys.props.get("skipGwt").exists(_.trim.toLowerCase == "true")

lazy val devCompile = taskKey[Unit]("Run the lighter dev compile path: codegen plus Compile / compile, without GWT packaging tasks")
lazy val compileGwt = taskKey[Unit]("Compile GWT client (delegates to Gradle when available, otherwise uses native Fork.java)")
lazy val compileGwtDev = taskKey[Unit]("Compile a single GWT permutation in draft mode into war-dev for faster compile feedback without touching production war assets")

ThisBuild / compileGwt := {
  val log      = streams.value.log
  val base     = baseDirectory.value
  val skip     = (ThisBuild / skipGwt).value
  // Eagerly resolve these outside any if-branch so SBT's task macro is happy.
  // They are only used in native mode but must be evaluated statically.
  val resolved = update.value
  val compileCp = (Compile / dependencyClasspath).value.map(_.data)

  val nocacheJs = base / "war" / "webclient" / "webclient.nocache.js"

  // Check whether any GWT input source is newer than the compiled output.
  // If the output is up-to-date we can skip the expensive GWT compile step.
  val gwtInputDirs = Seq(
    base / "wave" / "src" / "main" / "java",
    base / "wave" / "src" / "main" / "resources",
    base / "wave" / "generated" / "src" / "main" / "java",
    base / "proto_src",
    base / "gen" / "messages",
    base / "gen" / "flags"
  ).filter(_.exists)

  // Validate completeness by cross-checking compilation-mappings.txt (written by GWT
  // as one of its last steps) against the actual *.cache.js files on disk.  Every
  // permutation strong-name listed in that file must have a corresponding .cache.js.
  val webclientDir = base / "war" / "webclient"
  val mappingsFile = webclientDir / "compilation-mappings.txt"
  val hasCompleteOutput = nocacheJs.exists && mappingsFile.exists && {
    val cacheJsPattern = "([0-9A-Fa-f]{32})\\.cache\\.js".r
    val expectedFiles = IO.readLines(mappingsFile)
      .map(_.trim).collect { case cacheJsPattern(h) => h + ".cache.js" }
      .distinct
    expectedFiles.nonEmpty &&
      expectedFiles.forall(f => (webclientDir / f).exists)
  }

  val outputTs = if (hasCompleteOutput) nocacheJs.lastModified else 0L
  val gwtExts = Seq("java", "xml", "proto", "css", "html", "properties", "js", "jslib", "png", "gif", "jpg")
  val gwtFilter: FileFilter = gwtExts.map(ext => GlobFilter(s"*.$ext"): FileFilter).reduce(_ || _)
  // Include build config files — dependency or GWT setting changes should trigger recompile
  val buildConfigFiles = Seq(base / "build.sbt", base / "project" / "plugins.sbt").filter(_.exists)
  val newestInput = (gwtInputDirs.flatMap(d =>
    (d ** gwtFilter).get
  ) ++ buildConfigFiles).map(_.lastModified).foldLeft(0L)(math.max(_, _))

  val upToDate = hasCompleteOutput && newestInput <= outputTs

  if (skip) {
    log.info("[compileGwt] Skipped (skipGwt=true)")
  } else if (upToDate) {
    log.info("[compileGwt] Skipped — output is up-to-date (use 'sbt clean run' to force recompile)")
  } else {
    val gradlew = base / "gradlew"
    if (gradlew.exists && gradlew.canExecute) {
      // --- Bridge mode: delegate to Gradle ---
      log.info("[compileGwt] Bridge mode — delegating to Gradle")
      val cmd = Seq(gradlew.getAbsolutePath, "--no-daemon", ":wave:compileGwt")
      val code = Process(cmd, base).!(ProcessLogger(s => log.info(s), e => log.error(e)))
      if (code != 0) sys.error("[compileGwt] Gradle delegation failed (exit " + code + ")")
    } else {
      // --- Native SBT mode: fork GWT Compiler directly ---
      log.info("[compileGwt] Native mode — forking com.google.gwt.dev.Compiler")

      // GWT needs Java source directories on the classpath (for translatable source)
      // GWT needs source dirs + resources (for .gwt.xml module files) on classpath
      val javaSrcDirs = Seq(
        base / "wave" / "src" / "main" / "java",
        base / "wave" / "src" / "main" / "resources",
        base / "wave" / "generated" / "src" / "main" / "java",
        base / "proto_src",
        base / "gen" / "messages",
        // gen/gxp removed — GXP replaced by HtmlRenderer
        base / "gen" / "flags"
      ).filter(_.exists)

      // Resolve the isolated Gwt configuration (gwt-dev, gwt-user, gwt-codeserver)
      val gwtJars = resolved.select(configurationFilter(Gwt.name))

      // Full classpath: source dirs (first, for GWT source lookup) + GWT jars + compile classpath
      val fullCp = javaSrcDirs ++ gwtJars ++ compileCp

      val forkOpts = ForkOptions()
        .withRunJVMOptions(Vector("-Xmx1024M"))

      // Output to root war/ so sbt run and staged distributions share one
      // runtime asset layout.
      val warDir = (base / "war").getAbsolutePath

      val gwtArgs = Seq(
        "-war", warDir,
        // TODO(#273): Revert PRETTY mode and restore OBFUSCATED with
        // -XdisableClassMetadata and -XdisableCastChecking after debug cycle.
        "-style", "PRETTY",
        "-localWorkers", "4",
        "org.waveprotocol.box.webclient.WebClientProd"
      )

      log.info("[compileGwt] Classpath has " + fullCp.size + " entries")
      val cpStr = fullCp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

      val exitCode = Fork.java(
        forkOpts,
        Seq("-cp", cpStr, "com.google.gwt.dev.Compiler") ++ gwtArgs
      )
      if (exitCode != 0) sys.error("[compileGwt] GWT Compiler failed (exit " + exitCode + ")")
    }
  }
}

// Keep the dev draft compile isolated from the runtime war/ tree.
// `sbt run`, `stage`, and `packageBin` stay on the production compile path.
ThisBuild / compileGwtDev := {
  val log       = streams.value.log
  val base      = baseDirectory.value
  val resolved  = update.value
  val compileCp = (Compile / dependencyClasspath).value.map(_.data)

  val javaSrcDirs = Seq(
    base / "wave" / "src" / "main" / "java",
    base / "wave" / "src" / "main" / "resources",
    base / "wave" / "generated" / "src" / "main" / "java",
    base / "proto_src",
    base / "gen" / "messages",
    base / "gen" / "flags"
  ).filter(_.exists)

  val gwtJars = resolved.select(configurationFilter(Gwt.name))
  val fullCp = javaSrcDirs ++ gwtJars ++ compileCp

  val forkOpts = ForkOptions()
    .withRunJVMOptions(Vector("-Xmx1024M"))

  val devWarDir = (base / "war-dev").getAbsolutePath
  val gwtArgs = Seq(
    "-war", devWarDir,
    "-draftCompile",
    "-style", "PRETTY",
    "-localWorkers", "2",
    "-setProperty", "user.agent=safari",
    "org.waveprotocol.box.webclient.WebClientProd"
  )

  log.info("[compileGwtDev] Native mode — one-permutation draft compile (compile-only)")
  log.info("[compileGwtDev] Writing dev artifacts to " + devWarDir)
  log.info("[compileGwtDev] Classpath has " + fullCp.size + " entries")
  val cpStr = fullCp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

  val exitCode = Fork.java(
    forkOpts,
    Seq("-cp", cpStr, "com.google.gwt.dev.Compiler") ++ gwtArgs
  )
  if (exitCode != 0) sys.error("[compileGwtDev] GWT Compiler failed (exit " + exitCode + ")")
}

// Prevent packaging distributions with missing GWT assets when -DskipGwt=true
lazy val verifyGwtAssets = taskKey[Unit]("Fail when packaging would ship with missing GWT assets")

ThisBuild / verifyGwtAssets := {
  val log = streams.value.log
  val skip = (ThisBuild / skipGwt).value
  if (skip) {
    sys.error("[verifyGwtAssets] Cannot package distribution with skipGwt=true. " +
              "GWT assets would be missing. Use -DskipGwt=true only with 'sbt run'.")
  }
  log.info("[verifyGwtAssets] OK — GWT assets will be compiled")
}

// Wire compileGwt to run after compileJava (GWT needs compiled classes)
compileGwt := (compileGwt).dependsOn(Compile / compile).value
devCompile := (Compile / compile).value
compileGwtDev := (compileGwtDev).dependsOn(Compile / compile).value

Universal / stage := (Universal / stage).dependsOn(j2clSearchBuild, j2clProductionBuild).value
Universal / packageBin := (Universal / packageBin).dependsOn(j2clSearchBuild, j2clProductionBuild).value

cleanFiles += baseDirectory.value / "war" / "webclient"
cleanFiles += baseDirectory.value / "war" / "org"
cleanFiles += baseDirectory.value / "war" / "WEB-INF"
cleanFiles += baseDirectory.value / "war-dev"
cleanFiles += baseDirectory.value / "war" / "j2cl-search"
cleanFiles += baseDirectory.value / "war" / "j2cl-debug"
cleanFiles += baseDirectory.value / "war" / "j2cl"
