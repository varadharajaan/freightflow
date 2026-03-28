package com.freightflow.notificationservice.domain.model;

import java.util.Objects;

/**
 * Value object representing a notification template for message formatting.
 *
 * <p>Templates use Thymeleaf syntax for variable substitution. Each template
 * is associated with a specific channel type and includes both a subject line
 * template and a body template.</p>
 *
 * <h3>Template Variables</h3>
 * <p>Templates support Thymeleaf expressions like {@code ${customerName}},
 * {@code ${bookingId}}, etc. Variables are resolved at send time from the
 * notification context.</p>
 *
 * @param templateId   the unique template identifier
 * @param name         the human-readable template name
 * @param channel      the target channel type (EMAIL, SMS, WEBHOOK)
 * @param subject      the subject line template (Thymeleaf)
 * @param bodyTemplate the body content template (Thymeleaf)
 */
public record NotificationTemplate(
        String templateId,
        String name,
        String channel,
        String subject,
        String bodyTemplate
) {

    /**
     * Compact canonical constructor with fail-fast validation.
     */
    public NotificationTemplate {
        Objects.requireNonNull(templateId, "Template ID must not be null");
        Objects.requireNonNull(name, "Template name must not be null");
        Objects.requireNonNull(channel, "Channel must not be null");
        Objects.requireNonNull(subject, "Subject must not be null");
        Objects.requireNonNull(bodyTemplate, "Body template must not be null");

        if (templateId.isBlank()) {
            throw new IllegalArgumentException("Template ID must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Template name must not be blank");
        }
    }
}
