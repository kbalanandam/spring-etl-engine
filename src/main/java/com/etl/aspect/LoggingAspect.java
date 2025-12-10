package com.etl.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LoggingAspect provides AOP-based logging for method execution,
 * excluding all getter/setter style methods.
 */
@Aspect
@Component
public class LoggingAspect {
    private static final long SLOW_THRESHOLD_MS = 50;
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut for all public methods in com.etl package,
     * EXCEPT getter and setter methods (getX(), setX(), isX()).
     */
    @Pointcut("execution(public * com.etl..*(..)) && " +
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

        // ENTRY LOG
        logger.info("Entering {}.{}() with arguments: {}", className, methodName, args);

        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;

        // Determine if slow
        boolean isSlow = duration > SLOW_THRESHOLD_MS;

        String prefix = isSlow ? "SLOW :: " : "";

        // EXIT LOG
        logger.info("{}Exiting {}.{}(), execution time: {} ms",
                prefix, className, methodName, duration);

        return result;
    }
}
