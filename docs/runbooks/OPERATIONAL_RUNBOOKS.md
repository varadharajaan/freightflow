# FreightFlow - Operational Runbooks

## Purpose
These runbooks provide step-by-step procedures for common operational tasks
and incident response scenarios.

---

## Runbook Index

| ID | Runbook | Severity | When to Use |
|---|---|---|---|
| RB-001 | [Service Not Starting](#rb-001-service-not-starting) | P1 | Service fails to start or crashes on boot |
| RB-002 | [High Kafka Consumer Lag](#rb-002-high-kafka-consumer-lag) | P2 | Consumer lag > 10,000 messages |
| RB-003 | [Database Connection Pool Exhausted](#rb-003-database-connection-pool-exhausted) | P1 | HikariCP pool full, requests timing out |
| RB-004 | [Circuit Breaker Open](#rb-004-circuit-breaker-open) | P2 | External service calls failing |
| RB-005 | [Redis Cache Down](#rb-005-redis-cache-down) | P2 | Redis unreachable, cache misses spiking |
| RB-006 | [Rolling Back a Deployment](#rb-006-rolling-back-a-deployment) | P1 | Bad deploy, need to revert |
| RB-007 | [Database Migration Failed](#rb-007-database-migration-failed) | P1 | Flyway migration error on startup |

---

## RB-001: Service Not Starting

**Symptoms**: Pod in CrashLoopBackOff, service health check failing

**Steps**:
1. Check pod logs:
   ```bash
   kubectl logs -f <pod-name> -n freightflow --previous
   ```
2. Check startup probe:
   ```bash
   kubectl describe pod <pod-name> -n freightflow | grep -A5 "Conditions"
   ```
3. Common causes:
   - **Database unreachable**: Check RDS status and security groups
   - **Config Server down**: Check config-server pod, fall back to local config
   - **Flyway migration failed**: See RB-007
   - **Port conflict**: Check if port is already bound
   - **Memory limit**: Check OOMKilled in pod events
4. Remediation:
   - If config issue: fix config, restart pod
   - If resource issue: scale up node group or increase limits
   - If code issue: rollback deployment (see RB-006)

---

## RB-002: High Kafka Consumer Lag

**Symptoms**: Prometheus alert `kafka_consumer_lag > 10000`, events delayed

**Steps**:
1. Check consumer lag:
   ```bash
   kafka-consumer-groups --bootstrap-server $KAFKA_BROKERS \
     --group booking-service-group --describe
   ```
2. Check consumer health:
   ```bash
   kubectl logs -f deployment/booking-service -n freightflow | grep "consumer"
   ```
3. Common causes:
   - **Consumer crashed**: Check for exceptions in logs
   - **Slow processing**: Check database latency, external service latency
   - **Too few partitions**: Consumer count > partition count means idle consumers
   - **Rebalancing**: Frequent pod restarts cause rebalancing storms
4. Remediation:
   - Scale consumers: `kubectl scale deployment/booking-service --replicas=5`
   - If processing error: fix and redeploy, messages will reprocess
   - Check dead letter topic for poisoned messages

---

## RB-003: Database Connection Pool Exhausted

**Symptoms**: `HikariPool-1 - Connection is not available`, request timeouts

**Steps**:
1. Check active connections:
   ```sql
   SELECT count(*) FROM pg_stat_activity WHERE datname = 'freightflow_booking';
   ```
2. Check HikariCP metrics:
   ```bash
   curl localhost:8081/actuator/metrics/hikaricp.connections.active
   curl localhost:8081/actuator/metrics/hikaricp.connections.pending
   ```
3. Common causes:
   - **Connection leak**: Long-running transaction not committed/rolled back
   - **Spike in traffic**: Pool size too small for load
   - **Slow queries**: Connections held too long
4. Remediation:
   - Immediate: Restart affected pods to release connections
   - Short-term: Increase pool size in config (max-pool-size)
   - Long-term: Fix connection leaks, optimize slow queries

---

## RB-004: Circuit Breaker Open

**Symptoms**: `CircuitBreaker 'externalService' is OPEN`, fallback responses

**Steps**:
1. Check circuit breaker state:
   ```bash
   curl localhost:8081/actuator/circuitbreakers
   ```
2. Check the downstream service health
3. Check Resilience4j metrics:
   ```bash
   curl localhost:8081/actuator/metrics/resilience4j.circuitbreaker.calls
   ```
4. Remediation:
   - If downstream is down: wait for recovery (circuit breaker will half-open automatically)
   - If downstream recovered but circuit still open: check `waitDurationInOpenState` config
   - If false positive: adjust `failureRateThreshold` and `slidingWindowSize`

---

## RB-005: Redis Cache Down

**Symptoms**: Redis connection refused, cache hit ratio drops to 0%

**Steps**:
1. Check Redis connectivity:
   ```bash
   redis-cli -h $REDIS_HOST ping
   ```
2. Check ElastiCache status in AWS console
3. Application should degrade gracefully (L1 Caffeine still works, L3 PostgreSQL is source of truth)
4. Remediation:
   - If Redis node failed: ElastiCache automatic failover (Multi-AZ)
   - If network issue: Check security groups and VPC routing
   - Application continues serving with higher latency (cache-aside pattern degrades to DB reads)

---

## RB-006: Rolling Back a Deployment

**Steps**:
1. Using ArgoCD:
   ```bash
   argocd app rollback freightflow-booking --revision <previous-revision>
   ```
2. Using kubectl:
   ```bash
   kubectl rollout undo deployment/booking-service -n freightflow
   kubectl rollout status deployment/booking-service -n freightflow
   ```
3. Using Argo Rollouts (if canary was in progress):
   ```bash
   kubectl argo rollouts abort booking-service -n freightflow
   ```
4. Verify rollback:
   ```bash
   kubectl get pods -n freightflow -l app=booking-service
   curl localhost:8081/actuator/info  # check version
   ```

---

## RB-007: Database Migration Failed

**Symptoms**: Service won't start, Flyway error in logs

**Steps**:
1. Check Flyway status:
   ```bash
   ./mvnw -pl booking-service flyway:info \
     -Dflyway.url=jdbc:postgresql://$DB_HOST:5432/freightflow_booking
   ```
2. Check for failed migration:
   ```sql
   SELECT * FROM flyway_schema_history WHERE success = false;
   ```
3. Remediation:
   - If migration is fixable: Fix SQL, run `flyway:repair` then `flyway:migrate`
   - If migration must be reverted: Apply a new compensating migration (never delete history)
   - If data corruption: Restore from RDS automated backup

---

## Escalation Matrix

| Severity | Response Time | Who to Contact |
|---|---|---|
| P1 (Service Down) | 15 minutes | On-call engineer via PagerDuty |
| P2 (Degraded) | 1 hour | Team lead via Slack |
| P3 (Minor) | Next business day | Create GitHub issue |
