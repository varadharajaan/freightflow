# FreightFlow - API Design Guide

## API Standards

All APIs in FreightFlow follow these conventions:

- **RESTful** with proper HTTP methods and status codes
- **OpenAPI 3.1** specification (contract-first where possible)
- **JSON** as the default media type (`application/json`)
- **URI versioning**: `/api/v1/`, `/api/v2/`
- **RFC 7807** Problem Details for all error responses
- **Cursor-based pagination** (no offset-based)
- **Idempotency** via `Idempotency-Key` header for POST/PATCH
- **HATEOAS** links for discoverability

---

## Base URL Structure

```
https://api.freightflow.com/api/v1/{resource}
```

---

## Authentication

All API requests require a Bearer token (JWT from Keycloak):

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## Booking API

### Create Booking
```http
POST /api/v1/bookings
Content-Type: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

{
  "customerId": "CUST-001",
  "origin": {
    "port": "DEHAM",
    "terminal": "CTH"
  },
  "destination": {
    "port": "CNSHA",
    "terminal": "YST"
  },
  "cargo": {
    "commodityCode": "HS-8471",
    "description": "Electronic components",
    "weight": {
      "value": 18500.00,
      "unit": "KG"
    },
    "containerRequests": [
      {
        "type": "DRY_40",
        "quantity": 2
      }
    ]
  },
  "requestedDepartureDate": "2026-04-15",
  "specialInstructions": "Handle with care - fragile electronics"
}
```

#### Response: 202 Accepted
```json
{
  "bookingId": "BKG-2026-001234",
  "status": "DRAFT",
  "createdAt": "2026-03-27T14:30:00Z",
  "_links": {
    "self": {"href": "/api/v1/bookings/BKG-2026-001234"},
    "confirm": {"href": "/api/v1/bookings/BKG-2026-001234/confirm", "method": "POST"},
    "cancel": {"href": "/api/v1/bookings/BKG-2026-001234", "method": "DELETE"},
    "documents": {"href": "/api/v1/bookings/BKG-2026-001234/documents"},
    "tracking": {"href": "/api/v1/tracking/bookings/BKG-2026-001234"}
  }
}
```

### Get Booking
```http
GET /api/v1/bookings/BKG-2026-001234
Accept: application/json
If-None-Match: "33a64df551425fcc55e4d42a148795d9f25f89d4"
```

#### Response: 200 OK
```json
{
  "bookingId": "BKG-2026-001234",
  "status": "CONFIRMED",
  "customerId": "CUST-001",
  "origin": {
    "port": "DEHAM",
    "portName": "Hamburg",
    "country": "DE",
    "terminal": "CTH"
  },
  "destination": {
    "port": "CNSHA",
    "portName": "Shanghai",
    "country": "CN",
    "terminal": "YST"
  },
  "cargo": {
    "commodityCode": "HS-8471",
    "description": "Electronic components",
    "weight": {"value": 18500.00, "unit": "KG"},
    "containers": [
      {"containerId": "FRFL-1234567-0", "type": "DRY_40", "status": "ALLOCATED"},
      {"containerId": "FRFL-1234567-1", "type": "DRY_40", "status": "ALLOCATED"}
    ]
  },
  "voyage": {
    "vesselName": "MV FreightStar",
    "voyageNumber": "VOY-2026-0456",
    "estimatedDeparture": "2026-04-15T08:00:00Z",
    "estimatedArrival": "2026-05-12T14:00:00Z"
  },
  "createdAt": "2026-03-27T14:30:00Z",
  "updatedAt": "2026-03-27T15:45:00Z",
  "_links": {
    "self": {"href": "/api/v1/bookings/BKG-2026-001234"},
    "cancel": {"href": "/api/v1/bookings/BKG-2026-001234", "method": "DELETE"},
    "amend": {"href": "/api/v1/bookings/BKG-2026-001234", "method": "PATCH"},
    "documents": {"href": "/api/v1/bookings/BKG-2026-001234/documents"},
    "tracking": {"href": "/api/v1/tracking/bookings/BKG-2026-001234"},
    "invoice": {"href": "/api/v1/billing/invoices?bookingId=BKG-2026-001234"},
    "history": {"href": "/api/v1/bookings/BKG-2026-001234/history"}
  }
}
```

### List Bookings (Cursor-Based Pagination)
```http
GET /api/v1/bookings?status=CONFIRMED&limit=20&cursor=eyJjcmVhdGVkQXQiOiIyMDI2LTAzLTI3VDE0OjMwOjAwWiJ9
```

