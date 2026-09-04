# Use lightweight Java 21 runtime image
FROM eclipse-temurin:21-jre-alpine

# App working directory inside container
WORKDIR /app

# Copy built jar from Maven target output
COPY target/team-skeleton.jar app.jar

# Start the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]