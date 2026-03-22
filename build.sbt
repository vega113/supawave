// incubator-wave SBT build (Phase 1: minimal Java-only skeleton, ported from Wiab.pro)

ThisBuild / organization := "org.apache.wave"
name := "incubator-wave"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// Compile Java first, Scala later (we'll add Scala modules in future phases)
Compile / compileOrder := CompileOrder.JavaThenScala

// Target bytecode by mode:
// - Default (javax): use --release 8 to accommodate legacy sources
// - Jakarta mode: use --release 11 because jakarta.servlet-api 5+ requires Java 11 bytecode
javacOptions ++= {
  if ((ThisBuild / jakartaMode).value) Seq("--release", "11") else Seq("--release", "8")
}

// Include Maven standard source layout and generated sources
Compile / unmanagedSourceDirectories ++= Seq(
  baseDirectory.value / "wave" / "src" / "main" / "java",
  baseDirectory.value / "proto_src",
  baseDirectory.value / "gen" / "messages",
  baseDirectory.value / "gen" / "flags",
  baseDirectory.value / "gen" / "shims"
)

// Include jakarta-specific sources when enabled
Compile / unmanagedSourceDirectories ++= {
  if ((ThisBuild / jakartaMode).value) Seq(baseDirectory.value / "wave" / "src" / "jakarta-overrides" / "java") else Seq()
}

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

// In jakarta mode, exclude any javax-servlet/websocket based sources and legacy ServerMain
Compile / unmanagedSources := {
  val files = (Compile / unmanagedSources).value
  if ((ThisBuild / jakartaMode).value) {
    files.filterNot { f =>
      val p = f.getPath.replace('\\','/')
      val isSrc = p.contains("/wave/src/main/java/")
      val excludeByName = isSrc && p.endsWith("/org/waveprotocol/box/server/ServerMain.java")
      val excludeByImport = isSrc && {
        // Read a few lines to detect javax servlet/websocket usage
        val lines = try IO.readLines(f) catch { case _: Throwable => Nil }
        lines.exists { l =>
          val s = l.trim
          s.startsWith("import javax.servlet") || s.startsWith("import javax.websocket")
        }
      }
      // Allow robots core (registries, utils, modules) in Jakarta mode.
      // Exclude robot agents and examples to avoid pulling optional deps.
      val excludeRobotsAgents = p.contains("/src/org/waveprotocol/box/server/robots/agent/")
      val excludeRobotsExamples = p.contains("/src/org/waveprotocol/examples/robots/")
      // Include attachment service/utilities; Jakarta servlet variant is stubbed for constants
      val excludeAttachments = false
      // Include render package; individual javax servlets will be dropped by import detection
      val excludeRender = false
      val excludeSearchProfile = p.endsWith("/src/org/waveprotocol/box/server/rpc/FetchProfilesServlet.java") ||
                                 p.endsWith("/src/org/waveprotocol/box/server/rpc/AbstractSearchServlet.java") ||
                                 p.endsWith("/src/org/waveprotocol/box/server/rpc/InitSeensWavelet.java")
      // Include MongoDB persistence sources in Jakarta mode
      val excludeMongo = false
      val excludeHtmlModule = p.endsWith("/src/org/waveprotocol/box/server/HtmlModule.java")
      val excludeRegistrationUtil = p.endsWith("/src/org/waveprotocol/box/server/util/RegistrationUtil.java")
      // Include the real WaveDigester (works without javax deps)
      val excludeWaveDigester = false
      // Allow search core and Lucene persistence in Jakarta mode (we provide jakarta servlets separately)
      val excludeSearch = false
      val excludeLucenePersistence = false
      // Include migration utilities
      val excludeMigration = false
      val excludeDataTools = false
      // Needed by RobotApiModule provider
      val excludeRobotHttpConn = false
      val excludeDefaultServerModule = p.endsWith("/src/org/waveprotocol/box/server/ServerModule.java")
      val excludeDefaultMappings = p.endsWith("/src/org/waveprotocol/box/server/http/JettyServletMappingsConfigurer.java")
      val excludePersistenceModule = p.endsWith("/src/org/waveprotocol/box/server/persistence/PersistenceModule.java")
      val excludeGenShims = p.contains("/gen/shims/") && !(
        p.endsWith("/gen/shims/org/waveprotocol/box/webclient/search/WaveContext.java") ||
        p.endsWith("/gen/shims/org/waveprotocol/box/server/rpc/render/view/builder/TagsViewBuilder.java")
      )
      val baseExcludes = excludeByName || excludeByImport || excludeAttachments || excludeRender || excludeSearchProfile || excludeMongo || excludeHtmlModule || excludeRegistrationUtil || excludeDefaultServerModule || excludeDefaultMappings || excludePersistenceModule || excludeMigration || excludeDataTools || excludeGenShims
      val robotsExcludes = excludeRobotsAgents || excludeRobotsExamples
      baseExcludes || robotsExcludes
    }
  } else files
}

