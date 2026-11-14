# ZeroBounce Java SDK – test image (Java 21 + Maven)
FROM maven:3.9-eclipse-temurin-21

# A Maven repository the unprivileged user owns, so the default command can run as it.
ENV MAVEN_OPTS="-Dmaven.repo.local=/m2"

WORKDIR /app

COPY . .

# Run tests from the zero-bounce-sdk module (downloads dependencies into /m2).
WORKDIR /app/zero-bounce-sdk
RUN mvn -q test && chown -R 65532:65532 /m2 /app

USER 65532:65532

# Default: run the tests again offline, from the cached repository.
CMD ["mvn", "-q", "-o", "test"]
