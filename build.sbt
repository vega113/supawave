// incubator-wave SBT build (Phase 2: managed dependencies, ported from Gradle)

ThisBuild / organization := "org.apache.wave"
name := "incubator-wave"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// Compile Java first, Scala later (we'll add Scala modules in future phases)
Compile / compileOrder := CompileOrder.JavaThenScala

// Target bytecode for Jakarta runtime (JDK 17 for records, pattern matching)
javacOptions ++= Seq("--release", "17")

// Include Maven standard source layout and generated sources
Compile / unmanagedSourceDirectories ++= Seq(
  baseDirectory.value / "wave" / "src" / "main" / "java",
  baseDirectory.value / "proto_src",
  baseDirectory.value / "gen" / "gxp",
  baseDirectory.value / "gen" / "messages",
  baseDirectory.value / "gen" / "flags",
  baseDirectory.value / "gen" / "shims"
)

Compile / unmanagedSourceDirectories += baseDirectory.value / "wave" / "src" / "jakarta-overrides" / "java"

// Exclude GWT client and legacy Socket.IO server shims from compilation
Compile / unmanagedSources := (Compile / unmanagedSources).value.filterNot { f =>
  val p = f.getPath.replace('\\', '/')
  val isSrc = p.contains("/wave/src/main/java/")
  // Exclude client trees only when under src/, not shims
  (isSrc && p.contains("/org/waveprotocol/box/webclient/")) ||
  (isSrc && p.contains("/org/waveprotocol/wave/client/")) ||
  (isSrc && p.contains("/org/waveprotocol/wave/communication/gwt/")) ||
  (isSrc && p.contains("/com/google/gwt/")) ||
  (isSrc && p.contains("/org/waveprotocol/box/expimp/")) ||
  // Exclude stat shims since we have the real source files
  (p.contains("/gen/shims/") && p.contains("/org/waveprotocol/box/stat/")) ||
  // GXP-dependent server RPC pages
  // WaveClientServlet enabled when GXP generation works
  (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java")) ||
  (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/ChangePasswordServlet.java")) ||
  // AuthenticationServlet enabled for login flow
  (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/GoogleAuthenticationServlet.java")) ||
  (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/GoogleAuthenticationCallbackServlet.java")) ||
  // Exclude Socket.IO server shims
  p.endsWith("/org/waveprotocol/box/server/rpc/AbstractWaveSocketIOServlet.java") ||
  p.endsWith("/org/waveprotocol/box/server/rpc/SocketIOServerChannel.java") ||
  p.endsWith("/org/waveprotocol/box/server/rpc/WebSocketClientRpcChannel.java") ||
  p.endsWith("/org/waveprotocol/box/webclient/client/WaveSocketFactory.java") ||
  // Specific render view classes that couple to client (keep core render enabled).
  // Exclude only if coming from src/, allow shims under gen/shims.
  (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/render/view/builder/TagsViewBuilder.java")) ||
  (isSrc && p.endsWith("/org/waveprotocol/box/server/rpc/render/view/ModelAsViewProvider.java")) ||
  // Legacy helpers with client coupling that are unnecessary for server run
  p.endsWith("/org/waveprotocol/wave/concurrencycontrol/channel/ViewChannelImpl.java") ||
  p.endsWith("/org/waveprotocol/wave/concurrencycontrol/channel/ClientStatsRawFragmentsApplier.java") ||
  p.endsWith("/org/waveprotocol/wave/migration/helpers/FixLinkAnnotationsFilter.java") ||
  // Exclude servlets that depend on GXP-generated classes when GXP generation is disabled
  p.endsWith("/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java") ||
  p.endsWith("/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java") ||
  // Exclude generated GWT JSO implementations under gen/messages
  p.contains("/gen/messages/") && p.contains("/jso/") ||
  // Exclude GWT-only JSON serializers that reference JSO implementations
  (isSrc && p.endsWith("/org/waveprotocol/wave/model/raw/serialization/JsoSerializerAdaptor.java")) ||
  (isSrc && p.endsWith("/org/waveprotocol/wave/model/raw/serialization/JsoSerializer.java"))
}

// Exclude javax-based sources, legacy ServerMain, and src/main/java files that have
// a jakarta-overrides counterpart (to prevent duplicate class errors).
Compile / unmanagedSources := {
  val files = (Compile / unmanagedSources).value
  val base = baseDirectory.value

  // Build set of relative paths that exist in jakarta-overrides, so we can exclude
  // the corresponding src/main/java file to avoid duplicate class errors.
  val jakartaOverridesRoot = base / "wave" / "src" / "jakarta-overrides" / "java"
  val jakartaOverrideRelPaths: Set[String] = if (jakartaOverridesRoot.exists) {
    (jakartaOverridesRoot ** "*.java").get.flatMap { f =>
      IO.relativize(jakartaOverridesRoot, f)
    }.toSet
  } else Set.empty
  val srcMainRoot = base / "wave" / "src" / "main" / "java"

  files.filterNot { f =>
    val p = f.getPath.replace('\\','/')
    val isSrc = p.contains("/wave/src/main/java/")
    val excludeByName = isSrc && p.endsWith("/org/waveprotocol/box/server/ServerMain.java")
    // Exclude src/main/java files that have a jakarta-overrides replacement
    val excludeByOverride = isSrc && {
      IO.relativize(srcMainRoot, f).exists(jakartaOverrideRelPaths.contains)
    }
    val excludeByImport = isSrc && !excludeByOverride && {
      val lines = try IO.readLines(f) catch { case _: Throwable => Nil }
      lines.exists { l =>
        val s = l.trim
        s.startsWith("import javax.servlet") || s.startsWith("import javax.websocket")
      }
    }
    val excludeRobotsAgents = p.contains("/src/org/waveprotocol/box/server/robots/agent/")
    val excludeRobotsExamples = p.contains("/src/org/waveprotocol/examples/robots/")
    val excludeAttachments = false
    val excludeRender = false
    val excludeSearchProfile = p.endsWith("/src/org/waveprotocol/box/server/rpc/FetchProfilesServlet.java") ||
                               p.endsWith("/src/org/waveprotocol/box/server/rpc/AbstractSearchServlet.java") ||
                               p.endsWith("/src/org/waveprotocol/box/server/rpc/InitSeensWavelet.java")
    val excludeMongo = false
    val excludeHtmlModule = p.endsWith("/src/org/waveprotocol/box/server/HtmlModule.java")
    val excludeRegistrationUtil = p.endsWith("/src/org/waveprotocol/box/server/util/RegistrationUtil.java")
    val excludeWaveDigester = false
    val excludeSearch = false
    val excludeLucenePersistence = false
    val excludeMigration = false
    val excludeDataTools = false
    val excludeRobotHttpConn = false
    val excludeDefaultServerModule = p.endsWith("/src/org/waveprotocol/box/server/ServerModule.java")
    val excludeDefaultMappings = p.endsWith("/src/org/waveprotocol/box/server/http/JettyServletMappingsConfigurer.java")
    val excludePersistenceModule = p.endsWith("/src/org/waveprotocol/box/server/persistence/PersistenceModule.java")
    val excludeGenShims = p.contains("/gen/shims/") && !(
      p.endsWith("/gen/shims/org/waveprotocol/box/webclient/search/WaveContext.java") ||
      p.endsWith("/gen/shims/org/waveprotocol/box/server/rpc/render/view/builder/TagsViewBuilder.java")
    )
    val baseExcludes = excludeByName || excludeByOverride || excludeByImport || excludeAttachments || excludeRender || excludeSearchProfile || excludeMongo || excludeHtmlModule || excludeRegistrationUtil || excludeDefaultServerModule || excludeDefaultMappings || excludePersistenceModule || excludeMigration || excludeDataTools || excludeGenShims
    val robotsExcludes = excludeRobotsAgents || excludeRobotsExamples
    baseExcludes || robotsExcludes
  }
}

Test / unmanagedSourceDirectories += baseDirectory.value / "wave" / "src" / "test" / "java"

// Phase 2: all dependencies are now managed via libraryDependencies; third_party/ is no longer on the classpath.
// Codegen tasks resolve JARs from managed deps via (Compile / dependencyClasspath).

// Serve static assets from wave/war/ via classpath resources (Jetty will still serve filesystem if desired)
Compile / resourceDirectories += baseDirectory.value / "wave" / "war"

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

libraryDependencies ++= Seq(
  // --- Test ---
  "junit"                          % "junit"                      % "4.12"     % Test,
  "com.novocode"                   % "junit-interface"            % "0.11"     % Test,
  "org.hamcrest"                   % "hamcrest-junit"             % "2.0.0.0"  % Test,
  "org.mockito"                    % "mockito-core"               % "2.2.21"   % Test,

  // --- Protobuf ---
  "com.google.protobuf"            % "protobuf-java"              % ProtobufV,

  // --- Guava & Guice ---
  "com.google.guava"               % "guava"                      % GuavaV,
  // guava-gwt excluded: only needed for GWT client compilation, causes errors with Guava 32.x
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

  // --- HTTP ---
  "org.apache.httpcomponents"      % "httpclient"                 % "4.5.14",
  "org.apache.httpcomponents"      % "httpcore"                   % "4.4.16",

  // --- Logging (SLF4J 2.x + Logback) ---
  "org.slf4j"                      % "slf4j-api"                  % Slf4jV,
  "ch.qos.logback"                 % "logback-classic"            % LogbackV,
  "org.slf4j"                      % "jcl-over-slf4j"             % Slf4jV,
  "org.slf4j"                      % "jul-to-slf4j"               % Slf4jV,
  "org.slf4j"                      % "log4j-over-slf4j"           % Slf4jV,

  // --- Annotations ---
  "com.google.code.findbugs"       % "jsr305"                     % "2.0.1",
  "javax.inject"                   % "javax.inject"               % "1",

  // --- XML / DOM ---
  "dom4j"                          % "dom4j"                      % "1.6.1",
  "org.jdom"                       % "jdom"                       % "1.1.3",

  // --- Classpath scanning ---
  "eu.infomas"                     % "annotation-detector"        % "3.0.0",

  // --- Parser / template ---
  "org.antlr"                      % "antlr"                      % "3.2",
  "org.apache.velocity"            % "velocity"                   % "1.7",

  // --- Search ---
  "org.apache.lucene"              % "lucene-core"                % "3.5.0",

  // --- OAuth (net.oauth.core) ---
  "net.oauth.core"                 % "oauth"                      % "20090825",
  "net.oauth.core"                 % "oauth-consumer"             % "20090823",
  "net.oauth.core"                 % "oauth-provider"             % "20090531",

  // --- Crypto ---
  "org.bouncycastle"               % "bcprov-jdk16"               % "1.45",

  // --- Persistence ---
  "javax.jdo"                      % "jdo2-api"                   % "2.1",
  "org.mongodb"                    % "mongo-java-driver"          % "2.11.2" % Provided,  // compile-only; excluded from runtime (Gradle runtimeClasspath.exclude)
  "org.mongodb"                    % "mongodb-driver-sync"        % MongoV4,

  // --- Cache ---
  "com.github.ben-manes.caffeine"  % "caffeine"                   % "3.1.8",

  // --- GXP compiler (used at codegen time and needed on compile classpath for generated sources) ---
  "com.google.gxp"                 % "google-gxp"                 % "0.2.4-beta",

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

// Exclude legacy mongo-java-driver from runtime (compile-only for migration code).
// We declare it as Compile-only (not Runtime) in libraryDependencies instead.
// The Gradle build uses configurations.runtimeClasspath.exclude for this.

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
  "org.mongodb"        % "bson"               % MongoV4,
  "org.mongodb"        % "mongodb-driver-core" % MongoV4,
  "org.mongodb"        % "mongodb-driver-sync" % MongoV4
)

// Test JVM flags (Guice/cglib on JDK 17, enable assertions)
Test / javaOptions ++= Seq(
  "-ea",
  "--add-opens", "java.base/java.lang=ALL-UNNAMED",
  "--add-opens", "java.base/java.io=ALL-UNNAMED"
)

// Keep tests deterministic and similar to Ant: exclude GWT, large, and MongoDB tests
Test / testOptions += Tests.Filter { name =>
  val isJUnit = name.endsWith("Test")
  val isGwt = name.endsWith("GwtTest")
  val isLarge = name.endsWith("LargeTest")
  val isMongo = name.contains(".mongodb.")
  val isFederation = name.contains(".wave.federation.")
  val isPersistence = name.contains(".server.persistence.")
  val isAllowedPersistence = name.contains(".server.persistence.memory.") || name.contains(".server.persistence.file.")
  isJUnit && !isGwt && !isLarge && !isMongo && !isFederation && (!isPersistence || isAllowedPersistence)
}

// Make JUnit output verbose for debugging (stack traces, test names)
Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

// Make test resources available (e.g., *.schema stored under wave/src/test/)
Test / unmanagedResourceDirectories += baseDirectory.value / "wave" / "src" / "test" / "resources"

// Exclude client/GWT-dependent tests from compilation (server-only phase)
Test / unmanagedSources := (Test / unmanagedSources).value.filterNot { f =>
  val p = f.getPath.replace('\\', '/')
  p.contains("/org/waveprotocol/wave/client/") ||
  p.contains("/org/waveprotocol/box/webclient/") ||
  p.contains("/org/waveprotocol/box/server/rpc/render/") ||
  p.contains("/wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/") ||
  p.contains("/wave/src/test/java/org/waveprotocol/wave/migration/") ||
  p.contains("/wave/src/test/java/org/waveprotocol/wave/model/document/util/")
}

// Ensure `sbt clean` removes generated sources only (dependencies/caches are preserved)
cleanFiles ++= Seq(
  baseDirectory.value / "proto_src",
  baseDirectory.value / "gen" / "gxp",
  baseDirectory.value / "gen" / "messages",
  baseDirectory.value / "gen" / "flags"
)

// Deep clean task: removes generated sources and local sbt/coursier caches pinned via .sbtopts
lazy val deepClean = taskKey[Unit]("Remove generated sources and project-local caches (.sbt-boot, .sbt-global, .ivy2, .coursier-cache)")
ThisBuild / deepClean := {
  val log  = streams.value.log
  val base = baseDirectory.value
  val gen  = Seq(
    base / "proto_src",
    base / "gen" / "gxp",
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
// Codegen tasks (Phase 2, minimal)
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

lazy val wave = Project("wave", file(".")).aggregate(pst)
lazy val root = wave

lazy val prepareProtosForPB = taskKey[Unit]("Stage .protodevel/.proto into target/proto-pb-src for sbt-protoc")
lazy val generatePstMessages = taskKey[Unit]("Generate PST DTO sources into gen/messages")
lazy val generateGxp = taskKey[Unit]("Generate GXP sources into gen/gxp with gxpc")
lazy val generateFlags = taskKey[Unit]("Generate ClientFlags and FlagConstants into gen/flags")
lazy val prepareServerConfig = taskKey[Unit]("Generate server.config from server-config.example when missing")
lazy val testBackend = taskKey[Unit]("Run backend unit tests via Ant (excludes GWT/large/mongodb)")
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
  def cls(rel: String) = (pstProtoClasses / rel).getAbsolutePath
  val protosToGen = Seq(
    "org/waveprotocol/box/search/SearchProto.class",
    "org/waveprotocol/box/searches/SearchesProto.class",
    "org/waveprotocol/box/contact/ContactsProto.class",
    "org/waveprotocol/box/profile/ProfilesProto.class",
    "org/waveprotocol/box/server/persistence/protos/ProtoSnapshotStoreData.class",
    "org/waveprotocol/box/attachment/AttachmentProto.class",
    "org/waveprotocol/wave/clientserver/ClientServer.class",
    "org/waveprotocol/wave/clientserver/Rpc.class",
    "org/waveprotocol/wave/federation/Proto.class",
    "org/waveprotocol/wave/model/raw/Raw.class"
  ).map(cls)

  val cp = (Seq(pstAssemblyJar.getAbsolutePath, pstProtoClasses.getAbsolutePath) ++ depCp)
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

ThisBuild / generateGxp := {
  val log = streams.value.log
  val base = baseDirectory.value
  val srcDir = base / "wave" / "src" / "main" / "java"
  val genDir = base / "gen" / "gxp"
  IO.createDirectory(genDir)
  // If outputs already exist, skip (developers can delete to force regen)
  val gxpSrcs = (srcDir / "org" / "waveprotocol" / "box" / "server" / "gxp" * "*.gxp").get
  val outPkgDir = genDir / "org" / "waveprotocol" / "box" / "server" / "gxp"
  val haveOutputs = outPkgDir.exists && (outPkgDir ** "*.java").get.nonEmpty
  if (gxpSrcs.isEmpty) {
    log.info("No GXP sources found; skipping generation")
  } else if (haveOutputs) {
    log.info("GXP outputs already present; skipping generation")
  } else {
    // Resolve google-gxp from managed dependencies
    val gxpcJar = (Compile / dependencyClasspath).value
      .map(_.data)
      .find(f => f.getName.startsWith("google-gxp-"))
      .getOrElse(sys.error("google-gxp not found on managed classpath"))
    val cp = gxpcJar.getAbsolutePath
    // Invoke gxpc CLI: set source base, output dir, and language; pass all .gxp files as inputs
    val args = Seq(
      "com.google.gxp.compiler.cli.Gxpc",
      "--source", srcDir.getAbsolutePath,
      "--dir", genDir.getAbsolutePath,
      "--output_language", "java"
    ) ++ gxpSrcs.map(_.getAbsolutePath)
    val cmd = Seq("java", "-cp", cp) ++ args
    log.info(cmd.mkString(" "))
    val code = Process(cmd, base).!(ProcessLogger(s => log.info(s), e => log.error(e)))
    if (code != 0) log.warn("GXP generation failed; continuing with existing sources (if any)")
  }
}

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

ThisBuild / testBackend := {
  val log = streams.value.log
  val base = baseDirectory.value
  // Ensure junit jar exists in vendored test libs to run Ant-based tests offline
  val junitDir = base / "third_party" / "test" / "junit"
  val junitJars = (junitDir * "*.jar").get
  if (junitJars.isEmpty) {
    sys.error(s"JUnit jar not found under ${junitDir}. Place e.g. junit-4.13.2.jar there (and hamcrest is already vendored) or enable managed Test deps.")
  }
  val antCheck = Process(Seq("bash","-lc","command -v ant >/dev/null 2>&1; echo $?"), base).!!.trim
  if (antCheck != "0") sys.error("Ant not found; please install Ant to run tests via Ant.")
  val code = Process(Seq("ant", "-q", "test"), base).!(ProcessLogger(s => log.info(s), e => log.error(e)))
  if (code != 0) sys.error("Ant test run failed")
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
  IO.createDirectory(genDir)
  IO.createDirectory(toolClasses)

  // Compile the flag generator tool
  val generatorSources = {
    val flagsSrcs = (base / "wave" / "src" / "main" / "java" / "org" / "waveprotocol" / "wave" / "util" / "flags" ** "*.java").get
    val valueUtils = base / "wave" / "src" / "main" / "java" / "org" / "waveprotocol" / "wave" / "model" / "util" / "ValueUtils.java"
    if (valueUtils.exists) flagsSrcs :+ valueUtils else flagsSrcs
  }
  // Use managed dependency classpath instead of third_party jars
  val depCp = (Compile / dependencyClasspath).value.map(_.data.getAbsolutePath)
  val cp = depCp.mkString(java.io.File.pathSeparator)
  val javacCmd = Seq("javac", "-g", "-cp", cp, "-d", toolClasses.getAbsolutePath) ++ generatorSources.map(_.getAbsolutePath)
  runCmd(log)(javacCmd, base)

  // Run the generator
  val javaCp = (Seq(toolClasses.getAbsolutePath) ++ depCp).mkString(java.io.File.pathSeparator)
  val args = Seq(
    (base / "client.default.config").getAbsolutePath,
    "org.waveprotocol.box.clientflags",
    genDir.getAbsolutePath + java.io.File.separator
  )
  val run = Seq("java", "-cp", javaCp, "org.waveprotocol.wave.util.flags.ClientFlagsGenerator") ++ args
  runCmd(log)(run, base)
}

// Ensure codegen runs before compilation
// Ensure ordering: PST depends on sbt-protoc (which itself ensures staging first)
generatePstMessages := (generatePstMessages).dependsOn(Compile / PB.generate).value

Compile / compile := (Compile / compile)
  // .dependsOn(generatePstMessages) // disabled for now - requires JDK; can be run manually with `sbt generatePstMessages`
  // .dependsOn(generateFlags) // disabled for now - requires JDK; can be run manually with `sbt generateFlags`
  // .dependsOn(generateGxp) // disabled for now - requires gxpc jar; can be run manually with `sbt generateGxp`
  .value

// Ensure `run` has a config in place
Compile / run := (Compile / run).dependsOn(prepareServerConfig).evaluated
