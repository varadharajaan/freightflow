package com.freightflow.commons.observability.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration that registers distributed tracing for all FreightFlow services.
 *
 * <p>Any service that depends on {@code commons-observability} gets tracing support
 * automatically via this auto-configuration entry — no explicit {@code @Import}
 * or component scan required.</p>
 *
 * <p>Imports {@link TracingConfig} (ObservedAspect for @Observed support) and
 * {@link KafkaTracingConfig} (trace propagation across Kafka produce/consume).</p>
 *
 * @see TracingConfig
 * @see KafkaTracingConfig
 */
@AutoConfiguration
@Import({TracingConfig.class, KafkaTracingConfig.class})
public class FreightFlowTracingAutoConfiguration {
}