#### Response: 200 OK
```json
{
  "data": [
    {"bookingId": "BKG-2026-001234", "status": "CONFIRMED", "...": "..."},
    {"bookingId": "BKG-2026-001235", "status": "CONFIRMED", "...": "..."}
  ],
  "pagination": {
    "limit": 20,
    "hasMore": true,
    "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTAzLTI3VDEwOjAwOjAwWiJ9",
    "previousCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTAzLTI4VDE0OjMwOjAwWiJ9"
  },
  "_links": {
    "self": {"href": "/api/v1/bookings?status=CONFIRMED&limit=20"},
    "next": {"href": "/api/v1/bookings?status=CONFIRMED&limit=20&cursor=eyJjcmVhdGVkQXQiOiIyMDI2LTAzLTI3VDEwOjAwOjAwWiJ9"}
  }
}
```

---

## Error Responses (RFC 7807 Problem Details)

### 404 Not Found
```json
{
  "type": "https://api.freightflow.com/problems/booking-not-found",
  "title": "Booking Not Found",
  "status": 404,
  "detail": "No booking found with ID 'BKG-2026-999999'",
  "instance": "/api/v1/bookings/BKG-2026-999999",
  "timestamp": "2026-03-27T14:30:00Z",
  "traceId": "abc123def456"
}
```

### 409 Conflict (State Violation)
```json
{
  "type": "https://api.freightflow.com/problems/invalid-state-transition",
  "title": "Invalid State Transition",
  "status": 409,
  "detail": "Cannot cancel booking BKG-2026-001234: current state is SHIPPED. Only DRAFT or CONFIRMED bookings can be cancelled.",
  "instance": "/api/v1/bookings/BKG-2026-001234",
  "currentState": "SHIPPED",
  "allowedTransitions": ["DELIVERED"],
  "timestamp": "2026-03-27T14:30:00Z",
  "traceId": "abc123def456"
}
```

### 422 Validation Error
```json
{
  "type": "https://api.freightflow.com/problems/validation-error",
  "title": "Validation Failed",
  "status": 422,
  "detail": "Request body contains 2 validation errors",
  "errors": [
    {
      "field": "cargo.weight.value",
      "message": "Weight must be greater than 0",
      "rejectedValue": -500
    },
    {
      "field": "requestedDepartureDate",
      "message": "Departure date must be at least 7 days in the future",
      "rejectedValue": "2026-03-28"
    }
  ],
  "timestamp": "2026-03-27T14:30:00Z",
  "traceId": "abc123def456"
}
```

### 429 Rate Limited
```json
{
  "type": "https://api.freightflow.com/problems/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "You have exceeded the rate limit of 100 requests per minute",
  "retryAfter": 32,
  "timestamp": "2026-03-27T14:30:00Z"
}
```

**Rate Limit Headers** (on every response):
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1711546232
Retry-After: 32
```

---

## HTTP Status Code Usage

| Status | When Used |
|---|---|
| `200 OK` | Successful GET, PUT, PATCH |
| `201 Created` | Successful POST that creates a resource synchronously |
| `202 Accepted` | Successful POST for async operations (booking creation) |
| `204 No Content` | Successful DELETE |
| `304 Not Modified` | ETag match (conditional request) |
| `400 Bad Request` | Malformed request body / syntax error |
| `401 Unauthorized` | Missing or invalid JWT token |
| `403 Forbidden` | Valid token but insufficient permissions |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | State conflict or optimistic locking failure |
| `422 Unprocessable Entity` | Validation errors |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected server error |
| `503 Service Unavailable` | Circuit breaker open / service degraded |

---

## Idempotency

All state-changing operations (POST, PATCH) support idempotency:

```http
POST /api/v1/bookings
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

- Client generates a UUID v4 as the idempotency key
- Server stores the key + response for 24 hours in Redis
- Duplicate requests with the same key return the cached response
- Key format: UUID v4 (128-bit)

---

## API Versioning Strategy

| Version | Status | Sunset Date |
|---|---|---|
| `/api/v1/` | **Current** | - |
| `/api/v2/` | Planned | - |

### Versioning Rules
1. Breaking changes require a new version
2. Non-breaking changes (new optional fields) go in the current version
3. Deprecated versions get a `Sunset` header and 6-month notice
4. Maximum 2 versions maintained simultaneously
