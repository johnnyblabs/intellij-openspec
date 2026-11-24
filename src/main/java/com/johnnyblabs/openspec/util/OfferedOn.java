package com.johnnyblabs.openspec.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The CLI releases on which the plugin offers a {@link CliCommands} invocation: {@code [since, until)}, with an empty
 * {@code until} for "still offered". Each value names the floor constant the calling service already gates on, so the
 * version matrix test checks the same range the user sees: the invocation must resolve on every release inside it and
 * be refused outside it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OfferedOn {
    String since() default CliCommands.FLOOR;

    String until() default "";
}
