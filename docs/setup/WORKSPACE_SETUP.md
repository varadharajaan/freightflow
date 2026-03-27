# FreightFlow - Development Workspace Setup

## Prerequisites

Before setting up the project, ensure you have the following installed:

| Tool | Version | Install |
|---|---|---|
| **Java** | 21 (LTS) | [Adoptium Temurin](https://adoptium.net/) or [GraalVM](https://www.graalvm.org/) |
| **Maven** | 3.9+ | Included via `mvnw` (Maven Wrapper) |
| **Docker** | 24+ | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| **Docker Compose** | 2.20+ | Bundled with Docker Desktop |
| **Git** | 2.40+ | [git-scm.com](https://git-scm.com/) |
| **IDE** | IntelliJ IDEA 2024.1+ | [JetBrains](https://www.jetbrains.com/idea/) (recommended) |
| **kubectl** | 1.28+ | [kubernetes.io](https://kubernetes.io/docs/tasks/tools/) (optional, for K8s) |
| **Helm** | 3.14+ | [helm.sh](https://helm.sh/docs/intro/install/) (optional, for K8s) |
| **Terraform** | 1.7+ | [terraform.io](https://developer.hashicorp.com/terraform/install) (optional, for IaC) |

### Verify Java Installation
```bash
java -version
# Expected: openjdk version "21.x.x"

javac -version
# Expected: javac 21.x.x

echo $JAVA_HOME
# Should point to JDK 21 installation
```

---

## Quick Start (5 Minutes)

### 1. Clone the Repository
```bash
git clone https://github.com/varadharajaan/freightflow.git
cd freightflow
```

### 2. Start Infrastructure
```bash
# Start PostgreSQL, Kafka, Redis, Keycloak
docker-compose -f infrastructure/docker/docker-compose.yml up -d

# Verify all containers are running
docker-compose -f infrastructure/docker/docker-compose.yml ps
```

### 3. Build the Project
```bash
# Build all modules (skip tests for first build)
./mvnw clean install -DskipTests

# Build with tests
./mvnw clean verify
```

### 4. Run a Service
```bash
# Run booking service
./mvnw -pl booking-service spring-boot:run -Dspring-boot.run.profiles=local

# Run with virtual threads enabled
./mvnw -pl booking-service spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Dspring.threads.virtual.enabled=true"
```

### 5. Access Services
| Service | URL | Credentials |
|---|---|---|
| Booking Service API | http://localhost:8081/api/v1/bookings | JWT token |
| Swagger UI | http://localhost:8081/swagger-ui.html | - |
| Keycloak Admin | http://localhost:8180/admin | admin / admin |
| Kafka UI | http://localhost:8090 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Jaeger UI | http://localhost:16686 | - |
| pgAdmin | http://localhost:5050 | admin@freightflow.com / admin |
| Redis Commander | http://localhost:8087 | - |

---

## IDE Setup (IntelliJ IDEA)

### 1. Import Project
- File -> Open -> Select the `freightflow` root directory
- IntelliJ will auto-detect the Maven project

### 2. Configure JDK
- File -> Project Structure -> Project
- Set SDK to JDK 21 (Temurin or GraalVM)
- Set Language Level to "21 - Record patterns, pattern matching for switch"

### 3. Enable Annotation Processing
- Settings -> Build -> Compiler -> Annotation Processors
- Check "Enable annotation processing"
- Required for: Lombok, MapStruct, Immutables

### 4. Recommended Plugins
| Plugin | Purpose |
|---|---|
| **Lombok** | Annotation support |
| **MapStruct Support** | MapStruct navigation |
| **Docker** | Docker integration |
| **Kubernetes** | K8s manifest support |
| **Database Tools** | SQL and database management |
| **PlantUML Integration** | Diagram rendering |
| **SonarLint** | Real-time code quality |
| **CheckStyle-IDEA** | Code style enforcement |

### 5. Run Configurations
Create these run configurations for each service:

```
Name: BookingService [local]
Type: Spring Boot
Main class: com.freightflow.booking.BookingServiceApplication
Active profiles: local
VM options: -Dspring.threads.virtual.enabled=true
Environment variables: SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/freightflow_booking
```

---

## Environment Profiles

| Profile | Purpose | Config Source |
|---|---|---|
| `local` | Local development with Docker Compose | `application-local.yml` |
| `test` | Integration testing with Testcontainers | `application-test.yml` |
| `dev` | Development environment (cloud) | Spring Cloud Config Server |
| `staging` | Pre-production validation | Spring Cloud Config Server |
| `prod` | Production | Spring Cloud Config Server + Vault |

---

## Port Allocation

> **Note:** Currently only `booking-service` (port 8081) is runnable. Other service ports are reserved for future implementation.

| Service | Port | Debug Port |
|---|---|---|
| API Gateway | 8080 | 5080 |
| Booking Service | 8081 | 5081 |
| Tracking Service | 8082 | 5082 |
| Billing Service | 8083 | 5083 |
| Vessel Schedule Service | 8084 | 5084 |
| Customer Service | 8085 | 5085 |
| Notification Service | 8086 | 5086 |
| Config Server | 8888 | 5088 |
| Eureka Server | 8761 | 5761 |
| PostgreSQL | 5432 | - |
| Kafka | 9092 | - |
| Schema Registry | 8091 | - |
| Redis | 6379 | - |
| Keycloak | 8180 | - |

### Infrastructure UIs (Development Only)
| UI Tool | Port |
|---|---|
| Swagger UI (per service) | Same as service port + `/swagger-ui.html` |
| Kafka UI | 8090 |
| pgAdmin | 5050 |
| Redis Commander | 8087 |
| Grafana | 3000 |
| Prometheus | 9090 |
| Jaeger UI | 16686 |
| Kiali (Istio) | 20001 |
| Eureka Dashboard | 8761 |

> **Note:** Currently only `booking-service` (port 8081) is runnable. Other service ports are reserved for future implementation.

---

## Common Maven Commands

```bash
# Build everything
./mvnw clean install

# Build a specific module
./mvnw -pl booking-service clean install

# Run tests only
./mvnw test

# Run integration tests
./mvnw verify -P integration-tests

# Run with specific test
./mvnw test -pl booking-service -Dtest=BookingServiceTest

# Generate code coverage report
./mvnw verify jacoco:report

# Run checkstyle
./mvnw checkstyle:check

# Run SpotBugs
./mvnw spotbugs:check

# Run mutation testing (PIT)
./mvnw pitest:mutationCoverage

# Run architecture tests
./mvnw test -pl booking-service -Dtest=ArchitectureTest

# Build Docker image (Jib)
./mvnw -pl booking-service jib:dockerBuild

# Generate OpenAPI spec
./mvnw -pl booking-service springdoc-openapi:generate
```

---

## Database Management

### Connect to PostgreSQL
```bash
# Via Docker
docker exec -it freightflow-postgres psql -U freightflow -d freightflow_booking

# Via psql (if installed locally)
psql -h localhost -p 5432 -U freightflow -d freightflow_booking
```

### Run Flyway Migrations

> **Note:** The booking-service currently has 3 migrations: V1 (bookings table), V2 (audit columns), V3 (event store + projections).

```bash
# Migrations run automatically on service startup
# To run manually:
./mvnw -pl booking-service flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/freightflow_booking

# View migration status
./mvnw -pl booking-service flyway:info
```

### Reset Database (Development Only)
```bash
./mvnw -pl booking-service flyway:clean flyway:migrate
```

---

## Kafka Management

### Access Kafka
```bash
# List topics
docker exec -it freightflow-kafka kafka-topics --list --bootstrap-server localhost:9092

# Create a topic
docker exec -it freightflow-kafka kafka-topics --create \
  --topic booking.events \
  --partitions 12 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092

# Consume messages
docker exec -it freightflow-kafka kafka-console-consumer \
  --topic booking.events \
  --from-beginning \
  --bootstrap-server localhost:9092
```

---

## Troubleshooting

### Port Already in Use
```bash
# Find process using port 8081
lsof -i :8081    # Linux/Mac
netstat -ano | findstr :8081    # Windows

# Kill process
kill -9 <PID>    # Linux/Mac
taskkill /PID <PID> /F    # Windows
```

### Docker Compose Issues
```bash
# Reset all containers
docker-compose -f infrastructure/docker/docker-compose.yml down -v
docker-compose -f infrastructure/docker/docker-compose.yml up -d

# View logs
docker-compose -f infrastructure/docker/docker-compose.yml logs -f booking-db
```

### Maven Build Failures
```bash
# Clear local Maven cache
rm -rf ~/.m2/repository/com/freightflow

# Build with debug output
./mvnw clean install -X

# Skip a specific module if needed: ./mvnw clean install -pl '!module-name'
```
