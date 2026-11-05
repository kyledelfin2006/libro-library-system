# Compile with the JDK in a separate stage so the runtime image contains no build tooling.
FROM eclipse-temurin:25-jdk-alpine AS build
# Keep Maven's build tree separate from the final runtime working directory.
WORKDIR /build
# Copy wrapper metadata and the POM first so dependency downloads can be cached between source edits.
COPY .mvn/ .mvn/
# Include the pinned wrapper and project descriptor needed for Maven startup.
COPY mvnw pom.xml ./
# Ensure the Linux wrapper is executable and prefetch dependencies before application source is copied.
RUN chmod +x ./mvnw && ./mvnw -B -ntp dependency:go-offline
# Copy application sources only after the dependency layer is prepared.
COPY src/ src/
# Build one executable Spring Boot JAR; CI runs tests separately before image publication.
RUN ./mvnw -B -ntp -DskipTests package

# Run the application on a smaller JRE-only image with an unprivileged user.
FROM eclipse-temurin:25-jre-alpine
# Keep the runtime artifact and process in a predictable application directory.
WORKDIR /app
# Create a dedicated system account so a compromised API process has no root privileges.
RUN addgroup -S libro && adduser -S -G libro libro
# Copy the single, versioned artifact from the build stage and assign it to the runtime account.
COPY --from=build --chown=libro:libro /build/target/library-api-system-*.jar app.jar
# Bound JVM heap sizing to the container limit and keep offset-free timestamps on the library clock.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Manila"
# Drop privileges before starting the server.
USER libro
# Document the HTTP port used by the API and health probes.
EXPOSE 8080
# Probe /readyz every 30s; allow 60s for migrations, then require success within 5s for three retries.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -qO- http://127.0.0.1:8080/readyz || exit 1
# Start the packaged Spring Boot application in the foreground for container supervision.
ENTRYPOINT ["java", "-jar", "app.jar"]
