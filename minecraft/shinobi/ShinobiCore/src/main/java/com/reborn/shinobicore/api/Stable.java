package com.reborn.shinobicore.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contract surface. Dependent plugins may compile against anything
 * annotated {@code @Stable}; it must not be broken without a
 * deliberate, versioned migration. Everything not marked
 * {@code @Stable} should be treated as {@link Internal}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Stable {
}
