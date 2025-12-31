FROM eclipse-temurin:21-jre-jammy

# Set working directory
WORKDIR /app

# Create directories for file storage
RUN mkdir -p /app/imports /app/imports/errors && \
    chmod -R 777 /app/imports

# Copy the jar file
COPY target/*.jar app.jar

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
