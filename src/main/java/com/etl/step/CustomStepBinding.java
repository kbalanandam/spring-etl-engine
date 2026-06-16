package com.etl.step;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares how a custom step provider is bound to `steps[].custom.type`.
 *
 * <p>The factory reads this annotation from provider beans and resolves providers lazily,
 * so the type declared here is the single source of truth for runtime dispatch.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomStepBinding {

    /**
     * Logical type name referenced from `job-config.yaml -> steps[].custom.type`.
     */
    String type();

    /**
     * Marks this provider as an explicit replacement when multiple providers bind to the same type.
     */
    boolean override() default false;
}
