# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Overview and Quickstart

Apache Wave is a standalone Wave server and rich GWT web client, serving as the reference implementation of the Wave protocol. This repository builds the server, compiles the GWT client, and packages runnable distributions. The system uses real-time collaborative editing with operational transformation, WebSocket communication, and a flexible storage backend system.

**Quick Start:**
```bash
# Build everything
./gradlew clean build

# Run the server
./gradlew run

# Open the client
# Navigate to http://localhost:9898
```

**Default Configuration:**
- Wave server domain: `local.net`
- HTTP address: `localhost:9898`
- Storage: File-based stores in local directories

## Prerequisites

- **Java 8 (JDK 1.8)** - Required for compilation and runtime. The codebase uses sourceCompatibility 1.8 and GWT 2.8.0.
- **Gradle** - Use the included Gradle wrapper (`./gradlew`)
- **Optional Dependencies:**
  - MongoDB for alternate storage backend testing
  - Java keystore file for SSL configuration
- **Network:** Default HTTP endpoint is `localhost:9898`. Ensure the port is available or adjust configuration.

**Note:** This project requires Java 8. Newer Java versions may cause Gradle build failures.

## Essential Build and Development Commands

### Build and Artifacts
```bash
# Build all components
./gradlew build

# Build server and client jar
./gradlew jar
# Output: wave/build/libs/wave-0.4.2.jar

# Build source jars for each project
./gradlew sourcesJar
```

### GWT Compilation
```bash
# Standard GWT compile (production)
./gradlew compileGwt

# Demo style compilation
./gradlew compileGwtDemo

# Development style compilation
./gradlew compileGwtDev

# GWT Super Dev Mode (for debugging)
./gradlew gwtDev
```

### Server Execution
```bash
# Run server from source
./gradlew run
```

### Distribution Packaging
```bash
# Build full source and binary distributions
./gradlew createDist
# Output: distributions/ directory

# Build application distribution
./gradlew :wave:assembleDist
# Output: wave/build/distributions/
```

### IDE Support
```bash
# Eclipse project files
./gradlew eclipse
./gradlew cleanEclipse

# IntelliJ IDEA project files
./gradlew idea
./gradlew cleanIdea
```

### Code Generation and Checks
```bash
# Generate message sources from Protocol Buffers
./gradlew generateMessages

# Generate GXP template sources
./gradlew generateGXP

# Apache RAT license checks
./gradlew rat

# Clean all build artifacts
./gradlew clean
```

**Note:** If PST jar is missing during generation, build it explicitly: `./gradlew pst:jar`

## Running the Server

### From Source
```bash
# Using Gradle
./gradlew run
# Client available at: http://localhost:9898
```

### Using Distribution Scripts
```bash
# Create distribution
./gradlew :wave:assembleDist

# Extract and run
cd wave/build/distributions
unzip wave-0.4.2.zip
cd wave-0.4.2

# Unix/Linux/macOS
bin/wave

# Windows
bin/wave.bat
```

### Configuration

The server uses HOCON configuration format. Create `config/application.conf` to override defaults from `wave/config/reference.conf`.

**Basic Configuration Example:**
```hocon
core {
  wave_server_domain : "example.com"
  http_frontend_addresses : ["0.0.0.0:9898"]
  http_frontend_public_address : "example.com:9898"
}
```

**Important:** If the domain is invalid, startup will fail with "Invalid wave domain" error. Use a valid DNS-like domain string.

## High-Level Architecture Overview

### Client Architecture
- **GWT Web Client:** Compiled to JavaScript and served from the `war/` directory
- **Super Dev Mode:** Available for rapid development iteration
- **Real-time Communication:** WebSocket-based with fallback mechanisms

### Server Architecture

**Entry Point:** `org.waveprotocol.box.server.ServerMain`

