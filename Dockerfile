# Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Download dependencies first (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN ./gradlew build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create config directory
RUN mkdir -p /app/config

# Copy built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run application with external config location
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.config.location=classpath:/application.yml,file:/app/config/application.yml"]