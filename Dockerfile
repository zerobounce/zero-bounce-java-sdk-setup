# ZeroBounce Java SDK – test image (Java 21 + Maven)
FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

COPY . .

# Run tests from the zero-bounce-sdk module
WORKDIR /app/zero-bounce-sdk
RUN mvn -q test

# Default: run tests (re-run in case of docker run without build)
CMD ["mvn", "-q", "test"]
