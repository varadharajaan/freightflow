# ============================================================================
# FreightFlow Tracking Service — Multi-stage Dockerfile
# ============================================================================
# Stage 1: Build with Maven (JDK 21)
# Stage 2: Run with minimal JRE (distroless)
#
# Build: docker build -t freightflow/tracking-service:latest .
# Run:   docker run -p 8082:8082 freightflow/tracking-service:latest
# ============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM files first (layer caching for dependencies)
COPY ../mvnw ./
COPY ../.mvn .mvn
COPY ../pom.xml ./
COPY ../freightflow-bom/pom.xml freightflow-bom/
COPY ../freightflow-parent/pom.xml freightflow-parent/
COPY ../freightflow-commons/pom.xml freightflow-commons/
COPY ../freightflow-commons/commons-domain/pom.xml freightflow-commons/commons-domain/
COPY ../freightflow-commons/commons-events/pom.xml freightflow-commons/commons-events/
COPY ../freightflow-commons/commons-security/pom.xml freightflow-commons/commons-security/
COPY ../freightflow-commons/commons-observability/pom.xml freightflow-commons/commons-observability/
COPY ../freightflow-commons/commons-exception/pom.xml freightflow-commons/commons-exception/
COPY ../freightflow-commons/commons-testing/pom.xml freightflow-commons/commons-testing/
COPY tracking-service/pom.xml tracking-service/

# Download dependencies (cached unless POM changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -ntp -pl tracking-service -am

# Copy source code
COPY ../freightflow-commons freightflow-commons
COPY tracking-service tracking-service

# Build the service JAR (skip tests — tests run in CI, not in Docker build)
RUN ./mvnw package -B -ntp -pl tracking-service -am -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S freightflow && adduser -S freightflow -G freightflow

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/tracking-service/target/*.jar app.jar

# Create logs directory (referenced by logback-spring.xml)
RUN mkdir -p /app/logs && chown -R freightflow:freightflow /app

USER freightflow

# JVM configuration for containers
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.threads.virtual.enabled=true"

EXPOSE 8082

# Health check — uses Spring Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
