package com.etl.runtime.job;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
/**
 * Descriptor for one named subflow within the selected MainFlow.
 */
public record JobSubFlowDescriptor(
String subFlowName,
int subFlowOrder,
List<String> stepNames,
JobSubFlowExecutionStatus initialStatus,
    JobSubFlowControlDescriptor control,
List<String> dependsOnSubFlowNames,
List<String> consumesHandoffAliases,
List<String> producesHandoffAliases,
String summary
) {
public JobSubFlowDescriptor {
subFlowName = requireNonBlank(subFlowName, "subFlowName");
if (subFlowOrder < 0) {
throw new IllegalArgumentException("subFlowOrder must not be negative.");
}
stepNames = normalizeList(stepNames);
if (stepNames.isEmpty()) {
throw new IllegalArgumentException("stepNames must contain at least one step.");
}
if (initialStatus == null) {
throw new IllegalArgumentException("initialStatus must not be null.");
}
        control = control == null ? JobSubFlowControlDescriptor.defaultSequentialControl(!normalizeList(consumesHandoffAliases).isEmpty()) : control;
    dependsOnSubFlowNames = normalizeList(dependsOnSubFlowNames);
    consumesHandoffAliases = normalizeList(consumesHandoffAliases);
producesHandoffAliases = normalizeList(producesHandoffAliases);
    summary = summary == null || summary.isBlank()
        ? buildSummary(subFlowName, subFlowOrder, stepNames, initialStatus, control, dependsOnSubFlowNames)
: summary.trim();
}
public boolean dependsOnSubFlow(String subFlowName) {
return subFlowName != null && dependsOnSubFlowNames.contains(subFlowName);
}
public boolean consumesHandoffAlias(String alias) {
return alias != null && consumesHandoffAliases.contains(alias);
}
public boolean producesHandoffAlias(String alias) {
return alias != null && producesHandoffAliases.contains(alias);
}

  public boolean startsAfter(JobSubFlowExecutionStatus status) {
    return control.startsAfter(status);
  }

  public boolean blocksOn(JobSubFlowExecutionStatus status) {
    return control.blocksOn(status);
  }
private static List<String> normalizeList(List<String> values) {
if (values == null || values.isEmpty()) {
return List.of();
}
return List.copyOf(values.stream()
.filter(Objects::nonNull)
.map(String::trim)
.filter(value -> !value.isBlank())
.collect(Collectors.toCollection(LinkedHashSet::new)));
}
private static String buildSummary(String subFlowName,
                                  int subFlowOrder,
                                  List<String> stepNames,
                                  JobSubFlowExecutionStatus initialStatus,
                                    JobSubFlowControlDescriptor control,
                                  List<String> dependsOnSubFlowNames) {
String dependencies = dependsOnSubFlowNames.isEmpty() ? "none" : String.join(",", dependsOnSubFlowNames);
return "subFlow='" + subFlowName + "', order=" + subFlowOrder
+ ", steps=" + String.join(",", stepNames)
+ ", initialStatus=" + initialStatus
        + ", dependsOn=" + dependencies
        + ", control={" + control.summary() + "}";
}
private static String requireNonBlank(String value, String field) {
Objects.requireNonNull(value, field + " must not be null.");
if (value.isBlank()) {
throw new IllegalArgumentException(field + " must not be blank.");
}
return value.trim();
}
}
