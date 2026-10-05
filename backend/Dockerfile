# Multi-stage Dockerfile for the Spring Boot backend (Render / Railway)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Maven wrapper & pom first so dependency downloads are cached between builds
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x ./mvnw && ./mvnw -B -q dependency:go-offline

# Source (road graph + shelters are packaged from src/main/resources/data)
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Runtime stage — small JRE image, non-root user
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /app/target/mumbai-evacuation-system.jar app.jar

# Faster cold starts (free hosting tiers sleep and restart often):
# 1. unpack the fat jar so classes load from plain jars instead of nested ones;
# 2. do a training start that stops right after the app context is ready and
#    records every loaded class into a Class Data Sharing archive, which the
#    JVM maps straight into memory on later starts.
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
 && java -XX:ArchiveClassesAtExit=extracted/app.jsa -Dspring.context.exit=onRefresh \
         -Ddemo.enabled=false -jar extracted/app.jar \
 && rm app.jar

USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health || exit 1

# SerialGC: lowest overhead on a small single-CPU container.
ENTRYPOINT ["java", "-XX:SharedArchiveFile=extracted/app.jsa", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=75", \
            "-jar", "extracted/app.jar"]
