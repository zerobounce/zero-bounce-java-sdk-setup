# ZeroBounce Java SDK – test image (Maven, then Corretto 21.0.13.1.2).
# The Maven base image's Temurin 21 does not include the CVE-2026-114938 fix.
FROM maven:3.9-eclipse-temurin-21

# Replace that JDK with Amazon Corretto 21.0.13.1.2. Checksums are from the
# Corretto 21.0.13.1.2 release notes.
ARG TARGETARCH
RUN set -euo pipefail; \
    case "$TARGETARCH" in \
      amd64) arch=x64; sha=2aaa2a9a43143b20ff0b28c8d0da27407b97dc40b160d9a2f60a26965815e948 ;; \
      arm64) arch=aarch64; sha=55751c47c7e3be3b33af43ccdcf3a6f4814d308390b7400477594d421bd16a53 ;; \
      *) echo "unsupported architecture: $TARGETARCH"; exit 1 ;; \
    esac; \
    if ! command -v curl >/dev/null 2>&1; then \
      apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*; \
    fi; \
    curl -fsSL "https://corretto.aws/downloads/resources/21.0.13.1.2/amazon-corretto-21.0.13.1.2-linux-${arch}.tar.gz" -o /tmp/corretto.tar.gz; \
    echo "$sha  /tmp/corretto.tar.gz" | sha256sum -c -; \
    rm -rf /opt/java/openjdk; \
    mkdir -p /opt/java/openjdk; \
    tar -xzf /tmp/corretto.tar.gz -C /opt/java/openjdk --strip-components=1; \
    rm /tmp/corretto.tar.gz; \
    java -version 2>&1 | grep -q 'Corretto-21.0.13.1.2'

# A Maven repository the unprivileged user owns, so the default command can run as it.
ENV MAVEN_OPTS="-Dmaven.repo.local=/m2"
# CVE-2026-114938. Kept even on the patched JDK. Accepted on ARM.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"
# Maven keeps its settings and state here; the unprivileged user owns it (the default is /root/.m2, which it cannot write).
ENV MAVEN_CONFIG=/m2-config

WORKDIR /app

COPY . .

WORKDIR /app/zero-bounce-sdk

# Fetch every dependency and plugin into /m2 (the only step that needs the network). go-offline does not fetch
# the surefire JUnit provider or the JUnit launcher that surefire resolves at test time, so get them explicitly.
RUN mkdir -p /m2 /m2-config && mvn -q dependency:go-offline \
 && mvn -q dependency:get -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.5.6 \
 && mvn -q dependency:get -Dartifact=org.junit.platform:junit-platform-launcher:6.1.3

# Run the suite offline with no network at all, so a build-time run cannot reach any server.
RUN --network=none mvn -q -o test && chown -R 65532:65532 /m2 /m2-config /app

USER 65532:65532

# Default: run the tests again offline, from the cached repository.
CMD ["mvn", "-q", "-o", "test"]
