# FreightFlow Testing Pyramid (T15)

## Goal

Establish a principal-level testing baseline with clear layering and enforceable quality gates:

- Unit tests (JUnit 5 + Mockito)
- Integration tests (Testcontainers: PostgreSQL, Kafka, Redis, Keycloak)
- Architecture tests (ArchUnit)
- API tests (REST Assured + WireMock + Awaitility)
- Mutation tests (PIT, threshold > 70%)
- Performance tests (Gatling target: p99 < 200ms)

## Current Baseline

### Unit

- Standardized by parent Maven Surefire configuration.
- Naming convention:
  - `*Test.java`
  - `*Tests.java`

### Integration

- Standardized by parent Maven Failsafe configuration.
- Naming convention:
  - `*IntegrationTest.java`
  - `*IT.java`
- Shared support utilities available in:
  - [ContainerizedDependencies.java](C:/Users/vdamotharan/Desktop/freightflow/freightflow-commons/commons-testing/src/main/java/com/freightflow/commons/testing/ContainerizedDependencies.java)

### Architecture

- Shared rules in:
  - [ArchitectureRules.java](C:/Users/vdamotharan/Desktop/freightflow/freightflow-commons/commons-testing/src/main/java/com/freightflow/commons/testing/ArchitectureRules.java)
- Service-specific `ArchitectureTest` classes consume these rules.

### Mutation

- PIT plugin managed in parent POM with mutation threshold set to 70%.
- Use module-focused mutation runs to control CI time.

## Commands

```bash
# Unit + integration + verification
./mvnw test verify

# Architecture tests only
./mvnw test -Dtest="*ArchitectureTest" -Dsurefire.failIfNoSpecifiedTests=false

# Mutation tests (example: booking service)
./mvnw -pl booking-service org.pitest:pitest-maven:mutationCoverage

# Contract tests (enable profile)
./mvnw -Pcontract-tests test

# Performance tests (enable profile, add Gatling simulations per service)
./mvnw -Pperformance-tests verify
```

## Next Rollout (Incremental)

1. Add Spring Cloud Contract producer/consumer contracts per service boundary.
2. Add dedicated Gatling simulations for gateway + booking read paths.
3. Enforce per-service minimum test packs in CI:
   - at least one unit test suite
   - at least one integration slice
   - architecture rules
   - one API test
4. Add trend reporting in CI artifacts for:
   - coverage
   - mutation score
   - p95/p99 latency.
