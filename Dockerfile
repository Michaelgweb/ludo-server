# Base image
FROM openjdk:17-jdk-alpine

# Set working directory inside container
WORKDIR /app

# Copy the jar file into container
COPY target/ludo-server-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
