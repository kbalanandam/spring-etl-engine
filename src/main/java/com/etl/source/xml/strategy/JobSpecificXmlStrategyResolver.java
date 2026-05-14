package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Resolves job-specific XML source strategies by Spring bean name.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This resolver is used only when the selected XML source explicitly chooses the
 * {@code JOB_SPECIFIC_XML} strategy mode. It looks up the configured Spring bean, verifies that it
 * implements {@link XmlSourceStrategy}, and ensures the resulting strategy supports the active
 * source context before the runtime uses it.</p>
 */
@Component
public class JobSpecificXmlStrategyResolver {

    private final ApplicationContext applicationContext;

    public JobSpecificXmlStrategyResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Resolves the required job-specific XML strategy bean for the supplied runtime context.
     *
     * @throws IllegalArgumentException when the bean does not exist or the resolved strategy does
     * not support the active XML source context
     */
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

