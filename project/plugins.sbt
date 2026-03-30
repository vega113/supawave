// Plugins are declared here. Network fetch happens only when you run sbt.

// Fat jar bundling
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")

// Protobuf codegen (embedded protoc)
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")

// Distribution packaging (Phase 5)
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")

// JUnit 5 / Jupiter test framework for E2E suite
addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.11.1")
