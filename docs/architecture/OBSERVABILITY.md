# FreightFlow Observability Architecture

## Overview

FreightFlow implements the **Three Pillars of Observability** to provide complete visibility into
the health, performance, and behavior of every microservice in the platform.

| Pillar    | Tool                    | Access                        | Purpose                                |
|-----------|-------------------------|-------------------------------|----------------------------------------|
| Metrics   | Prometheus + Grafana    | http://localhost:9090 / :3000 | Time-series metrics, dashboards, alerts|
| Logging   | Logback + ELK (planned) | Structured JSON logs          | Request-level audit trail              |
| Tracing   | Jaeger (via OTLP)       | http://localhost:16686        | End-to-end distributed trace chains    |

---

## 1. Metrics (Prometheus + Grafana)

### What Gets Collected

Every FreightFlow microservice exposes a `/actuator/prometheus` endpoint via Spring Boot Actuator
and Micrometer. Prometheus scrapes all services every 15 seconds.

#### JVM Metrics (auto-registered via MetricsConfig)

| Metric                        | Description                             |
|-------------------------------|-----------------------------------------|
| `jvm_memory_used_bytes`       | Heap and non-heap memory usage          |
| `jvm_memory_committed_bytes`  | Committed memory by JVM                 |
| `jvm_memory_max_bytes`        | Maximum memory limit                    |
| `jvm_gc_pause_seconds`        | GC pause duration (by cause and action) |
| `jvm_threads_live_threads`    | Current live thread count               |
| `jvm_threads_daemon_threads`  | Daemon thread count                     |
| `jvm_classes_loaded_classes`  | Currently loaded classes                |
| `system_cpu_usage`            | System-wide CPU utilization             |
| `process_cpu_usage`           | JVM process CPU utilization             |

#### HTTP Metrics (auto-registered by Spring Boot Actuator)

| Metric                               | Description                               |
|---------------------------------------|-------------------------------------------|
| `http_server_requests_seconds_count`  | Total request count (by URI, method, status)|
| `http_server_requests_seconds_sum`    | Total time spent processing requests      |
| `http_server_requests_seconds_bucket` | Histogram buckets for latency percentiles |

#### Kafka Metrics (auto-registered by Spring Kafka)

| Metric                                         | Description                    |
|------------------------------------------------|--------------------------------|
| `kafka_consumer_fetch_manager_records_lag_max`  | Max consumer lag per partition |
| `kafka_consumer_fetch_manager_records_consumed_total` | Total records consumed   |
| `kafka_producer_topic_record_send_total`        | Total records produced         |

#### Resilience4j Metrics

| Metric                                 | Description                             |
|----------------------------------------|-----------------------------------------|
| `resilience4j_circuitbreaker_state`    | Circuit breaker state (0=closed, 1=open)|
| `resilience4j_retry_calls_total`       | Retry attempts by outcome               |
| `resilience4j_bulkhead_available_concurrent_calls` | Bulkhead capacity remaining   |

#### Cache Metrics (Spring Cache + Caffeine/Redis)

| Metric                | Description                                |
|-----------------------|--------------------------------------------|
| `cache_gets_total`    | Cache lookups (tagged `result=hit\|miss`)  |
| `cache_evictions_total` | Cache evictions by region                |
| `cache_size`          | Current number of entries in cache         |

#### Custom Business Metrics (registered in MetricsConfig)

| Metric                               | Description                     |
|--------------------------------------|---------------------------------|
| `freightflow_bookings_created_total` | Bookings created (success/fail) |
| `freightflow_bookings_confirmed_total` | Bookings confirmed            |
| `freightflow_bookings_cancelled_total` | Bookings cancelled            |
| `freightflow_events_published_total` | Domain events published by type |
| `freightflow_method_execution_seconds` | Method profiling (@Profiled)  |

### Retention

Prometheus is configured with `--storage.tsdb.retention.time=30d` (30 days of metrics history).

### Alerting Rules

