package com.freightflow.commons.exception;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration that registers global API exception translation for all services.
 *
 * <p>Any service that depends on {@code commons-exception} gets the centralized
 * {@link GlobalExceptionHandler} automatically without explicit {@code @Import}
 * or package-scan customization.</p>
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class FreightFlowExceptionAutoConfiguration {
}
