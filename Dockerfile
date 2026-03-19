# Multi-stage Dockerfile for Apache Wave (server + web client)

# Build stage: build the distribution with Gradle
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Leverage Docker layer caching: copy Gradle wrappers and settings first
COPY gradlew gradlew.bat settings.gradle build.gradle /workspace/
COPY gradle /workspace/gradle
COPY pst /workspace/pst
COPY wave /workspace/wave
COPY scripts /workspace/scripts
# Minimal copy for build to reduce layers; docs/keys are not needed for build

# Build only the server distribution (installDist) on the Jakarta-only path
RUN ./gradlew --no-daemon :wave:installDist

# Runtime stage: slim JRE image
FROM eclipse-temurin:17-jre
ENV WAVE_HOME=/opt/wave
WORKDIR ${WAVE_HOME}
COPY --from=build /workspace/wave/build/install/wave/ ${WAVE_HOME}/

EXPOSE 9898

# Default to production-ish config location; users can mount or bake their own.
# To use SSL, mount a keystore & set env WAVE_SSL_KEYSTORE_PASSWORD, and edit config/application.conf.
ENTRYPOINT ["/opt/wave/bin/wave"]
