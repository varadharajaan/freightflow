# FreightFlow — Microservices Architecture Guide

## Bounded Contexts & Domain Ownership

Each microservice owns a single **bounded context** from Domain-Driven Design.
They communicate via **events (Kafka)** for async operations and **REST/gRPC**
for synchronous queries. No service directly accesses another service's database.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          FreightFlow Platform                                │
│                                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐     │
│  │  Booking    │  │  Tracking   │  │  Billing    │  │ Vessel Schedule  │     │
│  │  Context    │  │  Context    │  │  Context    │  │ Context          │     │
│  │             │  │             │  │             │  │                  │     │
│  │ • Booking   │  │ • Container │  │ • Invoice   │  │ • Vessel         │     │
│  │ • Cargo     │  │ • Movement  │  │ • Payment   │  │ • Voyage         │     │
│  │ • Quote     │  │ • Position  │  │ • Ledger    │  │ • Route          │     │
│  │ • Document  │  │ • Milestone │  │ • CreditNote│  │ • Schedule       │     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬─────────┘     │
│         │                │                │                  │               │
│  ┌──────┴──────┐  ┌──────┴──────┐                                            │
│  │  Customer   │  │Notification │                                            │
│  │  Context    │  │  Context    │                                            │
│  │             │  │             │                                            │ 
│  │ • Customer  │  │ • Template  │                                            │
│  │ • Contract  │  │ • Channel   │                                            │
│  │ • Role      │  │ • Delivery  │                                            │
│  └─────────────┘  └─────────────┘                                            │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Service Descriptions

### 1. booking-service (Port 8081)
**Bounded Context:** Booking Management
**Responsibility:** Full lifecycle of cargo bookings — create, confirm, amend, cancel, track state

| Aspect | Detail |
|---|---|
| **Domain Model** | Booking (aggregate root), Cargo (value object), ContainerType, BookingStatus |
| **Patterns** | CQRS, Event Sourcing, Factory Method, State Machine, Sealed Types |
| **Database** | `freightflow_booking` — bookings, booking_events, booking_projections |
| **Kafka Topics** | Produces: `booking.events` / Consumes: `billing.events`, `vessel.events` |
| **API** | POST/GET/DELETE `/api/v1/bookings`, POST `/{id}/confirm` |

### 2. tracking-service (Port 8082)
**Bounded Context:** Container Tracking & Logistics Visibility
**Responsibility:** Real-time container position tracking, milestone events, geofencing alerts

| Aspect | Detail |
|---|---|
| **Domain Model** | Container (aggregate), Movement (entity), Position (value object), Milestone (entity), TrackingStatus |
| **Patterns** | Kafka Streams (real-time processing), Observer (WebSocket push), Strategy (position source) |
| **Database** | `freightflow_tracking` — containers, movements, milestones (time-partitioned) |
| **Kafka Topics** | Produces: `tracking.events` / Consumes: `booking.events` (new booking → start tracking) |
| **API** | GET `/api/v1/tracking/containers/{id}`, WebSocket `/ws/tracking`, SSE `/api/v1/tracking/stream` |
| **Special** | Kafka Streams for windowed aggregation, WebSocket for live updates, Virtual Threads for high-concurrency I/O |

### 3. billing-service (Port 8083)
**Bounded Context:** Financial Operations — Invoicing, Payments, Ledger
**Responsibility:** Generate invoices from confirmed bookings, process payments, manage refunds

| Aspect | Detail |
|---|---|
| **Domain Model** | Invoice (aggregate), LineItem (entity), Payment (entity), CreditNote (entity), LedgerEntry (value object) |
| **Patterns** | Saga (booking+billing transaction), Double-Entry Ledger, Strategy (pricing), State Machine (invoice lifecycle) |
| **Database** | `freightflow_billing` — invoices, line_items, payments, ledger_entries |
| **Kafka Topics** | Produces: `billing.events` / Consumes: `booking.events` (confirmed → generate invoice) |
| **API** | GET `/api/v1/billing/invoices`, POST `/api/v1/billing/payments`, GET `/api/v1/billing/ledger` |
| **Special** | Saga orchestration with compensating transactions, idempotent payment processing |

