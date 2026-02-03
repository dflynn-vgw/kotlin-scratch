# Multi-stage Dockerfile for Kotlin Spring Boot SPSC application

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Gradle wrapper and configuration files
COPY gradlew .
COPY gradlew.bat .
COPY gradle/ gradle/
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy build files
COPY app/build.gradle.kts app/
COPY gradle/ gradle/

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY app/src app/src

# Build the application
RUN ./gradlew :app:bootJar --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user
RUN groupadd -r spsc && useradd -r -g spsc spsc

# Copy the JAR from builder
COPY --from=builder /build/app/build/libs/*.jar app.jar

# Create directories for data and bookmarks
RUN mkdir -p /app/data /app/bookmarks && \
    chown -R spsc:spsc /app

USER spsc

# Expose any ports if needed (currently not used in worker mode)
# EXPOSE 8080

# Set Spring profile (application.yml contains all other defaults)
ENV SPRING_PROFILES_ACTIVE=worker

ENTRYPOINT ["java", "-jar", "app.jar"]