Alerts are defined in `infrastructure/monitoring/prometheus/alerts.yml`:

| Alert                | Severity | Condition                                   | Duration |
|----------------------|----------|---------------------------------------------|----------|
| ServiceDown          | Critical | Prometheus scrape target is unreachable      | 1 min    |
| HighErrorRate        | Critical | HTTP 5xx rate > 5%                          | 5 min    |
| HighLatency          | Warning  | p99 response time > 2 seconds               | 5 min    |
| HighKafkaConsumerLag | Warning  | Consumer lag > 10,000 records               | 10 min   |
| CircuitBreakerOpen   | Critical | Circuit breaker in OPEN state               | 2 min    |
| LowCacheHitRatio     | Warning  | Cache hit ratio < 70%                       | 10 min   |
| HighJvmMemory        | Warning  | JVM heap usage > 85%                        | 5 min    |
| SagaStuck            | Warning  | Saga in non-terminal state                  | 10 min   |

---

## 2. Grafana Dashboards

### Access

- **URL**: http://localhost:3000
- **Credentials**: admin / admin

### FreightFlow Platform Overview Dashboard

The auto-provisioned dashboard (`freightflow-overview.json`) provides seven rows of panels:

| Row       | Panels                                                              |
|-----------|---------------------------------------------------------------------|
| Service Health | UP/DOWN status for all 7 services (stat panels with color)    |
| HTTP Traffic   | Request rate (RPM), error rate (%), p50/p95/p99 latency      |
| JVM            | Heap used/committed, GC pause time, thread count             |
| Kafka          | Consumer lag, messages produced/consumed, DLQ count          |
| Business KPIs  | Bookings created/confirmed/cancelled, active sagas, events   |
| Resilience     | Circuit breaker state, retry count, bulkhead utilization     |
| Cache          | Hit ratio per region, eviction rate, cache size              |

### Template Variables

The dashboard uses Grafana template variables for filtering:

- **`$datasource`** — select which Prometheus datasource to query
- **`$service`** — multi-select filter for service names (populated from `up` metric labels)

---

## 3. Distributed Tracing (Jaeger via OpenTelemetry)

### Access

- **Jaeger UI**: http://localhost:16686
- **OTLP gRPC endpoint**: localhost:4317
- **OTLP HTTP endpoint**: localhost:4318

### How Traces Propagate

FreightFlow uses **W3C Trace Context** propagation (the OpenTelemetry default). Traces propagate
across all service boundaries:

```
Client
  │ traceparent: 00-<traceId>-<spanId>-01
  ▼
API Gateway (span: gateway-route)
  │ traceparent header forwarded
  ▼
Booking Service (span: POST /api/v1/bookings)
  │
  ├──► PostgreSQL (span: INSERT booking) — auto-instrumented by JDBC
  │
  └──► Kafka Producer (span: send BookingCreatedEvent)
        │ traceparent injected into Kafka record headers
        ▼
      Kafka Topic: freightflow.booking.events
        │
        ▼
      Tracking Service — Kafka Consumer (span: receive BookingCreatedEvent)
        │ traceparent extracted from Kafka record headers
        └──► TrackingService.handleBookingCreated (span: process)
```

### Configuration

Tracing is configured in each service's `application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling in dev, 0.1 (10%) in production
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces

freightflow:
  tracing:
    enabled: true
