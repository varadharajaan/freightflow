# FreightFlow - Caching Strategy

## Overview

FreightFlow implements a **multi-level caching strategy** to minimize database load,
reduce latency, and handle high read throughput for booking queries and tracking data.

---

## Cache Levels

```
Request --> L1 (Caffeine)  --> L2 (Redis)  --> L3 (PostgreSQL)
            In-process          Distributed      Source of truth
            ~1ms                ~5ms             ~20-50ms
            Per-pod             Shared cluster   Persistent
```

| Level | Technology | Scope | TTL | Use Case |
|---|---|---|---|---|
| **L1** | Caffeine | Per JVM instance | 5 min | Hot data, reference data, config |
| **L2** | Redis 7 | Shared across pods | 15-60 min | Session data, booking views, vessel schedules |
| **L3** | PostgreSQL | Source of truth | Permanent | All domain data |

---

## Caching Patterns Used

### 1. Cache-Aside (Lazy Loading)
Default pattern for most read operations.

```java
@Cacheable(value = "bookings", key = "#bookingId", unless = "#result == null")
public BookingView getBooking(String bookingId) {
    return bookingReadRepository.findById(bookingId)
        .orElseThrow(() -> new BookingNotFoundException(bookingId));
}
```

**Flow**:
```
1. Check cache -> HIT: return cached value
2. MISS: Query database
3. Store result in cache
4. Return result
```

### 2. Write-Through (for critical data)
Used when consistency is more important than write performance.

```java
@CachePut(value = "bookings", key = "#result.bookingId()")
public BookingView confirmBooking(String bookingId) {
    var booking = bookingRepository.findById(bookingId);
    booking.confirm();
    bookingRepository.save(booking);
    return BookingView.from(booking);  // cached immediately
}
```

### 3. Cache Eviction via Kafka Events
When a service modifies data, it publishes an event. Other services
evict their caches based on the event.

```java
@KafkaListener(topics = "booking.events")
public void onBookingEvent(BookingEvent event) {
    cacheManager.getCache("bookings").evict(event.bookingId());
    log.info("Cache evicted for bookingId={} due to event={}",
        event.bookingId(), event.eventType());
}
```

---

## Cache Configuration

### Caffeine (L1 - In-Process)
```yaml
spring:
  cache:
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=5m,recordStats
```

```java
@Configuration
public class CaffeineCacheConfig {

    @Bean
    public CacheManager caffeineCacheManager() {
        var manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats());
        return manager;
    }
}
```

### Redis (L2 - Distributed)
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 5
  cache:
    redis:
      time-to-live: 15m
      cache-null-values: false
      key-prefix: "freightflow:"
```

---

## Cache Stampede Prevention

When a popular cache key expires, multiple concurrent requests hit the database
simultaneously. We prevent this using **Redisson distributed locks**:

```java
public BookingView getBookingWithStampedePrevention(String bookingId) {
    var cached = redisTemplate.opsForValue().get(cacheKey(bookingId));
    if (cached != null) return cached;

    var lock = redissonClient.getLock("lock:booking:" + bookingId);
    try {
        if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
            // Double-check after acquiring lock
            cached = redisTemplate.opsForValue().get(cacheKey(bookingId));
            if (cached != null) return cached;

            var booking = bookingReadRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
            redisTemplate.opsForValue().set(cacheKey(bookingId), booking, Duration.ofMinutes(15));
            return booking;
        }
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
    throw new ServiceUnavailableException("Could not acquire cache lock");
}
```

---

## What Gets Cached (Per Service)

### Booking Service
| Data | Cache Level | TTL | Eviction Trigger |
|---|---|---|---|
| Booking views (read model) | L1 + L2 | 15 min | BookingUpdated event |
| Booking history | L2 | 30 min | New event appended |
| Port reference data | L1 | 60 min | Scheduled refresh |

### Tracking Service
| Data | Cache Level | TTL | Eviction Trigger |
|---|---|---|---|
| Latest container position | L2 (Redis) | 30 sec | New tracking event |
| Container route | L1 + L2 | 5 min | Route update event |
| Vessel positions | L2 | 15 sec | AIS data feed |

### Vessel Schedule Service
| Data | Cache Level | TTL | Eviction Trigger |
|---|---|---|---|
| Active schedules | L1 + L2 | 30 min | Schedule updated event |
| Port-to-port routes | L1 | 60 min | Route change |
| Vessel details | L1 | 60 min | Vessel update |

### Customer Service
| Data | Cache Level | TTL | Eviction Trigger |
|---|---|---|---|
| Customer profiles | L2 | 30 min | Profile updated |
| Customer contracts | L2 | 60 min | Contract updated |
| Roles & permissions | L1 + L2 | 15 min | Role change event |

---

## Cache Monitoring

### Metrics Exposed (Micrometer)
```
cache_gets_total{cache="bookings", result="hit"}
cache_gets_total{cache="bookings", result="miss"}
cache_evictions_total{cache="bookings"}
cache_size{cache="bookings"}
cache_puts_total{cache="bookings"}
```

### Grafana Dashboard Panels
1. **Cache Hit Ratio** - Target: > 85% for L1, > 90% for L2
2. **Cache Size** - Monitor for memory pressure
3. **Eviction Rate** - Spike = configuration issue
4. **Redis Connection Pool** - Active vs idle connections
5. **Cache Latency** - L1 < 1ms, L2 < 5ms

### Alerts
| Alert | Condition | Severity |
|---|---|---|
| Low cache hit ratio | < 70% for 10 min | Warning |
| Redis connection exhausted | Active >= max-active | Critical |
| Cache eviction spike | 5x normal rate for 5 min | Warning |
| Redis latency high | p99 > 50ms for 5 min | Warning |
