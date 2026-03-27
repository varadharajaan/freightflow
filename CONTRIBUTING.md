# Contributing to FreightFlow

## Coding Standards

This project adheres to **Principal Engineer-level** coding standards. All contributions must follow these guidelines.

### Java Style Guide

- **Java 21 features** are mandatory where applicable (Records, Sealed Classes, Pattern Matching, Virtual Threads)
- **Immutability by default** - use `final` fields, Records for DTOs, unmodifiable collections
- **No raw types** - always use generics
- **No `null` returns** - use `Optional<T>` for potentially absent values
- **No field injection** - use constructor injection only (with `final` fields)

### SOLID Principles

Every class and method must adhere to SOLID principles:

```java
// GOOD - Single Responsibility + Dependency Inversion
public class BookingCommandHandler {

    private final BookingRepository bookingRepository;
    private final EventPublisher eventPublisher;
    private final BookingValidator validator;

    // Constructor injection - no @Autowired on fields
    public BookingCommandHandler(BookingRepository bookingRepository,
                                  EventPublisher eventPublisher,
                                  BookingValidator validator) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.validator = validator;
    }
}

// GOOD - Open/Closed via Strategy Pattern
public sealed interface PricingStrategy permits
    StandardPricing, VolumePricing, ContractPricing {
    Money calculatePrice(BookingRequest request);
}
```

### Logging

```java
// GOOD - Structured logging with MDC
@Slf4j
public class BookingService {

    public Booking createBooking(CreateBookingCommand command) {
        log.debug("Creating booking for customer={}, route={}->{}",
            command.customerId(), command.origin(), command.destination());

        try {
            var booking = bookingFactory.create(command);
            log.info("Booking created successfully bookingId={}, customerId={}",
                booking.id(), command.customerId());
            return booking;
        } catch (InsufficientCapacityException e) {
            log.warn("Booking failed due to insufficient capacity vesselId={}, requestedTEU={}",
                e.vesselId(), e.requestedTeu());
            throw e;
        }
    }
}
```

### Exception Handling

```java
// Domain exception hierarchy
public sealed class BookingException extends RuntimeException
    permits BookingNotFoundException, BookingValidationException,
            BookingConflictException, InsufficientCapacityException {
    // ...
}

// Global exception handler
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(BookingNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Booking Not Found");
        problem.setType(URI.create("https://api.freightflow.com/problems/booking-not-found"));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
```

### Testing

```java
// BDD-style naming
@Test
void should_CreateBooking_When_ValidCommandProvided() { ... }

@Test
void should_ThrowBookingNotFoundException_When_BookingDoesNotExist() { ... }

@Test
void should_PublishBookingCreatedEvent_When_BookingSucceeds() { ... }
```

### Git Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(booking): add container allocation to booking flow
fix(tracking): resolve race condition in position updates
refactor(billing): extract invoice generation to strategy pattern
docs(adr): add ADR-007 for caching strategy
test(booking): add integration tests for booking saga
chore(deps): upgrade Spring Boot to 3.3.1
```

### Pull Request Checklist

- [ ] Code follows SOLID principles
- [ ] Proper logging at all levels (no sensitive data)
- [ ] Custom exceptions with proper hierarchy
- [ ] Unit tests with descriptive BDD-style names
- [ ] Integration tests with Testcontainers where needed
- [ ] Javadoc on public APIs
- [ ] No TODO/FIXME without a linked issue
- [ ] Flyway migration (if DB changes)
- [ ] OpenAPI spec updated (if API changes)
- [ ] ADR written (if architectural decision)
