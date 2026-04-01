# ============================================================================
# FreightFlow Vessel Schedule Service — Multi-stage Dockerfile
# ============================================================================
# Stage 1: Build with Maven (JDK 21)
# Stage 2: Run with minimal JRE (distroless)
#
# Build: docker build -f vessel-schedule-service/Dockerfile -t freightflow/vessel-schedule-service:latest .
# Run:   docker run -p 8084:8084 freightflow/vessel-schedule-service:latest
# ============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM files first (layer caching for dependencies)
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY freightflow-bom/pom.xml freightflow-bom/
COPY freightflow-parent/pom.xml freightflow-parent/
COPY freightflow-commons/pom.xml freightflow-commons/
COPY freightflow-commons/commons-domain/pom.xml freightflow-commons/commons-domain/
COPY freightflow-commons/commons-events/pom.xml freightflow-commons/commons-events/
COPY freightflow-commons/commons-security/pom.xml freightflow-commons/commons-security/
COPY freightflow-commons/commons-observability/pom.xml freightflow-commons/commons-observability/
COPY freightflow-commons/commons-exception/pom.xml freightflow-commons/commons-exception/
COPY freightflow-commons/commons-testing/pom.xml freightflow-commons/commons-testing/
COPY discovery-server/pom.xml discovery-server/
COPY config-server/pom.xml config-server/
COPY api-gateway/pom.xml api-gateway/
COPY booking-service/pom.xml booking-service/
COPY tracking-service/pom.xml tracking-service/
COPY billing-service/pom.xml billing-service/
COPY vessel-schedule-service/pom.xml vessel-schedule-service/
COPY customer-service/pom.xml customer-service/
COPY notification-service/pom.xml notification-service/

# Download dependencies (cached unless POM changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -ntp -pl vessel-schedule-service -am

# Copy source code
COPY freightflow-commons freightflow-commons
COPY vessel-schedule-service vessel-schedule-service

# Build the service JAR (skip tests — tests run in CI, not in Docker build)
RUN ./mvnw package -B -ntp -pl vessel-schedule-service -am -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S freightflow && adduser -S freightflow -G freightflow

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/vessel-schedule-service/target/*.jar app.jar

# Create logs directory (referenced by logback-spring.xml)
RUN mkdir -p /app/logs && chown -R freightflow:freightflow /app

USER freightflow

# JVM configuration for containers
ENV JAVA_OPTS="-XX:+UseZGC \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.threads.virtual.enabled=true"

EXPOSE 8084

# Health check — uses Spring Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
