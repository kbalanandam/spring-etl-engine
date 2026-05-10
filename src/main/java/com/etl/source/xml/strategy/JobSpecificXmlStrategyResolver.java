package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Resolves job-specific XML source strategies by Spring bean name.
 */
@Component
public class JobSpecificXmlStrategyResolver {

    private final ApplicationContext applicationContext;

    public JobSpecificXmlStrategyResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public XmlSourceStrategy resolve(XmlSourceRuntimeContext context) {
        String beanName = context.getRequiredJobSpecificStrategyBean();
        if (!applicationContext.containsBean(beanName)) {
            throw new IllegalArgumentException(
                    "No job-specific XML strategy bean named '" + beanName + "' found for source '"
                            + context.getSourceName() + "'."
            );
        }
        XmlSourceStrategy strategy = applicationContext.getBean(beanName, XmlSourceStrategy.class);
        if (!strategy.supports(context)) {
            throw new IllegalArgumentException(
                    "Job-specific XML strategy bean '" + beanName + "' does not support source '"
                            + context.getSourceName() + "'."
            );
        }
        return strategy;
    }
}

