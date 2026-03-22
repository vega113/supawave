# Multi-stage Dockerfile for Apache Wave (server + web client)

# Build stage: build the distribution with SBT
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Install SBT (with checksum verification)
RUN curl -fsSL -o /tmp/sbt.tgz "https://github.com/sbt/sbt/releases/download/v1.10.2/sbt-1.10.2.tgz" \
    && echo "a716dd018bd68bc7a95a2dd10337663aa76f443ad6c99deabe5eadd1adfc7639 /tmp/sbt.tgz" | sha256sum -c - \
    && tar xz -C /usr/local --strip-components=1 -f /tmp/sbt.tgz \
    && rm /tmp/sbt.tgz

# Leverage Docker layer caching: copy SBT build definitions first
COPY project/build.properties project/plugins.sbt /workspace/project/
COPY build.sbt /workspace/

# Fetch and cache all dependencies in a separate layer
RUN sbt --batch update

# Copy source trees
COPY pst /workspace/pst
COPY wave /workspace/wave
COPY scripts /workspace/scripts
COPY proto_src /workspace/proto_src
COPY gen /workspace/gen
# Minimal copy for build to reduce layers; docs/keys are not needed for build

# Build only the server distribution (Universal/stage) on the Jakarta-only path
RUN sbt --batch wave/Universal/stage

# Runtime stage: slim JRE image
FROM eclipse-temurin:17-jre
ENV WAVE_HOME=/opt/wave
WORKDIR ${WAVE_HOME}
COPY --from=build /workspace/target/universal/stage/ ${WAVE_HOME}/

EXPOSE 9898

# Default to production-ish config location; users can mount or bake their own.
# To use SSL, mount a keystore & set env WAVE_SSL_KEYSTORE_PASSWORD, and edit config/application.conf.
ENTRYPOINT ["/opt/wave/bin/wave"]
