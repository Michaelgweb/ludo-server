# Base image
FROM openjdk:17-jdk-alpine

# Set working directory
WORKDIR /app

# Copy project jar (Maven build artifact)
COPY target/ludo-server-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Command to run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