### 4. customer-service (Port 8085)
**Bounded Context:** Customer Identity & Contract Management
**Responsibility:** Customer profiles, contracts, credit limits, RBAC roles

| Aspect | Detail |
|---|---|
| **Domain Model** | Customer (aggregate), Contract (entity), CreditLimit (value object), CustomerRole (enum) |
| **Patterns** | RBAC, Multi-tenancy (row-level security), Specification (customer search), Builder (customer registration) |
| **Database** | `freightflow_customer` — customers, contracts, customer_roles |
| **Kafka Topics** | Produces: `customer.events` / Consumes: `booking.events` (update booking count) |
| **API** | CRUD `/api/v1/customers`, GET `/api/v1/customers/{id}/contracts`, GET `/api/v1/customers/{id}/credit` |
| **Special** | OAuth2/Keycloak integration, row-level security, contract validation |

### 5. vessel-schedule-service (Port 8084)
**Bounded Context:** Fleet & Voyage Management
**Responsibility:** Vessel fleet, voyage schedules, route optimization, capacity management

| Aspect | Detail |
|---|---|
| **Domain Model** | Vessel (aggregate), Voyage (aggregate), Route (entity), PortCall (entity), Capacity (value object) |
| **Patterns** | Graph algorithms (route optimization), Caching (schedule lookups), Strategy (routing), Adapter (AIS integration) |
| **Database** | `freightflow_vessel` — vessels, voyages, routes, port_calls |
| **Kafka Topics** | Produces: `vessel.events` / Consumes: `booking.events` (capacity reservation) |
| **API** | GET `/api/v1/vessels`, GET `/api/v1/voyages`, GET `/api/v1/routes/optimize` |
| **Special** | Caffeine + Redis caching for schedule lookups, capacity checks with optimistic locking |

### 6. notification-service (Port 8086)
**Bounded Context:** Communication & Alerts
**Responsibility:** Multi-channel notifications (email, SMS, webhook), templates, delivery tracking

| Aspect | Detail |
|---|---|
| **Domain Model** | Notification (aggregate), Channel (sealed interface — Email, SMS, Webhook), Template (entity), DeliveryStatus |
| **Patterns** | Strategy (channel selection), Template Method (message formatting), Observer (event-driven), Factory (channel factory) |
| **Database** | `freightflow_notification` — notifications, templates, delivery_log |
| **Kafka Topics** | Consumes: `booking.events`, `billing.events`, `tracking.events` (pure consumer — no produces) |
| **API** | GET `/api/v1/notifications`, GET `/api/v1/notifications/templates`, POST `/api/v1/notifications/send` |
| **Special** | Virtual Threads for async sending, dead letter handling, delivery tracking |

---

## Inter-Service Communication Map

### Event Flow (Kafka — Asynchronous)

```
booking-service ──[BookingCreated]──→ tracking-service     (start tracking containers)
                                   → billing-service       (generate invoice)
                                   → customer-service      (update booking count)
                                   → notification-service  (send confirmation email)

booking-service ──[BookingConfirmed]→ tracking-service     (assign to voyage tracking)
                                    → billing-service      (finalize invoice)
                                    → vessel-schedule-svc  (reserve capacity)
                                    → notification-service (send confirmation)

booking-service ──[BookingCancelled]→ billing-service      (issue credit note/refund)
                                    → tracking-service     (stop tracking)
                                    → vessel-schedule-svc  (release capacity)
                                    → notification-service (send cancellation email)

billing-service ──[InvoiceGenerated]→ booking-service      (update booking state)
                                    → notification-service (send invoice email)

billing-service ──[PaymentReceived]─→ booking-service      (mark as paid)
                                    → notification-service (send receipt)

billing-service ──[RefundIssued]────→ notification-service (send refund confirmation)

tracking-service ─[ContainerMoved]──→ notification-service (milestone alert)

vessel-schedule  ─[VoyageDeparted]──→ tracking-service     (update vessel position source)
                                    → booking-service      (mark bookings as SHIPPED)
                                    → notification-service (departure notification)

vessel-schedule  ─[VoyageArrived]───→ tracking-service     (container arrived at port)
                                    → booking-service      (mark bookings as DELIVERED)
                                    → notification-service (arrival notification)
```