**Core Components:**
- **Dependency Injection:** Google Guice modules assemble server subsystems
- **HTTP/WebSocket Server:** Jetty 9.2 handles all network communication
- **RPC Layer:** 
  - `ProtocolWaveClientRpc` defines the client API surface
  - `WaveClientRpcImpl` implements the client-facing operations
  - Protocol Buffers (proto2) with PST code generation for messages

**Wave Engine:**
- **WaveletProvider:** Core wavelet operations and storage interface
- **WaveBus:** Event distribution system for wave updates
- **PerUserWaveViewDispatcher:** Manages per-user wave views and permissions
- **WaveIndexer:** Search indexing and wave discovery

**Storage Backends:**
- **Pluggable Storage:** Accounts, deltas, and attachments support file, memory, or MongoDB
- **Delta Store:** Operational transformation history
- **Account Store:** User authentication and profiles
- **Attachment Store:** File upload handling

**Search Systems:**
- **Memory Search:** Default in-memory indexing
- **Lucene Search:** File-based search indexes
- **Solr Search:** Currently disabled

**Robot Framework:**
- **Active/Passive Robots:** Webhook and polling-based robot integrations
- **Agent Robots:** Built-in WelcomeRobot, PasswordRobot, RegistrationRobot
- **Robot Gateway:** Event routing to external robots

**Other Systems:**
- **Federation:** No-op federation module (placeholder for multi-server)
- **Profiling:** Optional server-side metrics via TimingFilter and StatuszServlet
- **Security:** SSL/TLS and X.509 client certificate support

### Key Technologies
- **GWT 2.8:** Client-side framework
- **Protocol Buffers 2.6.1:** RPC serialization with PST code generation
- **Google Guice:** Dependency injection
- **Jetty 9.2:** HTTP and WebSocket server
- **Typesafe Config:** HOCON configuration management
- **BouncyCastle:** Cryptographic operations

## Key Development Workflows

### GWT Super Dev Mode
```bash
# Start Super Dev Mode server
./gradlew gwtDev

# Follow Gradle output instructions to:
# 1. Open the code server page
# 2. Enable dev mode in browser
# 3. Make changes and recompile modules
# 4. Browser refresh picks up updated JS
```

### Switching Storage Backends

**MongoDB Configuration:**
```hocon
# config/application.conf
core {
  account_store_type : mongodb
  delta_store_type : mongodb
  attachment_store_type : mongodb
  mongodb_host : "127.0.0.1"
  mongodb_port : 27017
  mongodb_database : wiab
}
```

**File System Configuration:**
```hocon
# config/application.conf
core {
  account_store_type : file
  account_store_directory : _accounts
  delta_store_type : file
  delta_store_directory : _deltas
  attachment_store_type : disk
  attachment_store_directory : _attachments
}
```

### SSL Configuration
```hocon
# config/application.conf
security {
  enable_ssl : true
  ssl_keystore_path : "wave.keystore"
  ssl_keystore_password : "changeit"
}
```

### X.509 Client Authentication
```hocon
# config/application.conf
administration {
  disable_loginpage : true
}
security {
  enable_ssl : true
  enable_clientauth : true
  clientauth_cert_domain : "example.com"
  ssl_keystore_path : "wave.keystore"
  ssl_keystore_password : "changeit"
}
```

### Vagrant Development Environment
```bash
# Linux VM with pre-built Wave
vagrant up ubuntu
# or
vagrant up fedora

# Windows development VM
vagrant up win10
```

The Linux VMs install Wave to `/opt/apache/wave` and bind to `0.0.0.0:9898` for external access.

## Important File Locations and Structure

### Build Configuration
- **`wave/build.gradle`** - Main application build script with dependencies, JVM args, and tasks
- **`build.gradle`** - Root project configuration and distribution tasks
- **`settings.gradle`** - Multi-project settings

### Source Code
- **`wave/src/main/java`** - Server-side Java source including ServerMain and servlets
- **`wave/src/test/java`** - Server-side test code
- **`wave/src/proto/proto`** - Protocol Buffer definitions (`.protodevel` files) used by PST

