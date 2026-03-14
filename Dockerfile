# Multi-stage build for RAG OpenAI API with Ollama
# Stage 1: Builder - Build the application using Gradle wrapper
FROM amazoncorretto:21-alpine-jdk AS builder

# Set working directory
WORKDIR /app

# Copy Gradle wrapper files
COPY gradlew .
COPY gradlew.bat .
COPY gradle/ gradle/

# Copy build configuration files
COPY build.gradle .
COPY settings.gradle .
COPY org/ org/

# Make gradlew executable
RUN chmod +x gradlew

# Copy source code
COPY src/ src/

# Build the application using Gradle wrapper
# Skip tests during Docker build for faster builds
RUN ./gradlew build -i --stacktrace -x test --no-daemon

# Stage 2: Runtime - Create minimal runtime image
FROM amazoncorretto:25.0.0-alpine

# Set working directory
WORKDIR /app

# Create documents directory for document processing
RUN mkdir -p /app/documents

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose application port
EXPOSE 8080

# Add health check using wget
RUN apk add --no-cache wget
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Set JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
