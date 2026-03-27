# ADR-007: Multi-Level Caching Strategy (Caffeine L1 + Redis L2)

## Status

Accepted

## Date

2026-03-27

## Context

FreightFlow services experience significant read amplification on hot data paths. The Booking
service handles repeated lookups for active bookings (dashboard views, status checks). The
Route Optimization service reads frequently accessed port data, tariff tables, and route
templates. The Tracking service queries current shipment positions repeatedly for real-time
dashboards. Direct database queries for every read request create unnecessary load on
PostgreSQL and increase tail latency.

### Requirements
- Sub-millisecond reads for frequently accessed data within the same service instance
- Shared cache visibility across multiple replicas of the same service
- Cache consistency — stale data must be invalidated within an acceptable window (< 5 seconds
  for booking status, < 30 seconds for reference data)
- Resilience — cache failure must not take down the service; fallback to database reads
- Protection against cache stampede (thundering herd) during cache misses on hot keys
- Minimal memory footprint per service instance (L1 must not cause OOM)

### Options Considered
1. **Redis Only (L2)** - Single shared cache layer. Provides cross-replica consistency.
   However, every cache read incurs a network round-trip (~0.5-2 ms). Under high request
   rates, this adds up and creates network pressure. No benefit for repeated reads within
   the same request lifecycle or rapid successive calls within a single instance.
2. **Caffeine Only (L1)** - In-process cache with near-zero latency. No network overhead.
   However, each service replica has its own isolated cache. Cache invalidation across
   replicas requires a separate mechanism. Total memory usage multiplied by replica count.
   Not suitable as a shared session or coordination store.
3. **Multi-Level: Caffeine L1 + Redis L2** - Caffeine serves as a near-cache (L1) within
   each JVM for hot data. Redis serves as a shared distributed cache (L2) across all
   replicas. L1 cache miss falls through to L2; L2 cache miss falls through to the database.
   Cache invalidation events propagated via Kafka ensure L1 caches across replicas converge.
4. **Hazelcast / Apache Ignite** - Distributed in-memory data grid with built-in near-cache.
   Powerful but heavy — adds cluster membership management, split-brain handling, and a
   significant operational footprint. Overkill for our caching needs.

## Decision

We will implement a **multi-level caching strategy** with **Caffeine** as the L1 (in-process)
cache and **Redis** as the L2 (distributed) cache. Cache invalidation across service replicas
will be driven by **Kafka events**.

### Cache Architecture

```
[Request] → [Caffeine L1] → HIT → return
                 ↓ MISS
            [Redis L2] → HIT → populate L1, return
                 ↓ MISS
            [PostgreSQL] → populate L2, populate L1, return
```

