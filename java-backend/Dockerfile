# Stage 1: Build the Spring Boot application using Gradle and JDK 25
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy gradle wrappers and configuration files
COPY gradle/ gradle/
COPY gradlew gradlew
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle

# Grant execution rights to the gradle wrapper
RUN chmod +x gradlew

# Download dependencies (to leverage Docker layer caching)
RUN ./gradlew dependencies --no-daemon

# Copy source code and build the executable jar (excluding tests for faster builds)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Final lightweight runtime container using JDK 25 JRE
FROM eclipse-temurin:25-jre
WORKDIR /app

# Create a non-privileged system user for running the Spring Boot app safely
RUN useradd -ms /bin/sh spring && \
    chown -R spring:spring /app

USER spring

# Copy the generated spring-boot executable jar from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose Spring Boot's application port
EXPOSE 8080

# Configure production-ready garbage collection and run application
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
