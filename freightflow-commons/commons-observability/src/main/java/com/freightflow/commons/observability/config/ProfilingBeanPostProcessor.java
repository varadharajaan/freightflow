package com.freightflow.commons.observability.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Custom {@link BeanPostProcessor} that logs bean lifecycle events for profiling.
 *
 * <h3>Spring Advanced Feature: BeanPostProcessor</h3>
 * <p>BeanPostProcessor is one of the most powerful Spring extension points.
 * It intercepts every bean during initialization, allowing:</p>
 * <ul>
 *   <li>Dynamic proxy wrapping (how Spring AOP works internally)</li>
 *   <li>Bean validation at startup</li>
 *   <li>Custom initialization logic without modifying bean code</li>
 *   <li>Conditional bean modification based on annotations</li>
 * </ul>
 *
 * <p>This implementation tracks FreightFlow service bean initialization for
 * startup profiling — helps identify slow-starting beans in large applications.</p>
 *
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 */
@Component
public class ProfilingBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProfilingBeanPostProcessor.class);

    /**
     * Called before bean initialization (before @PostConstruct, InitializingBean.afterPropertiesSet).
     * Logs only FreightFlow beans to avoid noise from framework beans.
     */
    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {
        if (isFreightFlowBean(bean)) {
            log.trace("Initializing bean: name={}, class={}",
                    beanName, bean.getClass().getSimpleName());
        }
        return bean;
    }

    /**
     * Called after bean initialization. Logs successful initialization of
     * FreightFlow service and repository beans.
     */
    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName)
            throws BeansException {
        if (isFreightFlowBean(bean)) {
            log.debug("Bean initialized: name={}, class={}",
                    beanName, bean.getClass().getSimpleName());
        }
        return bean;
    }

    private boolean isFreightFlowBean(Object bean) {
        return bean.getClass().getPackageName().startsWith("com.freightflow");
    }
}
