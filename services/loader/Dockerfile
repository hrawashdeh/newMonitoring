FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the jar file
COPY target/loader-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]