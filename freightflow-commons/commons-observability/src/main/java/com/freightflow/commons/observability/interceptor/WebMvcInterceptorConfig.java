package com.freightflow.commons.observability.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * Spring MVC configuration that registers all custom interceptors.
 *
 * <h3>Spring Advanced Feature: WebMvcConfigurer</h3>
 * <p>Implements {@link WebMvcConfigurer} to customize Spring MVC behavior without
 * overriding the entire auto-configuration. This is the recommended approach
 * in Spring Boot 3 (vs. extending WebMvcConfigurationSupport which disables auto-config).</p>
 *
 * @see RequestLoggingInterceptor
 */
@Configuration
public class WebMvcInterceptorConfig implements WebMvcConfigurer {

    private final RequestLoggingInterceptor requestLoggingInterceptor;

    public WebMvcInterceptorConfig(RequestLoggingInterceptor requestLoggingInterceptor) {
        this.requestLoggingInterceptor = Objects.requireNonNull(requestLoggingInterceptor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLoggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");
    }
}
