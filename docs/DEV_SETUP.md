# Developer Setup (JDK 17, SBT, Protobuf)

This guide helps you build and run the Apache Wave server on JDK 17.

Prerequisites
- Java: JDK 17 installed
  - Suggested: sdkman
    - curl -s "https://get.sdkman.io" | bash
    - sdk install java 17.0.12-zulu
- SBT 1.10+ installed
  - See https://www.scala-sbt.org/download.html
- Git
- macOS or Linux shell

Notes
- SBT will use Java toolchains to compile with JDK 17 automatically for Java modules.
- Protobuf compiler (protoc) is provided via the sbt-protoc plugin; no system install needed.

Quick start (server only)
- From repo root:
  - sbt pst/compile wave/compile
  - sbt Universal/stage
  - ./target/universal/stage/bin/incubator-wave
- Visit http://localhost:9898/

Troubleshooting
- If SBT cannot find a suitable JDK, ensure JAVA_HOME is set to a JDK 17 and/or install via sdkman.
- For protobuf errors, ensure you ran `sbt pst/assembly` before `sbt wave/compile` if building tasks individually.
