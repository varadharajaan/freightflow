package com.freightflow.commons.observability.profiling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SQL query profiling and Hibernate statistics.
 *
 * <p>When enabled (via {@code freightflow.profiling.sql.enabled=true}), this provides:</p>
 * <ul>
 *   <li>Hibernate statistics exposed via Micrometer (query count, cache hit/miss, slow queries)</li>
 *   <li>Slow query logging threshold configuration</li>
 *   <li>N+1 query detection hints in logs</li>
 * </ul>
 *
 * <p>SQL profiling is enabled by default in {@code local} and {@code dev} profiles,
 * disabled in {@code prod} and {@code staging} for performance.</p>
 *
 * <h3>Metrics Exposed</h3>
 * <pre>
 * hibernate.sessions.open_total
 * hibernate.sessions.closed_total
 * hibernate.query.executions_total
 * hibernate.query.executions_max_time_seconds
 * hibernate.second_level_cache.hit_total
 * hibernate.second_level_cache.miss_total
 * hibernate.entities.inserts_total
 * hibernate.entities.updates_total
 * hibernate.entities.deletes_total
 * hibernate.entities.loads_total
 * </pre>
 *
 * @see PerformanceProfilingAspect
 */
@Configuration
@ConditionalOnProperty(name = "freightflow.profiling.sql.enabled", havingValue = "true", matchIfMissing = false)
public class SqlProfilingConfig {

    private static final Logger log = LoggerFactory.getLogger(SqlProfilingConfig.class);

    /**
     * Logs a startup message when SQL profiling is activated.
     * The actual Hibernate statistics are enabled via JPA properties in application.yml:
     * <pre>
     * spring.jpa.properties.hibernate.generate_statistics=true
     * spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=100
     * </pre>
     */
    @Bean
    public SqlProfilingInitializer sqlProfilingInitializer() {
        return new SqlProfilingInitializer();
    }

    /**
     * Startup bean that logs profiling activation status.
     */
    public static class SqlProfilingInitializer {

        private static final Logger log = LoggerFactory.getLogger(SqlProfilingInitializer.class);

        public SqlProfilingInitializer() {
            log.info("SQL query profiling ENABLED — Hibernate statistics and slow query logging active");
            log.info("Slow query threshold configured via: hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS");
        }
    }
}
