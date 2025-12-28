# Multi-stage Dockerfile for Spring Cloud Gateway Service
# Stage 1: Build stage using Maven
# Stage 2: Runtime stage with JRE only

# ============================================================
# Stage 1: Builder - Compile and package the application
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

LABEL maintainer="Hassan Rawashdeh <hassan@tiqmo.sa>"
LABEL stage=builder

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy Maven configuration and source code
COPY pom.xml .
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests && \
    mv target/gateway-service-*.jar target/gateway-service.jar

# Verify JAR was created
RUN ls -lh target/gateway-service.jar

# ============================================================
# Stage 2: Runtime - Create minimal runtime image
# ============================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Hassan Rawashdeh <hassan@tiqmo.sa>"
LABEL service=gateway-service
LABEL description="API Gateway for ETL Monitoring Platform - Bank-Grade Security"

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

# Copy JAR from builder stage
COPY --from=builder /app/target/gateway-service.jar app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring

# Expose Gateway port
EXPOSE 8888

# Health check using actuator endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# JVM tuning for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
