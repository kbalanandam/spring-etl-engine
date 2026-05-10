package com.etl.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LoggingAspect provides low-noise AOP logging around orchestration methods.
 *
 * <p>Generic entry/exit tracing stays at DEBUG so the normal INFO log stream is
 * reserved for explicit lifecycle markers such as RUN_EVENT, STEP_EVENT,
 * STEP_PLAN, and RUN_SUMMARY. Hot-path runtime packages are excluded because
 * per-record interception can generate excessive log volume.</p>
 */
@Aspect
@Component
public class LoggingAspect {
    private static final long SLOW_THRESHOLD_MS = 50;
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut for public orchestration methods in com.etl,
     * excluding record/data-carrier types, hot-path runtime packages,
     * and getter/setter style methods.
     */
    @Pointcut("execution(public * com.etl..*(..)) && " +
            "!execution(* com.etl.config.RunConfigurationMetadata.*(..)) && " +
            "!execution(* com.etl.runtime.job..*(..)) && " +
            "!execution(* com.etl.reader..*(..)) && " +
            "!execution(* com.etl.writer..*(..)) && " +
            "!execution(* com.etl.processor..*(..)) && " +
            "!execution(* com.etl.mapping..*(..)) && " +
            "!execution(* com.etl.source..*(..)) && " +
            "!execution(* com.etl.target..*(..)) && " +
            "!execution(* get*()) && " +
            "!execution(* is*()) && " +
            "!execution(* set*(..))")
    public void applicationMethods() {
    }

    @Around("applicationMethods()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        if (logger.isDebugEnabled()) {
            logger.debug("Entering {}.{}() with arguments: {}", className, methodName, args);
        }

        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;

        boolean isSlow = duration > SLOW_THRESHOLD_MS;

        if (isSlow) {
            logger.info("SLOW_CALL class={} method={} executionTimeMs={}", className, methodName, duration);
        } else if (logger.isDebugEnabled()) {
            logger.debug("Exiting {}.{}(), execution time: {} ms", className, methodName, duration);
        }

        return result;
    }
}