Test / unmanagedSourceDirectories += baseDirectory.value / "wave" / "src" / "test" / "java"

// Bring in vendored jars from third_party (filtered). Assign rather than append to avoid duplicates/precedence issues.
def filteredThirdParty(cp: Seq[Attributed[File]]) = cp.filterNot { a =>
  val n = a.data.getName
  val p = a.data.getPath.replace('\\','/')
  // Exclude jars with broken manifest, legacy protobuf runtime, and Jetty/socketio vendored jars (we use managed Jetty).
  p.contains("/third_party/test/") ||
  n.startsWith("guava-treerangemap") ||
  n.startsWith("guava-gwt-") ||
  n.startsWith("guava-") ||
  n.startsWith("guice-") ||
  n == "protobuf-java-2.5.0.jar" ||
  p.contains("/third_party/runtime/socketio/") ||
  p.contains("/third_party/runtime/jetty/") ||
  n.startsWith("gwt-dev") ||
  n.startsWith("websocket-") ||
  n == "servlet-api-3.1.jar"
}

Compile / unmanagedJars := {
  val all = (baseDirectory.value / "third_party" ** "*.jar").classpath
  filteredThirdParty(all)
}

Test / unmanagedJars := {
  val all = (baseDirectory.value / "third_party" ** "*.jar").classpath
  val filtered = filteredThirdParty(all)
  val testOnly = (baseDirectory.value / "third_party" / "test" ** "*.jar").classpath
  filtered ++ testOnly
}

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

// Assembly settings (Phase 1 placeholder): enable when migrating off unmanaged jars
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

// Assembly: build a runnable fat JAR for ServerMain including unmanaged jars
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

// Managed dependencies
lazy val JettyV         = "9.4.54.v20240208"          // javax servlet stack (default)
lazy val JettyJakartaV  = "12.0.23"                    // jakarta servlet stack (EE10, matches Gradle)
lazy val JakartaServletV = "5.0.0"                     // jakarta.servlet-api
lazy val JakartaWsV      = "2.0.0"                     // jakarta.websocket-api

// Build-time flag (scaffold): enable Jakarta dependencies when true
lazy val jakartaMode = settingKey[Boolean]("Enable Jakarta servlet/websocket dependencies (scaffold; default false)")
ThisBuild / jakartaMode := sys.props.get("jakarta").exists(_.trim.toLowerCase == "true")

// Dependencies independent of container choice
lazy val coreDeps = Seq(
  // Test stack: run JUnit tests via sbt
  "junit" % "junit" % "4.13.2" % Test,
  "com.novocode" % "junit-interface" % "0.11" % Test,
  // Protobuf runtime for generated sources
  "com.google.protobuf" % "protobuf-java" % "3.25.3",
  // Utilities
  "commons-io" % "commons-io" % "2.15.1",
  // Guava (required by Guice 5)
  "com.google.guava" % "guava" % "32.1.3-jre",
  // Guice (upgrade for Java 11+ compatibility)
  "com.google.inject" % "guice" % "5.1.0",
  "com.google.inject.extensions" % "guice-servlet" % "5.1.0",
  "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0"
)

