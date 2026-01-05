package com.tiqmo.monitoring.auth.infra.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint with a logical key for centralized configuration.
 *
 * Pattern: {service-id}.{controller-id}.{action}
 * Example: "auth.auth.login", "auth.auth.validate"
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiKey {

    /**
     * The logical key for this endpoint.
     * Pattern: {service-id}.{controller-id}.{action}
     */
    String value();

    /**
     * Description of the endpoint.
     */
    String description() default "";

    /**
     * Whether this endpoint is enabled by default.
     */
    boolean enabledByDefault() default true;

    /**
     * Tags for categorization.
     */
    String[] tags() default {};
}