### Generated Code
- **`wave/generated/main/java`** - Generated Java sources from PST and GXP
- **`wave/generated/proto/java`** - Generated Protocol Buffer Java classes

### Web Resources
- **`wave/war`** - GWT client resources served to browsers
- **`wave/war/WEB-INF`** - Generated during build, contains web.xml and classes

### Configuration
- **`wave/config/reference.conf`** - Complete configuration reference with defaults
- **`config/application.conf`** - User overrides (create this file)
- **`wave/config/wiab-logging.conf`** - Java logging configuration
- **`wave/config/jaas.config`** - JAAS authentication configuration

### Distributions
- **`wave/build/distributions/`** - Application distribution archives from `:wave:assembleDist`
- **`distributions/`** - Combined source and binary distributions from `createDist`

### PST Module
- **`pst/`** - Protocol Buffer String Templating tools for code generation

### Runtime Data Directories
Created automatically with default file-based storage:
- **`_accounts`** - User account data
- **`_deltas`** - Wave operation history
- **`_attachments`** - Uploaded files
- **`_sessions`** - User session persistence
- **`_indexes`** - Search indexes
- **`_certificates`** - SSL certificates
- **`_thumbnail_patterns`** - Attachment thumbnails

## Testing Suites and Commands

### Test Execution
```bash
# Standard unit tests
./gradlew test

# MongoDB integration tests
./gradlew testMongo
# Requires MongoDB running on configured host:port

# Large/long-running tests
./gradlew testLarge

# GWT client tests (currently broken)
./gradlew testGwt

# All test suites
./gradlew testAll
```

### Test Configuration
- **JaCoCo:** Code coverage reporting is enabled
- **Test Logging:** Adjust `wave/config/wiab-logging.conf` for test-specific logging
- **MongoDB Tests:** Require a running MongoDB instance matching `core.mongodb_*` settings

**Note:** `testGwt` is currently broken due to a GWT-Jetty classpath conflict. Use other test suites for validation.

## Troubleshooting and Tips

### Common Build Issues

**PST jar not found:**
```bash
./gradlew pst:jar
# Then retry your original command
```

**wave:extractApi unzip error:**
- Clear the problematic jar from Gradle cache (`~/.gradle/caches/`)
- Restart your IDE
- Retry the build

**Java version compatibility:**
- Ensure Java 8 is installed and active
- Check `java -version` output
- Set `JAVA_HOME` if necessary

### Runtime Issues

**Port 9898 conflicts:**
```hocon
# config/application.conf
core {
  http_frontend_addresses : ["localhost:8080"]
}
```

**Invalid wave domain error:**
```hocon
# config/application.conf
core {
  wave_server_domain : "example.com"
}
```

**MongoDB connection failures:**
- Verify MongoDB is running: `mongod --version`
- Check host/port configuration
- Ensure firewall allows local connections
- Verify database name and permissions

### GWT Development Issues

**GWT compilation failures:**
- Ensure all Java sources compile first: `./gradlew compileJava`
- Check for GWT-incompatible Java features in client code
- Verify GWT module definitions in `.gwt.xml` files

**Super Dev Mode connection issues:**
- Check that both the Wave server and GWT Dev Mode server are running
- Verify the Super Dev Mode bookmarklets are installed
- Clear browser cache and retry

## Appendix: Vagrant and Binary Distributions

### Vagrant Usage

**Linux Development VMs:**
```bash
# Ubuntu with automatic Wave build and install
vagrant up ubuntu

# Fedora with automatic Wave build and install  
vagrant up fedora

# Access Wave at: http://localhost:9898 (forwarded from VM)
# SSH access: vagrant ssh ubuntu (or fedora)
# Wave installed at: /opt/apache/wave (inside VM)
```

