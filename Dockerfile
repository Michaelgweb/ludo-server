# Use Maven + JDK image to build
FROM maven:3.9.3-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the project (skip tests if needed)
RUN mvn clean package -DskipTests

# Use smaller JRE image for runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the jar (Render এর PORT ব্যবহার করে)
ENTRYPOINT ["sh", "-c", "java -Xmx400m -jar app.jar --server.port=${PORT:-8080}"]
