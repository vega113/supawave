// incubator-wave SBT build (Phase 1: minimal Java-only skeleton, ported from Wiab.pro)

ThisBuild / organization := "org.apache.wave"
name := "incubator-wave"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version      := "0.1.0-SNAPSHOT"

// Compile Java first, Scala later (we'll add Scala modules in future phases)
Compile / compileOrder := CompileOrder.JavaThenScala

// Target bytecode for Jakarta runtime
javacOptions ++= Seq("--release", "11")

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

// Exclude any javax-servlet/websocket based sources and legacy ServerMain
Compile / unmanagedSources := {
  val files = (Compile / unmanagedSources).value
  files.filterNot { f =>
    val p = f.getPath.replace('\\','/')
    val isSrc = p.contains("/wave/src/main/java/")
    val excludeByName = isSrc && p.endsWith("/org/waveprotocol/box/server/ServerMain.java")
    val excludeByImport = isSrc && {
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
    val baseExcludes = excludeByName || excludeByImport || excludeAttachments || excludeRender || excludeSearchProfile || excludeMongo || excludeHtmlModule || excludeRegistrationUtil || excludeDefaultServerModule || excludeDefaultMappings || excludePersistenceModule || excludeMigration || excludeDataTools || excludeGenShims
    val robotsExcludes = excludeRobotsAgents || excludeRobotsExamples
    baseExcludes || robotsExcludes
  }
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
lazy val JettyJakartaV  = "12.0.23"                    // jakarta servlet stack (EE10, matches Gradle)
lazy val JakartaServletV = "5.0.0"                     // jakarta.servlet-api
lazy val JakartaWsV      = "2.0.0"                     // jakarta.websocket-api

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

lazy val containerDeps = Seq(
  "org.eclipse.jetty" % "jetty-server"  % JettyJakartaV,
  "org.eclipse.jetty" % "jetty-servlet" % JettyJakartaV,
  "org.eclipse.jetty" % "jetty-servlets" % JettyJakartaV,
  "org.eclipse.jetty" % "jetty-webapp" % JettyJakartaV,
  "org.eclipse.jetty" % "jetty-proxy"   % JettyJakartaV,
  "jakarta.servlet"    % "jakarta.servlet-api"    % JakartaServletV,
  "jakarta.websocket"  % "jakarta.websocket-api"  % JakartaWsV,
  "org.eclipse.jetty.websocket" % "websocket-jakarta-server" % JettyJakartaV,
  "org.slf4j" % "slf4j-simple" % "2.0.13"
)

libraryDependencies ++= (coreDeps ++ containerDeps)

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

  val depCp = (Compile / dependencyClasspath).value.map(_.data.getAbsolutePath)
  val cp = (Seq(pstAssemblyJar.getAbsolutePath, pstProtoClasses.getAbsolutePath, protobufJar) ++ depCp)
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
    val gxpcJar = base / "third_party" / "runtime" / "gxp" / "gxp-0.2.4-beta.jar"
    if (!gxpcJar.exists) sys.error("Missing GXP compiler jar: third_party/runtime/gxp/gxp-0.2.4-beta.jar")
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
  // .dependsOn(generateGxp) // disabled for now - requires gxpc jar; can be run manually with `sbt generateGxp`
  .value

// Ensure `run` has a config in place
Compile / run := (Compile / run).dependsOn(prepareServerConfig).evaluated
