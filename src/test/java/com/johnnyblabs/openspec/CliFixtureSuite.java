package com.johnnyblabs.openspec;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plain-JUnit test class that replays captured CLI releases ({@code FixtureVersions}). Those run as their own
 * suite, {@code cliFixtureTest}, which CI runs in parallel with {@code test}; {@code ./gradlew build} runs both.
 * {@code CliFixtureSuiteGuardTest} fails when such a class lacks this annotation.
 */
@Tag(CliFixtureSuite.TAG)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CliFixtureSuite {
    /** The JUnit tag {@code cliFixtureTest} includes and {@code test} excludes. */
    String TAG = "cli-fixtures";
}
