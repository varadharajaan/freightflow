# Cache Invalidation Contract — Booking Service

This document defines the cache invalidation contract for the FreightFlow Booking Service.
Every write operation specifies which cache regions are evicted, which keys are affected,
and when eviction occurs relative to the persistence operation.

## Cache Regions

| Region              | Populated By                          | Key Structure      | TTL    | Description                                 |
|---------------------|---------------------------------------|--------------------|--------|---------------------------------------------|
| `bookings`          | `BookingQueryHandler.getBooking()`    | `bookingId` string | 5 min  | Single booking projection by ID             |
| `customerBookings`  | `BookingQueryHandler.getBookingsByCustomer()` | `customerId` string | 5 min  | List of booking projections for a customer  |

## Eviction Contract Per Command

### CreateBookingCommand

| Cache Region        | Eviction Strategy | Key / Scope   | Timing                         |
|---------------------|-------------------|---------------|--------------------------------|
| `bookings`          | **No eviction**   | N/A           | New entry; not previously cached |
| `customerBookings`  | **Clear all**     | All entries   | After persist + event publish  |

**Rationale:** A new booking changes the customer's booking list, so the entire
`customerBookings` region is cleared. The `bookings` cache is unaffected because
the new booking ID has never been queried before.

### ConfirmBookingCommand

| Cache Region        | Eviction Strategy    | Key / Scope       | Timing                         |
|---------------------|----------------------|-------------------|--------------------------------|
| `bookings`          | **Evict by key**     | `cmd.bookingId()` | After persist + event publish  |
| `customerBookings`  | **Clear all**        | All entries        | After persist + event publish  |

**Rationale:** The booking status transitions from `PENDING` to `CONFIRMED`, which
changes both the single-booking projection and any customer-level list that includes
status information.

### CancelBookingCommand

| Cache Region        | Eviction Strategy    | Key / Scope       | Timing                         |
|---------------------|----------------------|-------------------|--------------------------------|
| `bookings`          | **Evict by key**     | `cmd.bookingId()` | After persist + event publish  |
| `customerBookings`  | **Clear all**        | All entries        | After persist + event publish  |

**Rationale:** The booking status transitions to `CANCELLED`, changing both the
single-booking projection and customer-level list views.

## Implementation Notes

### Why Programmatic Eviction (Not @CacheEvict)

The `BookingCommandHandler` uses a single public entry point — `handle(BookingCommand)` —
which dispatches to private methods via Java 21 pattern matching. Spring AOP proxies
do not intercept self-invocations (calls from `handle()` to `handleCreate()`, etc.),
so `@CacheEvict` annotations on the private methods would never fire.

Instead, the handler uses programmatic eviction via `CacheManager`:

```java
private void evictBookingCache(String bookingId) {
    var cache = cacheManager.getCache("bookings");
    if (cache != null) {
        cache.evict(bookingId);
    }
}

private void evictCustomerBookingsCache() {
    var cache = cacheManager.getCache("customerBookings");
    if (cache != null) {
        cache.clear();
    }
}
```

This approach is explicit, testable, and avoids the proxy self-invocation pitfall.

### Eviction Ordering

Cache eviction occurs **after** the aggregate is persisted and events are published
to the outbox, but **within the same `@Transactional` boundary**. This means:

1. If the transaction rolls back, the stale cache entry is harmless (data is unchanged).
2. If the transaction commits, the cache is already evicted — the next read will
   hit the database and see the updated projection.

### Read-Side Caching (BookingQueryHandler)

The read side populates caches via `@Cacheable`:

```java
@Cacheable(value = "bookings", key = "#bookingId", unless = "#result == null")
public BookingView getBooking(String bookingId) { ... }
```

The `customerBookings` cache is populated by `getBookingsByCustomer()` and should be
annotated with `@Cacheable(value = "customerBookings", key = "#customerId")` when
the query volume justifies caching.

## Cache Consistency Guarantees

- **Eventual consistency window:** Between a write committing and the next cache-miss
  read, there is no inconsistency — the cache entry is evicted synchronously within
  the transaction.
- **At-most stale for:** Zero seconds under normal operation. The only risk is if the
  JVM crashes after the database commit but before the in-process cache eviction call.
  In a distributed cache (Redis), eviction is a network call and the window is bounded
  by network RTT.
- **No phantom reads:** Null results are excluded from caching (`unless = "#result == null"`),
  preventing negative caching of not-yet-projected bookings.

---

## Vessel Schedule Service Matrix

Command-side invalidation is centralized in `CentralCacheInvalidationService`.

| Command | `voyages` | `vessel-voyages` | `available-routes` |
|---|---|---|---|
| `reserveCapacity` | Evict by `voyageId` | Evict by `vesselId` | Clear all |
| `releaseCapacity` | Evict by `voyageId` | Evict by `vesselId` | Clear all |
| `departVoyage` | Evict by `voyageId` | Evict by `vesselId` | Clear all |
| `arriveVoyage` | Evict by `voyageId` | Evict by `vesselId` | Clear all |