// Container-specific dependencies (javax vs jakarta)
lazy val containerDeps = Def.setting {
  if (jakartaMode.value) Seq(
    // Jetty Jakarta stack (scaffold; not enabled by default)
    "org.eclipse.jetty" % "jetty-server"  % JettyJakartaV,
    "org.eclipse.jetty" % "jetty-servlet" % JettyJakartaV,
    "org.eclipse.jetty" % "jetty-servlets" % JettyJakartaV,
    "org.eclipse.jetty" % "jetty-webapp" % JettyJakartaV,
    "org.eclipse.jetty" % "jetty-proxy"   % JettyJakartaV,
    // Jakarta APIs
    "jakarta.servlet"    % "jakarta.servlet-api"    % JakartaServletV,
    "jakarta.websocket"  % "jakarta.websocket-api"  % JakartaWsV,
    // Jetty JSR‑356 Jakarta implementation for Jetty 11
    // Correct artifact is websocket-jakarta-server (jakarta-websocket-server-impl does not exist on Maven Central)
    "org.eclipse.jetty.websocket" % "websocket-jakarta-server" % JettyJakartaV,
    // Logging backend compatible with slf4j-api 2.x
    "org.slf4j" % "slf4j-simple" % "2.0.13"
  ) else Seq(
    // Jetty 9.4.x (javax.servlet)
    "org.eclipse.jetty" % "jetty-server"  % JettyV,
    "org.eclipse.jetty" % "jetty-servlet" % JettyV,
    "org.eclipse.jetty" % "jetty-servlets"% JettyV,
    "org.eclipse.jetty" % "jetty-webapp"  % JettyV,
    "org.eclipse.jetty" % "jetty-proxy"   % JettyV,
    // JSR 356 (javax.servlet stack)
    "javax.websocket" % "javax.websocket-api" % "1.1",
    "org.eclipse.jetty.websocket" % "javax-websocket-server-impl" % JettyV,
    // javax.servlet API
    "javax.servlet" % "javax.servlet-api" % "3.1.0",
    // Logging backend compatible with slf4j-api 1.7
    "org.slf4j" % "slf4j-simple" % "1.7.36"
  )
}

libraryDependencies ++= (coreDeps ++ containerDeps.value)

// Force Jetty modules to consistent versions. Only pin 9.4.x in non‑Jakarta mode;
// leave Jakarta mode unpinned to avoid forcing javax-era artifacts.
dependencyOverrides ++= {
  if (jakartaMode.value) Seq.empty
  else Seq(
    "org.eclipse.jetty" % "jetty-util" % JettyV,
    "org.eclipse.jetty" % "jetty-io" % JettyV,
    "org.eclipse.jetty" % "jetty-http" % JettyV,
    "org.eclipse.jetty" % "jetty-xml" % JettyV,
    "org.eclipse.jetty" % "jetty-server" % JettyV,
    "org.eclipse.jetty" % "jetty-servlet" % JettyV,
    "org.eclipse.jetty" % "jetty-servlets" % JettyV,
    "org.eclipse.jetty" % "jetty-webapp" % JettyV,
    "org.eclipse.jetty.websocket" % "websocket-api" % JettyV,
    "org.eclipse.jetty.websocket" % "websocket-common" % JettyV,
    "org.eclipse.jetty.websocket" % "websocket-api" % JettyV,
    "org.eclipse.jetty.websocket" % "websocket-common" % JettyV
  )
}

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

