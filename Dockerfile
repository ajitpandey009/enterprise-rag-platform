# ============================================================
# Enterprise RAG Platform — Backend Dockerfile
# Multi-stage build: Maven build → JRE runtime
# ============================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and POM first for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B 2>/dev/null || true

# Copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create non-root user for security
RUN groupadd -r raguser && useradd -r -g raguser raguser

COPY --from=builder /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER raguser

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx512m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
