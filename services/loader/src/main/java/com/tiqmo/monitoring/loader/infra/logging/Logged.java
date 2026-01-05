package com.tiqmo.monitoring.loader.infra.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic method logging.
 *
 * <p>When applied to a method, automatically logs:
 * <ul>
 *   <li>[ENTRY] with method name and parameter values</li>
 *   <li>[EXIT] with success status and duration</li>
 *   <li>[ERROR] if an exception occurs</li>
 *   <li>[RESULT] with return value (optional)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * @Logged
 * public LoaderResult executeLoader(String loaderCode) {
 *     // Method body - entry/exit automatically logged
 * }
 *
 * @Logged(level = LogLevel.DEBUG, logResult = true)
 * public List<Loader> getLoaders() {
 *     // Method body - with DEBUG level and result logging
 * }
 * }</pre>
 *
 * <p>Log Output Example:
 * <pre>
 * [ENTRY] LoaderService.executeLoader | loaderCode=ABC123 | correlationId=xxx
 * [EXIT] LoaderService.executeLoader | success=true | duration=150ms
 * </pre>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Logged {

    /**
     * Log level for entry/exit logs.
     * Default is TRACE for entry/exit, INFO for results.
     */
    LogLevel level() default LogLevel.TRACE;

    /**
     * Whether to log the return value.
     * Default is false to avoid logging sensitive data.
     */
    boolean logResult() default false;

    /**
     * Whether to log method parameters.
     * Default is true.
     */
    boolean logParams() default true;

    /**
     * Parameter names to exclude from logging (e.g., "password", "token").
     */
    String[] excludeParams() default {};

    /**
     * Custom message to include in the log.
     */
    String message() default "";

    /**
     * Log levels for the @Logged annotation.
     */
    enum LogLevel {
        TRACE,
        DEBUG,
        INFO
    }
}
