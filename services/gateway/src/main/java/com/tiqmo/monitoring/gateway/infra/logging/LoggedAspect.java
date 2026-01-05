package com.tiqmo.monitoring.gateway.infra.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Aspect that processes the @Logged annotation for automatic method logging.
 * Supports both blocking and reactive (Mono/Flux) return types.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Aspect
@Component
public class LoggedAspect {

    private static final Set<String> DEFAULT_EXCLUDED_PARAMS = Set.of(
        "password", "pwd", "secret", "token", "apiKey", "apikey", "api_key",
        "credential", "credentials", "auth", "authorization"
    );

    @Around("@annotation(logged)")
    public Object logMethod(ProceedingJoinPoint joinPoint, Logged logged) throws Throwable {
        return doLog(joinPoint, logged);
    }

    @Around("@within(logged) && execution(public * *(..))")
    public Object logClassMethods(ProceedingJoinPoint joinPoint, Logged logged) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.isAnnotationPresent(Logged.class)) {
            return joinPoint.proceed();
        }
        return doLog(joinPoint, logged);
    }

    private Object doLog(ProceedingJoinPoint joinPoint, Logged logged) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = signature.getName();
        String fullMethodName = className + "." + methodName;
        Class<?> returnType = signature.getReturnType();

        LogUtil log = LogUtil.of(joinPoint.getTarget().getClass());
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        String params = logged.logParams()
            ? buildParamsString(joinPoint, signature, logged.excludeParams())
            : "";

        // Handle reactive return types
        if (Mono.class.isAssignableFrom(returnType)) {
            return handleMono(joinPoint, logged, log, fullMethodName, params, contextMap);
        } else if (Flux.class.isAssignableFrom(returnType)) {
            return handleFlux(joinPoint, logged, log, fullMethodName, params, contextMap);
        }

        // Blocking execution
        return handleBlocking(joinPoint, logged, log, fullMethodName, params);
    }

    @SuppressWarnings("unchecked")
    private Mono<?> handleMono(ProceedingJoinPoint joinPoint, Logged logged, LogUtil log,
                               String fullMethodName, String params, Map<String, String> contextMap) throws Throwable {
        long startTime = System.currentTimeMillis();
        logEntry(log, logged.level(), fullMethodName, params, logged.message());

        return ((Mono<?>) joinPoint.proceed())
            .contextWrite(ctx -> {
                if (contextMap != null) {
                    contextMap.forEach(MDC::put);
                }
                return ctx;
            })
            .doOnSuccess(result -> {
                restoreMdc(contextMap);
                long duration = System.currentTimeMillis() - startTime;
                if (logged.logResult() && result != null) {
                    logResult(log, fullMethodName, result, duration);
                }
                logExit(log, logged.level(), fullMethodName, true, duration);
            })
            .doOnError(error -> {
                restoreMdc(contextMap);
                long duration = System.currentTimeMillis() - startTime;
                log.error(fullMethodName + " failed", error, "duration={}ms", duration);
                logExit(log, logged.level(), fullMethodName, false, duration);
            });
    }

    @SuppressWarnings("unchecked")
    private Flux<?> handleFlux(ProceedingJoinPoint joinPoint, Logged logged, LogUtil log,
                               String fullMethodName, String params, Map<String, String> contextMap) throws Throwable {
        long startTime = System.currentTimeMillis();
        logEntry(log, logged.level(), fullMethodName, params, logged.message());

        return ((Flux<?>) joinPoint.proceed())
            .contextWrite(ctx -> {
                if (contextMap != null) {
                    contextMap.forEach(MDC::put);
                }
                return ctx;
            })
            .doOnComplete(() -> {
                restoreMdc(contextMap);
                long duration = System.currentTimeMillis() - startTime;
                logExit(log, logged.level(), fullMethodName, true, duration);
            })
            .doOnError(error -> {
                restoreMdc(contextMap);
                long duration = System.currentTimeMillis() - startTime;
                log.error(fullMethodName + " failed", error, "duration={}ms", duration);
                logExit(log, logged.level(), fullMethodName, false, duration);
            });
    }

    private Object handleBlocking(ProceedingJoinPoint joinPoint, Logged logged, LogUtil log,
                                   String fullMethodName, String params) throws Throwable {
        long startTime = System.currentTimeMillis();
        logEntry(log, logged.level(), fullMethodName, params, logged.message());

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            if (logged.logResult() && result != null) {
                logResult(log, fullMethodName, result, duration);
            }

            logExit(log, logged.level(), fullMethodName, true, duration);
            return result;

        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error(fullMethodName + " failed", e, "duration={}ms", duration);
            logExit(log, logged.level(), fullMethodName, false, duration);
            throw e;
        }
    }

    private void restoreMdc(Map<String, String> contextMap) {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }

    private void logEntry(LogUtil log, Logged.LogLevel level, String methodName, String params, String message) {
        String correlationId = MDC.get("correlationId");
        String logMsg = params.isEmpty()
            ? String.format("[ENTRY] %s | correlationId=%s", methodName, correlationId != null ? correlationId : "N/A")
            : String.format("[ENTRY] %s | %s | correlationId=%s", methodName, params, correlationId != null ? correlationId : "N/A");

        if (!message.isEmpty()) {
            logMsg += " | " + message;
        }

        switch (level) {
            case TRACE -> { if (log.isTraceEnabled()) log.getLogger().trace(logMsg); }
            case DEBUG -> { if (log.isDebugEnabled()) log.getLogger().debug(logMsg); }
            case INFO -> log.getLogger().info(logMsg);
        }
    }

    private void logExit(LogUtil log, Logged.LogLevel level, String methodName, boolean success, long duration) {
        String logMsg = String.format("[EXIT] %s | success=%s | duration=%dms", methodName, success, duration);

        switch (level) {
            case TRACE -> { if (log.isTraceEnabled()) log.getLogger().trace(logMsg); }
            case DEBUG -> { if (log.isDebugEnabled()) log.getLogger().debug(logMsg); }
            case INFO -> log.getLogger().info(logMsg);
        }
    }

    private void logResult(LogUtil log, String methodName, Object result, long duration) {
        String resultStr = formatResult(result);
        log.result(methodName + " completed", "result={} | duration={}ms", resultStr, duration);
    }

    private String buildParamsString(ProceedingJoinPoint joinPoint, MethodSignature signature, String[] excludeParams) {
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        if (parameters.length == 0) {
            return "";
        }

        Set<String> excludeSet = new HashSet<>(DEFAULT_EXCLUDED_PARAMS);
        excludeSet.addAll(Arrays.asList(excludeParams));

        StringJoiner joiner = new StringJoiner(" | ");
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();

            if (excludeSet.stream().anyMatch(e -> paramName.toLowerCase().contains(e.toLowerCase()))) {
                joiner.add(paramName + "=***");
                continue;
            }

            Object value = args[i];
            String valueStr = formatValue(value);
            joiner.add(paramName + "=" + valueStr);
        }

        return joiner.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String str) {
            return str.length() > 100 ? str.substring(0, 100) + "...(truncated)" : str;
        }
        if (value instanceof byte[]) return "byte[" + ((byte[]) value).length + "]";
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[" + java.lang.reflect.Array.getLength(value) + "]";
        }
        if (value instanceof java.util.Collection<?> col) {
            return value.getClass().getSimpleName() + "[" + col.size() + "]";
        }
        String str = value.toString();
        return str.length() > 100 ? str.substring(0, 100) + "...(truncated)" : str;
    }

    private String formatResult(Object result) {
        if (result == null) return "null";
        if (result instanceof java.util.Collection<?> col) {
            return result.getClass().getSimpleName() + "[" + col.size() + " items]";
        }
        if (result instanceof java.util.Optional<?> opt) {
            return opt.isPresent() ? "Optional[present]" : "Optional[empty]";
        }
        String str = result.toString();
        return str.length() > 200 ? str.substring(0, 200) + "...(truncated)" : str;
    }
}
