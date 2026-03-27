# ADR-005: Virtual Threads for I/O-Bound Operations

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow's microservices spend the majority of their time waiting on I/O: database queries
(PostgreSQL via HikariCP), Kafka produce/consume, REST calls to other services, and Redis
cache operations. Traditional platform threads (OS threads) are expensive — each consumes
~1 MB of stack memory and is limited by the OS thread scheduler. Under high concurrency
(thousands of simultaneous requests), platform thread pools become the bottleneck, leading
to thread starvation and increased latency.

Java 21 introduced **Virtual Threads** (Project Loom, JEP 444) as a production-ready feature.
Virtual threads are lightweight, JVM-managed threads that unmount from carrier (platform)
threads during blocking I/O operations, allowing a small number of carrier threads to
multiplex millions of virtual threads.

### Requirements
- Handle 10,000+ concurrent requests per service instance during peak freight booking periods
- Reduce tail latency caused by thread pool exhaustion under high concurrency
- Maintain compatibility with existing Spring Boot 3.x and Spring Framework 6.x stack
- No regressions in CPU-bound workload performance (route optimization calculations)
- Minimize code changes — ideally a configuration-level change for most services

### Options Considered
1. **Increase platform thread pool sizes** - Simple but wasteful. Each thread consumes ~1 MB;
   10,000 threads = ~10 GB just for stacks. OS scheduling overhead degrades performance
   beyond a few thousand threads. Does not scale.
2. **Reactive programming (WebFlux / Project Reactor)** - Non-blocking, highly efficient for
   I/O. However, requires a complete rewrite from imperative to reactive style. Reactor's
   `Mono`/`Flux` API has a steep learning curve. Stack traces are unreadable. Debugging is
   painful. Testing is more complex. The entire ecosystem (JPA, JDBC) must be replaced with
   reactive equivalents (R2DBC). Massive migration cost.
3. **Virtual Threads (Project Loom)** - Lightweight threads managed by the JVM. Write
   synchronous, blocking code that behaves asynchronously under the hood. No API changes.
   Compatible with existing JDBC, JPA, and Spring MVC. Spring Boot 3.2+ has first-class
   support via a single configuration property.
4. **Kotlin Coroutines** - Lightweight concurrency via suspend functions. Requires adopting
   Kotlin. Similar benefits to virtual threads but with language migration overhead.

## Decision

We will enable **Virtual Threads** for all I/O-bound microservices in FreightFlow using
Spring Boot's built-in support:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

This single property configures Tomcat to dispatch each incoming HTTP request on a virtual
thread instead of a platform thread from the traditional thread pool.

### When to Use Virtual Threads
Virtual threads are ideal for:
- **HTTP request handling** — each request runs on a virtual thread; blocking on DB/cache/API
  calls yields the carrier thread to other virtual threads
- **Kafka consumers** — blocking poll loops benefit from virtual threads when processing
  involves I/O (database writes, API calls)
- **Database operations** — JDBC calls block the virtual thread but unmount from the carrier
  thread, freeing it for other work
- **REST client calls** — `RestClient` and `WebClient` (in blocking mode) calls to other
  services benefit from virtual thread scheduling
- **Scheduled tasks** — `@Scheduled` I/O-heavy tasks (e.g., polling for saga timeouts) run
  efficiently on virtual threads

### When NOT to Use Virtual Threads
Virtual threads must be **avoided or used with caution** in these scenarios:

1. **CPU-bound operations** — Virtual threads provide no benefit for compute-intensive work
   (e.g., route optimization algorithms, complex tariff calculations). CPU-bound work pins
   the carrier thread regardless. Use a dedicated, bounded `ForkJoinPool` or
   `Executors.newFixedThreadPool()` with platform threads for CPU-intensive tasks:
   ```java
   @Bean("cpuBoundExecutor")
   ExecutorService cpuBoundExecutor() {
       return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
   }
   ```

2. **`synchronized` blocks with I/O inside** — `synchronized` blocks *pin* the virtual
   thread to its carrier thread, preventing unmounting. This negates the benefit. Replace
   `synchronized` with `ReentrantLock`:
   ```java
   // BAD — pins virtual thread
   synchronized (lock) {
       database.query(...); // carrier thread is pinned during I/O
   }

   // GOOD — virtual thread unmounts during I/O
   private final ReentrantLock lock = new ReentrantLock();
   lock.lock();
   try {
       database.query(...); // carrier thread is freed during I/O
   } finally {
       lock.unlock();
   }
   ```

