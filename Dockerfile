# ZeroBounce Java SDK – test image (Java 21 + Maven)
FROM maven:3.9-eclipse-temurin-21

# A Maven repository the unprivileged user owns, so the default command can run as it.
ENV MAVEN_OPTS="-Dmaven.repo.local=/m2"
# CVE-2026-114938 (Java2Shell): the x86-64 Base64 intrinsic writes past the buffer.
# Temurin images still ship the vulnerable build. This flag is accepted on ARM too.
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
