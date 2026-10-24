# Build the runnable JAR before building this image, for example:
#   mvn clean package
# Then build from the repository root with:
#   docker compose build app
#
# This image packages the already-built application; it does not run Maven.
# Keep the Java major version aligned with the version configured in pom.xml.
FROM eclipse-temurin:25-jdk-alpine

# Keep the application files together in a predictable working directory.
WORKDIR /app

# Copy Maven's packaged Spring Boot JAR into the image under a stable name.
# The build context must contain exactly one matching JAR in target/.
COPY target/*.jar app.jar

# Run the executable Spring Boot JAR when the container starts.
ENTRYPOINT ["java", "-jar", "app.jar"]
