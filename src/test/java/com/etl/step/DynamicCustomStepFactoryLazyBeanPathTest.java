package com.etl.step;

import com.etl.config.job.JobConfig;
import com.etl.exception.config.ConfigException;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.ListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicCustomStepFactoryLazyBeanPathTest {

    @Test
    void getHandlerFailsFastWhenProviderBeanIsMissingCustomStepBinding() {
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(CustomStepProvider.class, true, false)).thenReturn(new String[]{"missingBinding"});
        when(beanFactory.findAnnotationOnBean("missingBinding", CustomStepBinding.class)).thenReturn(null);

        DynamicCustomStepFactory factory = new DynamicCustomStepFactory(beanFactory);

        ConfigException exception = assertThrows(ConfigException.class,
                () -> factory.getHandler("audit-step", customConfig("audit")));

        assertTrue(exception.getMessage().contains("must declare @CustomStepBinding(type=...)"));
    }

    @Test
    void getHandlerFailsFastWhenProviderBindingTypeIsBlank() {
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(CustomStepProvider.class, true, false)).thenReturn(new String[]{"blank"});
        when(beanFactory.findAnnotationOnBean("blank", CustomStepBinding.class))
                .thenReturn(BlankTypeProvider.class.getAnnotation(CustomStepBinding.class));

        DynamicCustomStepFactory factory = new DynamicCustomStepFactory(beanFactory);

        ConfigException exception = assertThrows(ConfigException.class,
                () -> factory.getHandler("audit-step", customConfig("audit")));

        assertTrue(exception.getMessage().contains("declares a blank @CustomStepBinding type"));
    }

    @Test
    void getHandlerUsesOnlyProvidersBoundToRequestedType() {
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(CustomStepProvider.class, true, false))
                .thenReturn(new String[]{"other", "wanted"});
        when(beanFactory.findAnnotationOnBean("other", CustomStepBinding.class))
                .thenReturn(OtherTypeProvider.class.getAnnotation(CustomStepBinding.class));
        when(beanFactory.findAnnotationOnBean("wanted", CustomStepBinding.class))
                .thenReturn(WantedTypeProvider.class.getAnnotation(CustomStepBinding.class));

        WantedTypeProvider wantedProvider = new WantedTypeProvider();
        when(beanFactory.getBean("wanted", CustomStepProvider.class)).thenReturn(wantedProvider);

        DynamicCustomStepFactory factory = new DynamicCustomStepFactory(beanFactory);

        CustomStepHandler handler = factory.getHandler("wanted-step", customConfig("wantedType"));

        assertSame(wantedProvider.expectedHandler(), handler);
        verify(beanFactory, never()).getBean("other", CustomStepProvider.class);
    }

    @Test
    void getHandlerPrefersOverrideProviderWhenMultipleBeansShareType() {
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(CustomStepProvider.class, true, false))
                .thenReturn(new String[]{"base", "override"});
        when(beanFactory.findAnnotationOnBean("base", CustomStepBinding.class))
                .thenReturn(BaseAuditProvider.class.getAnnotation(CustomStepBinding.class));
        when(beanFactory.findAnnotationOnBean("override", CustomStepBinding.class))
                .thenReturn(OverrideAuditProvider.class.getAnnotation(CustomStepBinding.class));

        OverrideAuditProvider overrideProvider = new OverrideAuditProvider();
        when(beanFactory.getBean("override", CustomStepProvider.class)).thenReturn(overrideProvider);

        DynamicCustomStepFactory factory = new DynamicCustomStepFactory(beanFactory);

        CustomStepHandler handler = factory.getHandler("audit-step", customConfig("audit"));

        assertSame(overrideProvider.expectedHandler(), handler);
        verify(beanFactory, never()).getBean("base", CustomStepProvider.class);
    }

    @Test
    void getHandlerFailsFastWhenDuplicateProvidersLackSingleOverrideWinner() {
        ListableBeanFactory beanFactory = mock(ListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(CustomStepProvider.class, true, false))
                .thenReturn(new String[]{"first", "second"});
        when(beanFactory.findAnnotationOnBean("first", CustomStepBinding.class))
                .thenReturn(FirstDuplicateProvider.class.getAnnotation(CustomStepBinding.class));
        when(beanFactory.findAnnotationOnBean("second", CustomStepBinding.class))
                .thenReturn(SecondDuplicateProvider.class.getAnnotation(CustomStepBinding.class));

        DynamicCustomStepFactory factory = new DynamicCustomStepFactory(beanFactory);

        ConfigException exception = assertThrows(ConfigException.class,
                () -> factory.getHandler("audit-step", customConfig("audit")));

        assertTrue(exception.getMessage().contains("Multiple custom step providers are registered for type 'audit'"));
        assertTrue(exception.getMessage().contains("first"));
        assertTrue(exception.getMessage().contains("second"));
    }

    private static JobConfig.CustomStepConfig customConfig(String type) {
        JobConfig.CustomStepConfig config = new JobConfig.CustomStepConfig();
        config.setType(type);
        return config;
    }

    private abstract static class BaseTestProvider implements CustomStepProvider {
        private final CustomStepHandler handler = (contribution, context) -> RepeatStatus.FINISHED;

        CustomStepHandler expectedHandler() {
            return handler;
        }

        @Override
        public CustomStepHandler createHandler(JobConfig.CustomStepConfig config) {
            return handler;
        }
    }

    @CustomStepBinding(type = "   ")
    private static final class BlankTypeProvider extends BaseTestProvider {
    }

    @CustomStepBinding(type = "otherType")
    private static final class OtherTypeProvider extends BaseTestProvider {
    }

    @CustomStepBinding(type = "wantedType")
    private static final class WantedTypeProvider extends BaseTestProvider {
    }

    @CustomStepBinding(type = "audit")
    private static final class BaseAuditProvider extends BaseTestProvider {
    }

    @CustomStepBinding(type = "audit", override = true)
    private static final class OverrideAuditProvider extends BaseTestProvider {
    }

    @CustomStepBinding(type = "audit")
    private static final class FirstDuplicateProvider extends BaseTestProvider {
    }

    @CustomStepBinding(type = "audit")
    private static final class SecondDuplicateProvider extends BaseTestProvider {
    }
}