**Windows Development VM:**
```bash
# Windows 10 development environment
vagrant up win10

# Manual build required inside VM following Gradle task documentation
# Source copied to: ~/Documents/Apache-Wave (inside VM)
```

### Binary Distributions

**Official Releases:**
- Latest releases: https://dist.apache.org/repos/dist/release/incubator/wave/
- Development builds: https://dist.apache.org/repos/dist/dev/incubator/wave/
- Nightly builds: https://builds.apache.org/view/S-Z/view/Wave/job/wave-artifacts/lastSuccessfulBuild/artifact/

**Running Binary Distributions:**
```bash
# Extract archive
tar -xjf apache-wave-bin-0.4.tar.bz2
cd apache-wave-bin-0.4

# Unix/Linux/macOS
./bin/wave

# Windows
bin\wave.bat
```

**Configuration for Binary Distributions:**
- Copy `config/reference.conf` to `config/application.conf` 
- Edit `config/application.conf` with your overrides
- Ensure proper file permissions on data directories


# Integration Guide (Non‑Duplicative)

This document focuses on how to use AI agent-specific tooling with this repository.
For all general project information (overview, structure, setup, building, running,
dependencies, configuration), please refer to README.md. This avoids duplication
and keeps a single source of truth.


## Agent Journal Tool Guidelines

When using an agent with the Journal MCP tool enabled, the agent must actively
document its work so activity is auditable, decisions are explainable, and progress is trackable.

- Start-of-turn context refresh: Before beginning any new work in a turn, review the most recent
  journal entries to restore context and ensure continuity.
- Frequency: Journal frequently throughout a turn (at minimum after planning, after key decisions,
  after completing a task, and before ending the turn).
- Content of entries:
    - Thoughts and feelings: Briefly capture current understanding, uncertainties, confidence level,
      and any concerns or risks.
    - Decisions: Whenever a choice is made, record the options considered, pros/cons, and why the
      specific option was chosen.
    - Plan and tasks: Record the current plan, task list, owners (if applicable), and status for
      each task.
    - Progress updates: Note what was attempted, what worked, what failed, and any blockers.
    - End-of-turn summary: Summarize what changed since the start of the turn, what was completed,
      what remains, and the next intended action.

Recommended structure for each journal update:

1. Context: What I am working on now and why (include “start-of-turn context refresh” when applicable).
2. Plan: Current plan and tasks (with statuses: Not started / In progress / Blocked / Done).
3. Decision Log: Alternatives considered and rationale for any choices made.
4. Progress: Actions taken, results, and evidence (links/paths/commits/tests if relevant).
5. Feelings/Confidence: Confidence level, risks/unknowns, and mitigation ideas.
6. Next Step: The very next concrete action.
7. End-of-Turn Summary: Brief recap before yielding control.

Examples (prompts to the agent or tool-invocation intent):

- Start-of-turn context refresh: "Journal: Reviewed last two entries (build failure and fix attempt).
  Resuming work on CI config. Context restored; proceeding with task 2."
- Start-of-turn planning: "Journal: Planning current work on <issue>. Tasks: [1) Analyze files,
    2) Implement change, 3) Test]. Initial status: all Not started. Confidence: medium; risk: unclear
       config format."
- Decision rationale: "Journal: Considered option A (low effort, partial coverage) vs option B
  (more robust, higher effort). Chose B due to long-term maintainability and testability."
- Progress update: "Journal: Completed task 1 (analysis). Findings: file X requires section Y.
  Starting task 2."
- End-of-turn: "Journal: Summary – Implemented section addition in GEMINI.md, no code changes
  required. Remaining: team review. Next step: integrate feedback."

Notes:

- Keep entries concise but specific; prefer bullet points and checklists when appropriate.
- If a task is blocked, clearly state what is needed to unblock it.
- Include references to files, paths, or commits for traceability when applicable.
- Follow any organization-specific retention or privacy policies when journaling sensitive information.