### Caffeine L1 Configuration
- **Eviction policy**: Window-TinyLFU (Caffeine's default) — near-optimal hit rate combining
  frequency and recency.
- **Maximum size**: Bounded by entry count per cache region to prevent OOM.
- **TTL**: Short time-to-live (30-120 seconds) to limit staleness window.

```java
@Bean
public CacheManager caffeineCacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofSeconds(60))
        .recordStats());  // Enable metrics via Micrometer
    return manager;
}
```

### Redis L2 Configuration
- **Serialization**: JSON (Jackson) for debuggability; MessagePack for high-throughput
  telemetry caches.
- **Connection**: Lettuce client with connection pooling (default in Spring Boot).
- **Cluster mode**: Redis Cluster with 3 masters and 3 replicas for production.

```yaml
spring:
  data:
    redis:
      cluster:
        nodes: ${REDIS_CLUSTER_NODES}
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
      timeout: 2000ms
```

### TTL Strategy by Data Type

| Cache Region           | L1 TTL     | L2 TTL     | Max L1 Size | Rationale                              |
|------------------------|------------|------------|-------------|----------------------------------------|
| `booking-active`       | 30 sec     | 5 min      | 5,000       | Frequently updated, staleness sensitive |
| `booking-completed`    | 5 min      | 1 hour     | 10,000      | Immutable once completed               |
| `port-reference`       | 10 min     | 24 hours   | 2,000       | Slowly changing reference data          |
| `tariff-tables`        | 5 min      | 1 hour     | 1,000       | Changes with schedule updates          |
| `route-templates`      | 10 min     | 6 hours    | 500         | Rarely changes                          |
| `tracking-current`     | 10 sec     | 1 min      | 20,000      | Rapidly changing telemetry data        |
| `user-permissions`     | 2 min      | 15 min     | 5,000       | Security-sensitive, moderate churn     |

### Cache Invalidation via Kafka Events
- When a booking is updated, the Booking service publishes a `BookingUpdatedEvent` to
  `freightflow.booking.events` (this happens regardless of caching — see ADR-001).
- All Booking service replicas consume from this topic and invalidate the corresponding
  L1 Caffeine entry upon receiving the event.
- The publishing service also invalidates the L2 Redis entry immediately (synchronous
  delete after database write).
- **Flow**:
  ```
  Service A (writer):
    1. Write to PostgreSQL
    2. Delete from Redis L2 (immediate)
    3. Publish event to Kafka

  Service A, B, C (all replicas — consumers):
    4. Receive Kafka event
    5. Invalidate Caffeine L1 entry
  ```
- This ensures **L2 is invalidated immediately** by the writer, and **L1 across all replicas
  converges within Kafka consumer lag** (typically < 100 ms).

### Stampede Prevention
Cache stampede occurs when a popular key expires and hundreds of concurrent requests
simultaneously miss the cache and hit the database.

Mitigations:
1. **Caffeine's `refreshAfterWrite`** — Triggers an asynchronous refresh before expiry.
   Only one thread refreshes; others get the stale value:
   ```java
   Caffeine.newBuilder()
       .maximumSize(10_000)
       .expireAfterWrite(Duration.ofSeconds(120))
       .refreshAfterWrite(Duration.ofSeconds(90))  // Refresh before expiry
       .build(key -> loadFromRedisOrDatabase(key));
   ```
2. **Redis distributed lock for L2 misses** — When L2 misses, acquire a short-lived Redis
   lock (`SET key NX EX 5`). Only the lock holder queries the database; other requests wait
   or get a stale fallback value.
3. **Probabilistic early expiration** — For extremely hot keys, apply an XFetch-style
   algorithm where each request has a small probability of refreshing the cache before TTL,
   distributing refresh load over time.

### Resilience
- If Redis is unavailable, the service falls back to Caffeine L1 + direct database reads.
  Redis operations are wrapped with a Resilience4j `CircuitBreaker` (failure threshold: 50%,
  wait duration: 30 seconds).
- If Caffeine L1 is full, the LFU eviction policy evicts the least valuable entries
  automatically.
- Cache reads never throw exceptions to the caller — all cache failures are logged and
  bypassed transparently.

### Monitoring
- Caffeine stats exposed via Micrometer: `cache.gets`, `cache.puts`, `cache.evictions`,
  `cache.hit.ratio`.
- Redis metrics via `redis-exporter`: `redis_connected_clients`, `redis_memory_used_bytes`,
  `redis_keyspace_hits`, `redis_keyspace_misses`.
- Alert on L1 hit ratio dropping below 80% (indicates TTL misconfiguration or workload
  change).
- Alert on Redis latency p99 exceeding 5 ms.

## Consequences

### Positive
- Sub-millisecond reads for hot data via Caffeine L1 — no network round-trip
- Shared L2 cache (Redis) provides cross-replica consistency for warm data
- Kafka-driven invalidation ensures L1 caches converge quickly without polling
- Stampede prevention protects the database from thundering herd on hot key expiry
- Resilience — service continues to function (with degraded latency) if Redis is down
- Per-region TTL strategy matches data volatility, optimizing hit rates while bounding
  staleness

### Negative
- Two cache layers add complexity to data flow and debugging (which layer has stale data?)
- Cache invalidation via Kafka introduces a small consistency window (Kafka consumer lag)
- Redis adds an infrastructure dependency to deploy, monitor, and operate
- Memory pressure — Caffeine consumes JVM heap; requires careful sizing to avoid GC pressure
- Cache warming on cold starts (new deployment, new replica) causes a temporary latency
  spike as L1 is empty

### Mitigations
- Implement cache-related logging with correlation IDs showing cache layer hit/miss per
  request for debugging: `[L1:MISS, L2:HIT, source=redis]`
- Set L1 TTL conservatively short (30-120 seconds) to bound the staleness window
- Use Redis Cluster with automatic failover; wrap Redis calls in circuit breakers
- Size Caffeine caches based on load testing and heap profiling; monitor GC pause times
- Implement cache pre-warming on startup for critical reference data (ports, tariffs) by
  loading from the database into both L1 and L2 during the `ApplicationReadyEvent`
- Create a shared `freightflow-cache-starter` that encapsulates the multi-level caching
  pattern, invalidation wiring, and monitoring configuration

## References
- [Caffeine - A High Performance Caching Library](https://github.com/ben-manes/caffeine)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Redis Cluster Specification](https://redis.io/docs/reference/cluster-spec/)
- [Cache Stampede Prevention — XFetch Algorithm](https://cseweb.ucsd.edu/~avattani/papers/cache_stampede.pdf)
- [Martin Kleppmann - Designing Data-Intensive Applications, Ch. 5](https://dataintensive.net/)
