# syntax=docker/dockerfile:1

# ------------------------------------------------------------------------------
# Build Stage: Compile and package the application
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy POM and download dependencies offline for efficient layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy project source code and build executable jar (skipping tests)
COPY src ./src
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# Runtime Stage: Lightweight minimal JRE environment
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the packaged Spring Boot jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose default application port (Render routes PORT to this or dynamic port)
EXPOSE 8080

# Configure JVM flags and launch the application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