### REST Calls (Synchronous — via API Gateway + Eureka)

```
booking-service ──→ vessel-schedule-service  (check capacity before confirm)
booking-service ──→ customer-service         (validate customer + credit limit)
billing-service ──→ customer-service         (get contract pricing)
api-gateway     ──→ all services             (routes all client requests)
```

---

## End-to-End Booking Flow (All Services)

```
Step 1: Customer creates booking
  Client → API Gateway → booking-service.createBooking()
  → Validate cargo, departure date
  → booking-service → customer-service (REST: validate customer exists + credit limit)
  → Persist booking (DRAFT) + append event
  → Publish BookingCreated to Kafka
  → Return 202 Accepted

Step 2: Events fan out
  BookingCreated → tracking-service    : Create container tracking record
  BookingCreated → billing-service     : Prepare draft invoice
  BookingCreated → customer-service    : Increment customer booking count
  BookingCreated → notification-service: Send "Booking received" email

Step 3: Operator confirms booking
  Client → API Gateway → booking-service.confirmBooking()
  → booking-service → vessel-schedule-service (REST: check + reserve capacity)
  → Transition DRAFT → CONFIRMED
  → Publish BookingConfirmed to Kafka

Step 4: Confirmation events fan out
  BookingConfirmed → billing-service     : Finalize invoice, set payment terms
  BookingConfirmed → tracking-service    : Link containers to voyage
  BookingConfirmed → vessel-schedule-svc : Confirm capacity reservation
  BookingConfirmed → notification-service: Send "Booking confirmed" + invoice

  billing-service publishes InvoiceGenerated:
  InvoiceGenerated → booking-service     : Update booking with invoice reference
  InvoiceGenerated → notification-service: Send invoice PDF to customer

Step 5: Vessel departs
  vessel-schedule-service publishes VoyageDeparted:
  VoyageDeparted → booking-service      : Mark bookings as SHIPPED
  VoyageDeparted → tracking-service     : Start live AIS tracking
  VoyageDeparted → notification-service : Send "Your cargo has departed" email

Step 6: Container milestones
  tracking-service publishes ContainerMoved (at each port):
  ContainerMoved → notification-service : Send milestone update to customer

Step 7: Vessel arrives
  vessel-schedule-service publishes VoyageArrived:
  VoyageArrived → booking-service      : Mark bookings as DELIVERED
  VoyageArrived → tracking-service     : Mark containers as arrived
  VoyageArrived → notification-service : Send "Your cargo has arrived" email
```

---

## Database Isolation (Database-per-Service)

```
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│freightflow_booking│  │freightflow_tracking│ │freightflow_billing│
│                  │  │                  │  │                  │
│ bookings         │  │ containers       │  │ invoices         │
│ booking_events   │  │ movements        │  │ line_items       │
│ booking_project. │  │ milestones       │  │ payments         │
│ outbox_events    │  │ positions        │  │ ledger_entries   │
└──────────────────┘  └──────────────────┘  └──────────────────┘

┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│freightflow_vessel │  │freightflow_cust.  │  │freightflow_notif. │
│                  │  │                  │  │                  │
│ vessels          │  │ customers        │  │ notifications    │
│ voyages          │  │ contracts        │  │ templates        │
│ routes           │  │ customer_roles   │  │ delivery_log     │
│ port_calls       │  │ credit_limits    │  │ channels         │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

**No shared databases. No cross-service JOINs. Data flows via events.**
