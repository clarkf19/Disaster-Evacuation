# Multi-stage Dockerfile for Spring Boot Backend on Render / Railway
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper & pom.xml first for dependency caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B

# Copy source code & graph data files
COPY src src
COPY data data

# Build executable JAR without running tests
RUN ./mvnw package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy compiled JAR & graph data
COPY --from=builder /app/target/mumbai-evacuation-system-1.0.0.jar app.jar
COPY --from=builder /app/data /app/data

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
