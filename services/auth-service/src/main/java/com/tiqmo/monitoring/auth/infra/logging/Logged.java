package com.tiqmo.monitoring.auth.infra.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic method logging.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 * @see /docs/LOGGING_STRATEGY.md
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Logged {

    LogLevel level() default LogLevel.TRACE;

    boolean logResult() default false;

    boolean logParams() default true;

    String[] excludeParams() default {};

    String message() default "";

    enum LogLevel {
        TRACE,
        DEBUG,
        INFO
    }
}