3. **Thread-local heavy code** — Virtual threads are cheap to create but thread-local
   variables are per-thread. If millions of virtual threads each hold large thread-locals,
   memory consumption spikes. Prefer `ScopedValue` (JEP 464, preview) for request-scoped
   data where possible.

4. **Native code / JNI calls** — Native calls that block in OS code pin the carrier thread.
   Isolate these in a dedicated platform thread pool.

5. **Connection pool exhaustion** — Virtual threads allow far more concurrent requests than
   platform threads. If 10,000 virtual threads simultaneously request a database connection
   from a pool of 20, contention shifts to the connection pool. Ensure connection pools,
   rate limiters, and downstream services are sized for the increased concurrency.

### Configuration Details
```yaml
# Tomcat — virtual threads handle all incoming requests
spring:
  threads:
    virtual:
      enabled: true

# HikariCP — sized to handle increased virtual thread concurrency
# (the pool becomes the bottleneck, not the thread count)
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Database connection limit is the real constraint
      connection-timeout: 30000
```

### Performance Benchmarks Expected
We will conduct benchmarks before production rollout comparing:
- **Throughput**: Requests/second under 1K, 5K, 10K concurrent connections
- **Latency**: p50, p95, p99 latency at each concurrency level
- **Memory**: RSS and heap usage comparison (platform threads vs virtual threads)
- **Thread count**: OS thread count under load (expect dramatic reduction)

Preliminary benchmarks from Spring Boot team and community report:
- 5-10× throughput improvement for I/O-bound services at high concurrency
- 50-80% reduction in memory consumption per concurrent connection
- Near-zero improvement for CPU-bound workloads (as expected)

### JVM Monitoring
- Use `-Djdk.tracePinnedThreads=short` in development to detect pinned virtual threads
  (logs a warning when a virtual thread is pinned to its carrier).
- Monitor `jdk.VirtualThreadPinned` JFR events in production.
- Track carrier thread pool utilization via `ForkJoinPool.commonPool()` metrics.

## Consequences

### Positive
- Dramatic scalability improvement for I/O-bound services with a single config change
- No code rewrite required — existing blocking, imperative Spring MVC code works as-is
- Familiar programming model — developers write sequential, blocking code (no reactive
  complexity, no callback hell, no Mono/Flux chains)
- Stack traces remain readable and debuggable (unlike reactive streams)
- Reduces infrastructure cost — fewer service replicas needed to handle peak load
- Spring Boot 3.2+ and Java 21 provide production-ready, first-class support

### Negative
- Requires Java 21+ — all services must be on a supported JDK version
- Risk of carrier thread pinning from `synchronized` blocks in third-party libraries
  (JDBC drivers, logging frameworks) — requires verification
- Connection pool contention replaces thread pool contention as the bottleneck — requires
  careful pool sizing and backpressure
- Thread-local usage patterns may cause unexpected memory growth
- Team must understand virtual thread semantics to avoid anti-patterns
- JFR/monitoring tooling for virtual threads is still maturing

### Mitigations
- Mandate Java 21 as the minimum JDK version across all services (already planned)
- Run `-Djdk.tracePinnedThreads=short` in all non-production environments and add CI
  integration tests that detect pinned threads
- Audit all `synchronized` usage in application code and key libraries (HikariCP, PostgreSQL
  JDBC driver, Logback); replace with `ReentrantLock` where I/O occurs inside the block
- Implement Resilience4j `BulkHead` or Semaphore-based rate limiting to prevent connection
  pool stampedes from virtual thread concurrency surges
- Conduct the defined performance benchmarks in a staging environment that mirrors production
  topology before enabling in production
- Create a team knowledge-sharing session covering virtual thread internals, pinning, and
  anti-patterns

## References
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot - Virtual Threads Support](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
- [Project Loom: Modern Scalable Concurrency for the Java Platform](https://cr.openjdk.org/~rpressler/loom/Loom-Proposal.html)
- [José Paumard - Virtual Threads: Performance Insights](https://inside.java/2024/02/04/sip094/)
- [Brian Goetz - State of Loom](https://cr.openjdk.org/~rpressler/loom/loom/sol1_part1.html)
