# Multi-stage image for the Libro API.
#   Stage 1 (build):   compile and package with the JDK and the Maven wrapper.
#   Stage 2 (runtime): a JRE-only image holding the application split into layers, run as a non-root user.
# Build: docker build -t libro-api .   (BuildKit, the default builder since Docker 23, is required for --mount.)
# Base images are pinned by digest for reproducible builds; Dependabot proposes tag and digest updates.

# ---------------------------------------------------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS build
WORKDIR /build

# Build inputs only: the wrapper, the POM, and the sources. .dockerignore keeps secrets, docs, and target/ out.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# --mount=type=cache keeps ~/.m2 between local builds without baking it into an image layer. That replaces the
# old `dependency:go-offline` step, which also misses plugins resolved later in the build.
# Tests are skipped here because CI has already run them on this commit before any image is built.
# The extract step splits the fat JAR into four directories, ordered from least to most often changed. Each one
# becomes its own image layer below, so a code-only change ships a small `application` layer and reuses the rest.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw -B -ntp -DskipTests package \
    && cp target/library-api-system-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
WORKDIR /app

# A dedicated system account: a compromised API process gets no root privileges.
# libexpat is upgraded for a fixed Alpine CVE until the base image digest includes the patch.
RUN addgroup -S libro && adduser -S -G libro libro \
    && apk upgrade --no-cache libexpat

# Least to most frequently changed, matching the extract order above.
COPY --from=build --chown=libro:libro /build/extracted/dependencies/ ./
COPY --from=build --chown=libro:libro /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=libro:libro /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=libro:libro /build/extracted/application/ ./

# MaxRAMPercentage   size the heap from the container memory limit, not the host's RAM.
# ExitOnOutOfMemoryError   exit on heap exhaustion so the restart policy replaces a half-dead JVM.
# user.timezone   stored LocalDateTime values use the library's Asia/Manila wall clock (see LibraryTime).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Manila"

USER libro
# HTTP API and public probes. The Actuator management port (9091) is deliberately not exposed.
EXPOSE 8080

# The one health check for this image; Compose and the deploy scripts rely on it rather than defining their own.
# Probe /readyz (which includes the database) every 30s; allow 60s for Flyway migrations on first start, then
# mark the container unhealthy after three consecutive failures of 5s each.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/readyz || exit 1

# Exec form: java runs as PID 1 and receives SIGTERM directly, so graceful shutdown works.
# application.jar's manifest points at the extracted lib/ directory, so `java -jar` still works on the layers.
ENTRYPOINT ["java", "-jar", "application.jar"]
