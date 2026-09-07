# syntax=docker/dockerfile:1

# ---- Build stage: compile, test, and package the executable jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and build descriptor first so dependency resolution is
# cached in its own layer and only re-runs when the build config changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml checkstyle.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Now the sources. `package` runs the unit + property tests in the build stage.
COPY src/ src/
RUN ./mvnw -B clean package

# ---- Runtime stage: slim, non-root JRE ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system app && useradd --system --gid app --home /app app

# The Spring Boot repackaged (executable) jar is the only *.jar in target.
COPY --from=build /workspace/target/exchange-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
