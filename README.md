| Tool | Status |
| --- | --- |
| Jenkins | [![Build Status](https://builds.apache.org/buildStatus/icon?job=wave-small_tests)](https://builds.apache.org/job/wave-small_tests) |
| Travis | [![Build Status](https://travis-ci.org/apache/incubator-wave.svg?branch=master)](https://travis-ci.org/apache/incubator-wave) |

# Apache Wave

The Apache Wave project is a stand alone wave server and rich web client
that serves as a Wave reference implementation.
Apache Wave site: http://incubator.apache.org/wave/.
This project lets developers and enterprise users run wave servers and
host waves on their own hardware. And then share those waves with other
wave servers.

## Cryptographic Software Notice

This distribution includes cryptographic software.  The country in
which you currently reside may have restrictions on the import,
possession, use, and/or re-export to another country, of
encryption software.  BEFORE using any encryption software, please
check your country's laws, regulations and policies concerning the
import, possession, or use, and re-export of encryption software, to
see if this is permitted.  See <http://www.wassenaar.org/> for more
information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms.  The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS
Export Administration Regulations, Section 740.13) for both object
code and source code.

The following provides more details on the included cryptographic
software:

  Wave requires the BouncyCastle Java cryptography APIs:
    http://www.bouncycastle.org/java.html

## Quick Start (Dev)

Requirements: Java 17+, Gradle Wrapper (included)

1) One‑time bootstrap (creates config and optional dev keystore):

   - Dev HTTP only:
     `scripts/wave-bootstrap.sh`

   - Dev HTTPS (self‑signed):
     `scripts/wave-bootstrap.sh --with-ssl`

2) Run the server:

   `./gradlew :wave:run`

   - Default dev URL: http://localhost:9898/
   - If you enabled SSL: https://localhost:9898/

Notes:
- WebSocket auth: In dev, `network.session_cookie_http_only = false` so the legacy web client can read `JSESSIONID` for a WebSocket fallback authenticate.
- The server renders the WebSocket address from the request Host header to avoid localhost/127.0.0.1 cookie mismatches.

## Setup with Vagrant

A vagrant setup has been provided for automatic compile on a Ubuntu or Fedora
linux box. A windows box is also provided for testing but only installs requirements,
compilation and setup of the server require manual setup.

Note:

- requires vagrant and virtual box to be installed and an internet
connection.
- these images use jdk v8 which isn't officially supported but is used to test
for future compatibility.

### Ubuntu & Fedora ( recommended )

running `vagrant up ubuntu` or `vagrant up fedora` will create a linux box
where the project will be compiled and dist installed to `/opt/apache/wave`. In
this folder you can find the current dist source and run the server. A server
config file has been provided to allow the server to be accessible outside the
vm listening on `0.0.0.0:9898`.

To update the dist just run `vagrant up linux` where linux is either ubuntu or
fedora.

note:
`vagrant ssh linux` where linux is either ubuntu or fedora can be used for a
ssh session.

### Windows 10 (requires vagrant 1.8+)

running `vagrant up win10` will setup a windows 10 environment for Apache Wave,
this vm does not setup a dist but that can be done by following the steps below
in the Gradle Tasks section within the vm.

The virtual machine will make a copy of the current source into the users
documents folder under Apache-Wave. Running the vagrant up command again will
update this folder. The standard config for vagrant testing can be located at
`scripts/vagrant/application.conf`, just copy this file to the location
of the distributions config folder.

### Note

These vagrant setups are not production use and should not be used as such.

## Setup Dev

Apache Wave can be setup for eclipse and intellij IDE's.

Running `./gradlew eclipse` or `./gradlew idea` will generate all project files needed.
In a situation where dependencies have changed or project structure has changed
run `./gradlew cleanEclipse` or `./gradlew cleanIdea` depending on your IDE.


## Gradle Tasks

Apache Wave now targets Java 17+. Use the included Gradle Wrapper.

Gradle tasks can be run by `./gradlew [task name]`

Test Tasks:

- **test**: runs the standard unit tests.
- **testMongo**: runs the mongodb tests.
- **testLarge**: runs the more lengthy test cases.
- **testGwt**: runs gwt specific tests (currently broken till gwt jetty conflict issue).
- **testAll**: runs all the above tests.

Compile Tasks:

- **generateMessages**: Generates the message source files from the .st sources.
- **generateGXP**: Compiles sources from the gxp files.
- **compileJava**: Compiles all java sources.
- **compileGwt**: Compiles all the Gwt sources.
- **compileGwtDemo**: Compiles all the Gwt sources in Demo style.
- **compileGwtDev**: Compiles all the Gwt sources in Dev style.

Check Tasks:

- **rat**: will run the apache rat tool to check all distribution files.

Run Tasks:

- **run**: runs the server with the default parameters and with gwt compiled normally.
- **gwtDev**: runs the gwt development mode.

Distribution Tasks:
- **jar**: builds jar file for the project.
- **sourcesJar**: builds a source jar file for each project.
- **createDist**: builds the zip and tar file for bin and source.
- **createDistBin**: builds the zip for distribution.
- **createDistBinZip**: builds the zip for distribution.
- **createDistBinTar**: builds the tar for distribution.
- **createDistSource**: builds the zip and tar file for distributing the source.
- **createDistSourceZip**: builds the zip for distributing the source.
- **createDistSourceTar**: builds the tar for distributing the source.


## Build

To build the client and server:
    `./gradlew jar`
It will be created in wave/build/libs/wave-*version*.jar

The sources can also be packaged into a jar by doing
    `./gradlew sourcesJar`
This will create a `project name`-sources.jar in each projects build/libs directory.

Note:

- if pst-`version`.jar is unable to be found run `./gradlew pst:jar` then retry.
- if a jar is unable to be unzipped with wave:extractApi then delete the jar from your cache and try again.
    You may need to restart. If problem persists let the newsgroup know or create an issue on Jira.

Take a look at the reference.conf to learn about configuration and possible/default values.

To run from sources:
    ./gradlew :wave:run
The web client is accessible by default at http://localhost:9898/.

To build an installable distribution:
    ./gradlew :wave:installDist
Use `scripts/wave-smoke.sh start|status|stop` against the installed dist.

### Jetty 12 (Jakarta) profile

- Build and run with Jetty 12 / Jakarta EE10 APIs without affecting the default build:
  - Compile Jakarta sources and tests:
    `./gradlew -PjettyFamily=jakarta :wave:classes :wave:jakartaTestClasses`
  - Run the Jakarta test suite:
    `./gradlew -PjettyFamily=jakarta :wave:testJakarta`
  - Build an installable distribution (Jakarta path):
    `./gradlew -PjettyFamily=jakarta :wave:installDist`

- Docker image with Jakarta profile:
  - `docker build --build-arg JETTY_FAMILY=jakarta -t wave:jakarta .`
  - `docker run --rm -p 9898:9898 wave:jakarta`

Note: Until the Jakarta path becomes the default, you can continue to use the existing (javax/Jetty 9.4) build and switch to Jakarta explicitly via `-PjettyFamily=jakarta`.

### Enabling SSL and handling sensitive data

- To enable SSL locally or in Docker, you need a Java keystore that contains a certificate/private key.
  - Example (self-signed for development only):
    `keytool -genkeypair -alias wave -keyalg RSA -keysize 2048 -validity 365 \\
      -keystore wave/config/keystore.jks -storepass changeit -dname "CN=localhost"`
  - Set `WAVE_SSL_KEYSTORE_PASSWORD` and point the server config to the keystore path.
- Never commit keystores or passwords to source control. Avoid printing secrets in logs; review your logging configuration to ensure sensitive values are not logged.
- Scrub CI/CD logs and artifacts as needed and prefer environment variables or secret stores for sensitive configuration.


## To learn more about Wave in a Box and Wave Federation Protocol:

1. Subscribe to the wave-dev mailing list, find instructions at http://incubator.apache.org/wave/mailing-lists.html.
2. Visit the Apache Wave wiki at https://cwiki.apache.org/confluence/display/WAVE/Home.
3. Look at the white papers folder - the information is a bit old but still usable.
4. Watch the Wave Summit videos on YouTube, find the links at: https://cwiki.apache.org/confluence/display/WAVE/Wave+Summit+Talks


## Dev vs. Prod configuration

Configuration files live under `wave/config/`.

For an overview of important server flags and test environment variables (including temporary experimental flags slated for removal), see docs/CONFIG_FLAGS.md.

- `reference.conf` — all defaults and documented options.
- `application.conf` — your overrides (created by `scripts/wave-bootstrap.sh`).

Key settings:

- `core.http_frontend_addresses`: list of `host:port` listeners. Dev default is `127.0.0.1:9898`.
- `network.session_cookie_http_only`:
  - Dev default: `false` (legacy web client reads `JSESSIONID` to send a WebSocket ProtocolAuthenticate).
  - Prod recommended: `true`.
- `security.enable_ssl`:
  - Dev default: `false` (HTTP only).
  - Prod recommended: `true` with a real certificate.

### Enabling SSL

Option A (dev self‑signed):

    scripts/wave-bootstrap.sh --with-ssl

This generates `wave/config/dev-keystore.jks` (password `changeme`) and flips `enable_ssl = true`.

Option B (prod):

1. Obtain a real certificate and create/import a Java keystore (JKS or PKCS12).
2. In `application.conf`:

```
security {
  enable_ssl = true
  ssl_keystore_path = "config/your-prod.jks"
  ssl_keystore_password = ${?WAVE_SSL_KEYSTORE_PASSWORD}
}
```

Run with the password provided by environment variable:

    export WAVE_SSL_KEYSTORE_PASSWORD='...'
    ./gradlew :wave:run


### X.509 client authentication (optional)

If your users have X.509 certificates which include their email address, you can have
them logged in automatically (with their wave ID being the same as their email address):
You can get X.509 certificates issued from any normal CA (e.g. StartSSL offer them for free).
You can get your CA's certficate from their website, though note they might provide more than 1 certificate which you need to chain before your client certificates are considered trusted.

1. Add the signing CA to your keystore file.
2. Set enable_clientauth = true
3. Set clientauth_cert_domain (to the part after the "@" in your email addresses).
4. (optional) Set disable_loginpage = true to prevent password-based logins.

Users will be automatically logged in when they access the site, with the username
taken from the email address in their certificate.

Setting up third party optional dependencies:

## To enable MongoDB:

In order to specify MongoDB in server.config as the storage option for storing deltas, accounts and attachments - you need to install according to instructions at: http://www.mongodb.org/downloads.
Or on Ubuntu Linux you can use the following command:
    sudo apt-get install mongodb-org

## Solr (status)

Solr integration is currently disabled. The code paths remain for historical reference,
but the build no longer relies on Ant and we do not ship Solr helpers. If you want to
experiment, point `core.search_type = solr` and set `core.solr_base_url`, then run a
separate Solr instance yourself. Contributions to re-enable and modernize Solr support
via Gradle are welcome.
## Docker

Build the image (multi-stage, Java 17):

    docker build -t wave:dev .

Run (HTTP on 9898):

    docker run --rm -p 9898:9898 wave:dev

Mount a custom config (optional):

    docker run --rm -p 9898:9898 -v "$PWD/wave/config:/opt/wave/config" wave:dev

Enable SSL in the container (mount keystore and set env):

    docker run --rm -p 9898:9898 \
      -v "$PWD/wave/config:/opt/wave/config" \
      -e WAVE_SSL_KEYSTORE_PASSWORD=changeme wave:dev
