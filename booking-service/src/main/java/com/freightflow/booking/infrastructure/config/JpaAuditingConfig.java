package com.freightflow.booking.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * JPA Auditing configuration for automatic population of audit columns.
 *
 * <p>Enables {@code @CreatedDate}, {@code @LastModifiedDate}, {@code @CreatedBy},
 * and {@code @LastModifiedBy} annotations on JPA entities.</p>
 *
 * <p>The {@link AuditorAware} bean provides the current user. In a future iteration,
 * this will extract the user from the JWT token in the SecurityContext.
 * Currently returns "system" as a placeholder.</p>
 *
 * @see org.springframework.data.annotation.CreatedDate
 * @see org.springframework.data.annotation.CreatedBy
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    /**
     * Provides the current auditor (user) for JPA auditing.
     *
     * <p>TODO: Replace with SecurityContext-based implementation when OAuth2 is integrated.
     * See Issue #15 (T9: Security).</p>
     *
     * @return the auditor provider bean
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        // Placeholder — will be replaced with JWT-based auditor in T9 (Security)
        return () -> Optional.of("system");
    }
}
