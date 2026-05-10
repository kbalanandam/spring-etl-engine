package com.etl.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAspectTest {

    private final LoggingAspect loggingAspect = new LoggingAspect();
    private final Logger aspectLogger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final Level originalLevel = aspectLogger.getLevel();

    @AfterEach
    void tearDown() {
        aspectLogger.setLevel(originalLevel);
        aspectLogger.detachAndStopAllAppenders();
    }

    @Test
    void logExecutionWritesDebugEntryAndExitForNormalCalls() throws Throwable {
        ListAppender<ILoggingEvent> appender = attachAppender(Level.DEBUG);
        ProceedingJoinPoint joinPoint = joinPoint("com.etl.config.ConfigLoader", "sourceWrapper", new Object[]{"source-config.yaml"}, "ok", 0L);

        Object result = loggingAspect.logExecution(joinPoint);

        assertEquals("ok", result);
        assertEquals(2, appender.list.size());
        assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
        assertEquals(Level.DEBUG, appender.list.get(1).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("Entering com.etl.config.ConfigLoader.sourceWrapper()"));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("Exiting com.etl.config.ConfigLoader.sourceWrapper()"));
    }

    @Test
    void logExecutionWritesOnlySlowInfoAtInfoLevel() throws Throwable {
        ListAppender<ILoggingEvent> appender = attachAppender(Level.INFO);
        ProceedingJoinPoint joinPoint = joinPoint("com.etl.config.BatchConfig", "buildSteps", new Object[0], "done", 80L);

        Object result = loggingAspect.logExecution(joinPoint);

        assertEquals("done", result);
        assertEquals(1, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("SLOW_CALL class=com.etl.config.BatchConfig method=buildSteps"));
    }

    private ListAppender<ILoggingEvent> attachAppender(Level level) {
        aspectLogger.detachAndStopAllAppenders();
        aspectLogger.setLevel(level);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
        return appender;
    }

    private ProceedingJoinPoint joinPoint(String className,
                                          String methodName,
                                          Object[] args,
                                          Object result,
                                          long delayMs) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(className);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            if (delayMs > 0L) {
                Thread.sleep(delayMs);
            }
            return result;
        });
        return joinPoint;
    }
}

