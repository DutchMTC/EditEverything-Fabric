package com.dutchmtc.ee.internalcommand;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InternalCommandModule {
    String name() default "";

    /**
     * @return true if the name of the type should be used as a prefix, false
     * otherwise
     */
    boolean useBaseName() default true;
}
