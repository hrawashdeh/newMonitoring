FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the jar file
COPY target/etl-initializer-0.0.1-SNAPSHOT.jar app.jar

# Create directories for file processing
RUN mkdir -p /data/uploads /data/processed /data/failed && \
    chmod 777 /data/uploads /data/processed /data/failed

# Run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown -R appuser:appgroup /app /data
USER appuser

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
