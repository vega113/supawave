# Multi-stage Dockerfile for Apache Wave (server + web client)

# Build stage: build the distribution with SBT
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Install SBT
RUN apt-get update -qq && \
    apt-get install -y -qq apt-transport-https gnupg && \
    install -d -m 0755 /etc/apt/keyrings && \
    curl --fail --show-error --location --retry 5 \
      "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
      | gpg --dearmor -o /etc/apt/keyrings/sbt-archive-keyring.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/sbt-archive-keyring.gpg] https://repo.scala-sbt.org/scalasbt/debian all main" \
      | tee /etc/apt/sources.list.d/sbt.list > /dev/null && \
    apt-get update -qq && \
    apt-get install -y -qq sbt && \
    rm -rf /var/lib/apt/lists/*

# Copy build definition first for Docker layer caching
COPY build.sbt /workspace/
COPY project /workspace/project
COPY .sbtopts /workspace/

# Copy source trees and root docs needed by Universal/stage mappings
COPY pst /workspace/pst
COPY wave /workspace/wave
COPY gen /workspace/gen
COPY scripts /workspace/scripts
COPY THANKS RELEASE-NOTES KEYS DISCLAIMER /workspace/

# Build sequentially: compile, GWT client, then stage the distribution
RUN sbt --batch "pst/compile; wave/compile; compileGwt; Universal/stage"

# Runtime stage: slim JRE image
FROM eclipse-temurin:17-jre
LABEL org.waveprotocol.mongo-migration-marker-supported="true"
ENV WAVE_HOME=/opt/wave
RUN apt-get update -qq && \
    apt-get install -y -qq curl && \
    rm -rf /var/lib/apt/lists/*
WORKDIR ${WAVE_HOME}
COPY --from=build /workspace/target/universal/stage/ ${WAVE_HOME}/
RUN mkdir -p ${WAVE_HOME}/logs

EXPOSE 9898

# Default to production-ish config location; users can mount or bake their own.
# To use SSL, mount a keystore & set env WAVE_SSL_KEYSTORE_PASSWORD, and edit config/application.conf.
ENTRYPOINT ["/opt/wave/bin/wave"]