lazy val prepareProtosForPB = taskKey[Unit]("Stage .protodevel/.proto into target/proto-pb-src for sbt-protoc")
lazy val generatePstMessages = taskKey[Unit]("Generate PST DTO sources into gen/messages")
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
  val pstToolClasses = base / "target" / "pst-tool-classes"
  val pstProtoClasses = base / "target" / "pst-proto-classes"
  val genMsgDir = base / "gen" / "messages"
  IO.createDirectory(pstToolClasses)
  IO.createDirectory(pstProtoClasses)
  IO.createDirectory(genMsgDir)

  // Compile proto_src Java to classes for reflection
  // Exclude any generated google.protobuf runtime classes if present
  val protoSources = (outSrc ** "*.java").get.filterNot { f =>
    val p = f.getPath.replace('\\', '/')
    p.contains("/com/google/protobuf/")
  }
  if (protoSources.isEmpty) sys.error("proto_src is empty — run Compile / PB.generate first.")
  // Prefer a modern protobuf-java runtime jar (installed by tools/install-macos-deps.sh)
  val protobufDir = base / "third_party" / "runtime" / "protobuf"
  val protobufJar = {
    val candidates = (protobufDir * "protobuf-java-*.jar").get
    if (candidates.nonEmpty) candidates.maxBy(_.getName).getAbsolutePath
    else (protobufDir / "protobuf-java-2.5.0.jar").getAbsolutePath // fallback if user didn't run deps script
  }
  val javacProto = Seq(
    "javac",
    "-g",
    "-cp", protobufJar,
    "-d", pstProtoClasses.getAbsolutePath
  ) ++ protoSources.map(_.getAbsolutePath)
  runCmd(log)(javacProto, base)

  // Compile PST tool sources
  val pstSources = (base / "wave" / "src" / "main" / "java" / "org" / "waveprotocol" / "pst" ** "*.java").get
  val antlrJar = (base / "third_party" / "codegen" / "antlr" / "antlr-3.2.jar").getAbsolutePath
  val guavaJar = (base / "third_party" / "runtime" / "guava" / "guava-16.0.1.jar").getAbsolutePath
  // SBT still bootstraps PST tools from vendored runtime jars; Gradle now owns the main wave commons-cli dependency.
  val commonsCliJar = (base / "third_party" / "runtime" / "commons_cli" / "commons-cli-1.2.jar").getAbsolutePath
  val javacPst = Seq(
    "javac",
    "-g",
    "-cp",
      Seq(
        antlrJar,
        guavaJar,
        commonsCliJar,
        protobufJar,
        pstProtoClasses.getAbsolutePath // for org.waveprotocol.protobuf.Extensions, etc.
      ).mkString(java.io.File.pathSeparator),
    "-d", pstToolClasses.getAbsolutePath
  ) ++ pstSources.map(_.getAbsolutePath)
  runCmd(log)(javacPst, base)

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

  val cp = Seq(
    pstToolClasses.getAbsolutePath,
    pstProtoClasses.getAbsolutePath,
    protobufJar,
    antlrJar,
    guavaJar,
    commonsCliJar
  ).mkString(java.io.File.pathSeparator)

  protosToGen.foreach { protoClassFile =>
    val args = Seq(
      "-s", "pst",
      "-d", genMsgDir.getAbsolutePath,
      "-f", protoClassFile
    ) ++ templates
    val cmd = Seq("java", "-cp", cp, "org.waveprotocol.pst.PstMain") ++ args
    runCmd(log)(cmd, base)
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
  val runtimeJars = (base / "third_party" / "runtime" ** "*.jar").classpath
    .map(_.data.getAbsolutePath)
    // Exclude jars with broken manifests and legacy protobuf runtime
    .filterNot { p =>
      val n = new java.io.File(p).getName
      n.startsWith("guava-treerangemap") || n == "protobuf-java-2.5.0.jar"
    }
  val cp = runtimeJars.mkString(java.io.File.pathSeparator)
  val javacCmd = Seq("javac", "-g", "-cp", cp, "-d", toolClasses.getAbsolutePath) ++ generatorSources.map(_.getAbsolutePath)
  runCmd(log)(javacCmd, base)

  // Run the generator
  val javaCp = (Seq(toolClasses.getAbsolutePath) ++ runtimeJars).mkString(java.io.File.pathSeparator)
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
  // GXP generation removed: templates replaced by HtmlRenderer.java
  .value

// Ensure `run` has a config in place
Compile / run := (Compile / run).dependsOn(prepareServerConfig).evaluated
