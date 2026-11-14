# ZeroBounce Java SDK – test image (Java 21 + Maven)
FROM maven:3.9-eclipse-temurin-21

# A Maven repository the unprivileged user owns, so the default command can run as it.
ENV MAVEN_OPTS="-Dmaven.repo.local=/m2"
# Maven keeps its settings and state here; the unprivileged user owns it (the default is /root/.m2, which it cannot write).
ENV MAVEN_CONFIG=/m2-config

WORKDIR /app

COPY . .

WORKDIR /app/zero-bounce-sdk

# Fetch every dependency and plugin into /m2 (the only step that needs the network).
RUN mkdir -p /m2 /m2-config && mvn -q dependency:go-offline

# Run the suite offline with no network at all, so a build-time run cannot reach any server.
RUN --network=none mvn -q -o test && chown -R 65532:65532 /m2 /m2-config /app

USER 65532:65532

# Default: run the tests again offline, from the cached repository.
CMD ["mvn", "-q", "-o", "test"]
