package com.freightflow.booking.infrastructure.config;

import com.freightflow.commons.observability.config.FreightFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * Spring Boot {@link ApplicationRunner} that executes on startup.
 *
 * <h3>Spring Advanced Feature: ApplicationRunner</h3>
 * <p>ApplicationRunner runs after the application context is fully initialized
 * but before the service starts accepting requests. It's ideal for:</p>
 * <ul>
 *   <li>Startup health checks (verify connectivity to PostgreSQL, Kafka, Redis)</li>
 *   <li>Configuration validation and logging</li>
 *   <li>Data seeding in development profiles</li>
 *   <li>Warm-up operations (cache priming, connection pool initialization)</li>
 * </ul>
 *
 * <h3>Spring Advanced Feature: @EnableConfigurationProperties</h3>
 * <p>Enables type-safe binding of {@link FreightFlowProperties} from application.yml.
 * Properties are validated at startup via Jakarta Bean Validation annotations.</p>
 *
 * @see ApplicationRunner
 * @see FreightFlowProperties
 */
@Component
@EnableConfigurationProperties(FreightFlowProperties.class)
public class StartupHealthCheckRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupHealthCheckRunner.class);

    private final Environment environment;
    private final FreightFlowProperties properties;

    public StartupHealthCheckRunner(Environment environment, FreightFlowProperties properties) {
        this.environment = Objects.requireNonNull(environment);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] activeProfiles = environment.getActiveProfiles();

        log.info("============================================================");
        log.info("  FreightFlow Booking Service STARTED");
        log.info("============================================================");
        log.info("  Active Profiles   : {}", Arrays.toString(activeProfiles));
        log.info("  Service Name      : {}", properties.getServiceName());
        log.info("  Virtual Threads   : {}", environment.getProperty("spring.threads.virtual.enabled", "false"));
        log.info("  SQL Profiling     : {}", properties.getProfiling().getSql().isEnabled());
        log.info("  Min Departure Days: {}", properties.getBooking().getMinDepartureDays());
        log.info("  Max Containers    : {}", properties.getBooking().getMaxContainersPerBooking());
        log.info("  Kafka Partitions  : {}", properties.getKafka().getPartitions());
        log.info("  Server Port       : {}", environment.getProperty("server.port", "8081"));
        log.info("============================================================");

        // Validate critical configuration
        if (activeProfiles.length == 0) {
            log.warn("No active Spring profile set! Defaulting to built-in configuration. "
                    + "Set spring.profiles.active=local|dev|staging|prod");
        }

        if ("true".equals(environment.getProperty("spring.jpa.open-in-view"))) {
            log.warn("spring.jpa.open-in-view=true detected! This is a performance anti-pattern. "
                    + "Set it to false in production.");
        }
    }
}