```

### Spring Boot 3.x Tracing Stack

| Component                      | Role                                      |
|--------------------------------|-------------------------------------------|
| Micrometer Tracing             | Vendor-neutral tracing abstraction        |
| micrometer-tracing-bridge-otel | Bridges Micrometer to OpenTelemetry SDK   |
| opentelemetry-exporter-otlp    | Exports traces via OTLP to Jaeger         |
| Spring Boot auto-configuration | Auto-instruments HTTP, JDBC, Kafka, etc.  |

### Kafka Trace Propagation

`KafkaTracingConfig` enables observation on both the KafkaTemplate (producer) and the listener
container factory (consumer). This ensures the `traceparent` header is:

1. **Injected** into Kafka record headers by the producer
2. **Extracted** from Kafka record headers by the consumer
3. **Linked** as parent-child spans in the same trace

---

## 4. Structured Logging

### Configuration

Logging is configured via `logback-spring.xml` in `commons-observability`:

| Profile      | Output Format   | Destination               |
|--------------|-----------------|---------------------------|
| local        | Human-readable  | Console + rolling file    |
| prod/staging | JSON (Logstash) | Rolling JSON file         |

### MDC Fields

Every log entry includes Mapped Diagnostic Context (MDC) fields populated by `CorrelationIdFilter`:

| Field           | Source                           | Purpose                    |
|-----------------|----------------------------------|----------------------------|
| `correlationId` | `X-Correlation-ID` header or UUID| Cross-service request trace|
| `requestMethod` | HTTP request method              | Request context            |
| `requestUri`    | HTTP request URI                 | Request context            |
| `tenantId`      | Security context (future)        | Multi-tenancy support      |
| `userId`        | Security context (future)        | Audit trail                |
| `serviceName`   | `spring.application.name`        | Service identification     |

### Log Levels

| Logger                    | Level | Purpose                           |
|---------------------------|-------|-----------------------------------|
| `com.freightflow`         | DEBUG | Application code (INFO in prod)   |
| `org.springframework`     | INFO  | Spring framework                  |
| `org.hibernate.SQL`       | DEBUG | SQL queries (WARN in prod)        |
| `org.apache.kafka`        | WARN  | Kafka client                      |
| `io.github.resilience4j`  | INFO  | Circuit breakers, retries         |

---

## 5. How to Extend

### Adding a New Custom Metric

1. Inject `MeterRegistry` into your service class
2. Create a counter, gauge, or timer:
   ```java
   Counter.builder("freightflow.invoices.generated")
       .description("Total invoices generated")
       .tag("currency", "USD")
       .register(meterRegistry);
   ```
3. The metric will automatically appear in `/actuator/prometheus` and Grafana

### Adding a New Alert Rule

1. Edit `infrastructure/monitoring/prometheus/alerts.yml`
2. Add a new rule under the appropriate group:
   ```yaml
   - alert: MyNewAlert
     expr: my_metric > threshold
     for: 5m
     labels:
       severity: warning
     annotations:
       summary: "Brief description"
       description: "Detailed description with {{ $labels.job }} and {{ $value }}"
   ```
3. Prometheus reloads rules automatically (or send `POST /-/reload`)

### Adding a New Dashboard Panel

1. Edit `infrastructure/monitoring/grafana/dashboards/freightflow-overview.json`
2. Add a new panel object to the `panels` array with:
   - Unique `id`
   - `gridPos` for layout (h=height, w=width, x/y=position)
   - `targets` with Prometheus queries
   - `type` (timeseries, stat, gauge, table, etc.)
3. Grafana auto-reloads from the provisioned directory

### Adding a New Service to Prometheus

1. Edit `infrastructure/monitoring/prometheus/prometheus.yml`
2. Add a new `scrape_configs` entry:
   ```yaml
   - job_name: 'my-new-service'
     metrics_path: '/actuator/prometheus'
     static_configs:
       - targets: ['my-new-service:8087']
         labels:
           service: 'my-new-service'
   ```
3. The service will appear in Grafana's `$service` dropdown after the next scrape

---

## 6. Infrastructure Ports Summary

| Service          | Port  | Purpose                    |
|------------------|-------|----------------------------|
| Prometheus       | 9090  | Metrics query UI + API     |
| Grafana          | 3000  | Dashboard visualization    |
| Jaeger UI        | 16686 | Distributed trace explorer |
| Jaeger OTLP gRPC | 4317 | Trace ingestion (gRPC)     |
| Jaeger OTLP HTTP | 4318 | Trace ingestion (HTTP)     |
