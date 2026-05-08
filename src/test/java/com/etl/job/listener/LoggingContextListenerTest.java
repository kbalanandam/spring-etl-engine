package com.etl.job.listener;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.etl.common.util.ConfigBundlePathAliasResolver;
import com.etl.config.exception.ConfigException;
import com.etl.config.ColumnConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.logging.RunLoggingContext;
import com.etl.runtime.job.JobConfigPaths;
import com.etl.runtime.job.JobHierarchyLoggingSupport;
import com.etl.runtime.job.JobRunMode;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobRuntimeDescriptorAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingContextListenerTest {

    private static final String CUSTOMER_LOAD_JOB_CONFIG = resourcePath("config-jobs/customer-load/job-config.yaml");
    private final Logger jobListenerLogger = (Logger) LoggerFactory.getLogger(JobCompletionNotificationListener.class);
    private final Logger stepListenerLogger = (Logger) LoggerFactory.getLogger(StepLoggingContextListener.class);

    private final JobCompletionNotificationListener jobCompletionNotificationListener = new JobCompletionNotificationListener();
    private final StepLoggingContextListener stepLoggingContextListener = new StepLoggingContextListener();

    @AfterEach
    void tearDown() {
        RunLoggingContext.clearAll();
        jobListenerLogger.detachAndStopAllAppenders();
        stepListenerLogger.detachAndStopAllAppenders();
    }

    @Test
    void jobListenerPopulatesAndClearsJobScopeMdc() {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("scenario", "customer-load")
                .addString("scenarioLogKey", "2026-04-23/customer-load")
                .addString("runCorrelationId", "20260423-123000-000")
                .addString("mainFlow", "customer-load-main-flow")
                .addString("subFlow", "default-subflow")
                .addString("recoveryPolicy", "rerun-from-start")
                .addString("runMode", "explicit-job")
                .addString("jobConfigPath", CUSTOMER_LOAD_JOB_CONFIG)
                .toJobParameters();

        JobExecution jobExecution = mock(JobExecution.class);
        JobInstance jobInstance = mock(JobInstance.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParameters);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("etlJob");
        when(jobExecution.getId()).thenReturn(42L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(5));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

        jobCompletionNotificationListener.beforeJob(jobExecution);

        assertEquals("customer-load", MDC.get(RunLoggingContext.SCENARIO));
        assertEquals("2026-04-23/customer-load", MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertEquals("20260423-123000-000", MDC.get(RunLoggingContext.RUN_CORRELATION_ID));
        assertEquals("customer-load-main-flow", MDC.get(RunLoggingContext.MAIN_FLOW));
        assertEquals("default-subflow", MDC.get(RunLoggingContext.SUB_FLOW));
        assertEquals("rerun-from-start", MDC.get(RunLoggingContext.RECOVERY_POLICY));
        assertEquals("42", MDC.get(RunLoggingContext.JOB_EXECUTION_ID));
        assertEquals("etlJob", MDC.get(RunLoggingContext.JOB_NAME));

        jobCompletionNotificationListener.afterJob(jobExecution);

        assertEquals("customer-load", MDC.get(RunLoggingContext.SCENARIO));
        assertEquals("2026-04-23/customer-load", MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertEquals("20260423-123000-000", MDC.get(RunLoggingContext.RUN_CORRELATION_ID));
        assertEquals("customer-load-main-flow", MDC.get(RunLoggingContext.MAIN_FLOW));
        assertEquals("default-subflow", MDC.get(RunLoggingContext.SUB_FLOW));
        assertEquals("rerun-from-start", MDC.get(RunLoggingContext.RECOVERY_POLICY));
        assertNull(MDC.get(RunLoggingContext.JOB_EXECUTION_ID));
        assertNull(MDC.get(RunLoggingContext.JOB_NAME));
    }

    @Test
    void stepListenerAddsAndClearsStepNameFromMdc() {
        StepExecution stepExecution = mock(StepExecution.class);
        JobExecution jobExecution = mock(JobExecution.class);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("mainFlow", "customer-load-main-flow")
                .addString("subFlow", "default-subflow")
                .addString("recoveryPolicy", "rerun-from-start")
                .toJobParameters();
        ExecutionContext executionContext = new ExecutionContext();
        executionContext.putString(JobHierarchyLoggingSupport.STEP_SUB_FLOW_NAME_KEY, "customers-step-subflow");
        executionContext.putInt(JobHierarchyLoggingSupport.STEP_SUB_FLOW_ORDER_KEY, 0);
        executionContext.putString(JobHierarchyLoggingSupport.STEP_DEPENDS_ON_SUB_FLOWS_KEY, "none");
        executionContext.putString(JobHierarchyLoggingSupport.STEP_CONSUMES_HANDOFFS_KEY, "none");
        executionContext.putString(JobHierarchyLoggingSupport.STEP_PRODUCES_HANDOFFS_KEY, "Customers");
        executionContext.putString(JobHierarchyLoggingSupport.STEP_UPSTREAM_STEPS_KEY, "none");
        executionContext.putString(JobHierarchyLoggingSupport.STEP_LINK_TYPES_KEY, "none");
        executionContext.putString(JobHierarchyLoggingSupport.STEP_LINK_CONTROL_SUMMARY_KEY, "none");
        executionContext.putString(JobHierarchyLoggingSupport.STEP_SUMMARY_KEY, "Customers step summary");
        when(stepExecution.getStepName()).thenReturn("etlStep_0_Customers");
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        when(jobExecution.getJobParameters()).thenReturn(jobParameters);
        when(stepExecution.getExecutionContext()).thenReturn(executionContext);
        when(stepExecution.getId()).thenReturn(11L);
        when(stepExecution.getExitStatus()).thenReturn(org.springframework.batch.core.ExitStatus.COMPLETED);

        ListAppender<ILoggingEvent> appender = attachAppender(stepListenerLogger);

        stepLoggingContextListener.beforeStep(stepExecution);
        assertEquals("etlStep_0_Customers", MDC.get(RunLoggingContext.STEP_NAME));
        assertEquals("customer-load-main-flow", MDC.get(RunLoggingContext.MAIN_FLOW));
        assertEquals("customers-step-subflow", MDC.get(RunLoggingContext.SUB_FLOW));
        assertEquals("rerun-from-start", MDC.get(RunLoggingContext.RECOVERY_POLICY));
        assertTrue(appender.list.get(0).getFormattedMessage().contains("subFlow=customers-step-subflow"));
        assertTrue(appender.list.get(0).getFormattedMessage().contains("producesHandoffAliases=Customers"));
        assertTrue(appender.list.get(0).getFormattedMessage().contains("stepSummary=Customers step summary"));

        stepLoggingContextListener.afterStep(stepExecution);
        assertNull(MDC.get(RunLoggingContext.STEP_NAME));
        assertEquals("default-subflow", MDC.get(RunLoggingContext.SUB_FLOW));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("stepSubFlowOrder=0"));
    }

    @Test
    void failedJobKeepsScenarioContextWhileLoggingFailureDetails() {
		ListAppender<ILoggingEvent> appender = attachAppender(jobListenerLogger);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("scenario", "department-load")
                .addString("scenarioLogKey", "2026-04-23/department-load")
                .addString("runCorrelationId", "20260423-124500-000")
                .addString("mainFlow", "department-load-main-flow")
                .addString("subFlow", "default-subflow")
                .addString("recoveryPolicy", "rerun-from-start")
                .toJobParameters();

        JobExecution jobExecution = mock(JobExecution.class);
        JobInstance jobInstance = mock(JobInstance.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParameters);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("etlJob");
        when(jobExecution.getId()).thenReturn(77L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(3));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());
        when(jobExecution.getAllFailureExceptions()).thenReturn(List.of(new ConfigException("boom")));

        jobCompletionNotificationListener.beforeJob(jobExecution);
        jobCompletionNotificationListener.afterJob(jobExecution);

        assertEquals("department-load", MDC.get(RunLoggingContext.SCENARIO));
        assertEquals("2026-04-23/department-load", MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertEquals("department-load-main-flow", MDC.get(RunLoggingContext.MAIN_FLOW));
        assertEquals("default-subflow", MDC.get(RunLoggingContext.SUB_FLOW));
        assertEquals("rerun-from-start", MDC.get(RunLoggingContext.RECOVERY_POLICY));
        assertNull(MDC.get(RunLoggingContext.JOB_EXECUTION_ID));
		assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("JOB_FAILURE event=job_failure")
				&& event.getFormattedMessage().contains("failureCategory=config")
				&& event.getFormattedMessage().contains("exceptionType=ConfigException")
				&& event.getFormattedMessage().contains("rootCause=boom")));
    }

  @Test
  void jobListenerLogsHierarchyPlanAndBlockedSubflowSummaryWhenUpstreamFails() {
    JobRuntimeDescriptor descriptor = ordersDescriptor();
    JobCompletionNotificationListener listener = new JobCompletionNotificationListener(descriptor);
    ListAppender<ILoggingEvent> appender = attachAppender(jobListenerLogger);

    JobParameters jobParameters = new JobParametersBuilder()
        .addString("scenario", "orders-flow")
        .addString("scenarioLogKey", "2026-05-05/orders-flow")
        .addString("runCorrelationId", "20260505-101500-000")
        .addString("mainFlow", "orders-flow")
        .addString("subFlow", "default-subflow")
        .addString("recoveryPolicy", "rerun-from-start")
        .addString("runMode", "explicit-job")
        .addString("jobConfigPath", "C:/scenarios/orders/job-config.yaml")
        .toJobParameters();

    JobExecution jobExecution = mock(JobExecution.class);
    JobInstance jobInstance = mock(JobInstance.class);
    StepExecution failedStep = mock(StepExecution.class);
    when(jobExecution.getJobParameters()).thenReturn(jobParameters);
    when(jobExecution.getJobInstance()).thenReturn(jobInstance);
    when(jobInstance.getJobName()).thenReturn("etlJob");
    when(jobExecution.getId()).thenReturn(88L);
    when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
    when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(4));
    when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());
    when(jobExecution.getAllFailureExceptions()).thenReturn(List.of(new IllegalStateException("normalize failed")));
    when(jobExecution.getStepExecutions()).thenReturn(Set.of(failedStep));
    when(failedStep.getStepName()).thenReturn("normalize-orders");
    when(failedStep.getStatus()).thenReturn(BatchStatus.FAILED);

    listener.beforeJob(jobExecution);
    listener.afterJob(jobExecution);

    assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("MAIN_FLOW_PLAN event=main_flow_plan")));
    assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("SUBFLOW_PLAN event=subflow_plan")
        && event.getFormattedMessage().contains("subFlow=publish-orders-subflow")));
    assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("SUBFLOW_SUMMARY event=subflow_summary")
        && event.getFormattedMessage().contains("subFlow=publish-orders-subflow")
        && event.getFormattedMessage().contains("status=BLOCKED")
        && event.getFormattedMessage().contains("normalize-orders-subflow")
        && event.getFormattedMessage().contains("FAILED")));
  }

  private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
    logger.detachAndStopAllAppenders();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private JobRuntimeDescriptor ordersDescriptor() {
    SourceWrapper sourceWrapper = new SourceWrapper();
    sourceWrapper.setSources(List.of(
        csvSource("OrdersIn", "com.etl.model.source.orders", "input/orders.csv"),
        csvSource("OrdersValidated", "com.etl.model.source.validated", "target/orders-validated.csv")
    ));

    TargetWrapper targetWrapper = new TargetWrapper();
    targetWrapper.setTargets(List.of(
        csvTarget("OrdersValidated", "com.etl.model.target.validated", "target/orders-validated.csv"),
        csvTarget("OrdersFinal", "com.etl.model.target.final", "target/orders-final.csv")
    ));

    ProcessorConfig processorConfig = new ProcessorConfig();
    processorConfig.setType("default");
    processorConfig.setMappings(List.of(
        mapping("OrdersIn", "OrdersValidated"),
        mapping("OrdersValidated", "OrdersFinal")
    ));

    JobRuntimeDescriptorAssembler assembler = new JobRuntimeDescriptorAssembler();
    return assembler.assemble(
        "orders-flow",
        "C:/scenarios/orders/job-config.yaml",
        JobRunMode.EXPLICIT_JOB,
        new JobConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
        List.of(
            step("normalize-orders", "OrdersIn", "OrdersValidated"),
            step("publish-orders", "OrdersValidated", "OrdersFinal")
        ),
        sourceWrapper,
        targetWrapper,
        processorConfig
    );
  }

  private CsvSourceConfig csvSource(String sourceName, String packageName, String filePath) {
    return new CsvSourceConfig(sourceName, packageName, List.of(column("id"), column("status")), filePath, ",");
  }

  private CsvTargetConfig csvTarget(String targetName, String packageName, String filePath) {
    return new CsvTargetConfig(targetName, packageName, List.of(column("id"), column("status")), filePath, ",");
  }

  private ColumnConfig column(String name) {
    ColumnConfig column = new ColumnConfig();
    column.setName(name);
    column.setType("String");
    return column;
  }

  private ProcessorConfig.EntityMapping mapping(String sourceName, String targetName) {
    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource(sourceName);
    mapping.setTarget(targetName);
    ProcessorConfig.FieldMapping field = new ProcessorConfig.FieldMapping();
    field.setFrom("id");
    field.setTo("id");
    mapping.setFields(List.of(field));
    return mapping;
  }

  private com.etl.config.job.JobConfig.JobStepConfig step(String name, String source, String target) {
    com.etl.config.job.JobConfig.JobStepConfig step = new com.etl.config.job.JobConfig.JobStepConfig();
    step.setName(name);
    step.setSource(source);
    step.setTarget(target);
    return step;
  }

    private static String resourcePath(String resourceName) {
        try {
            String resolvedResourceName = ConfigBundlePathAliasResolver.resolveExistingResourceName(
                    LoggingContextListenerTest.class.getClassLoader(),
                    resourceName
            );
            return Path.of(Objects.requireNonNull(
                    LoggingContextListenerTest.class.getClassLoader().getResource(resolvedResourceName),
                    () -> "Missing test resource: " + resolvedResourceName
            ).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve test resource: " + resourceName, e);
        }
    }
}



