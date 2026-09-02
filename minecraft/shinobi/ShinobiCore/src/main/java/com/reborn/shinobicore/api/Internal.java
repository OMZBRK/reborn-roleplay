package com.reborn.shinobicore.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Implementation detail. May change or disappear in any release
 * without notice. Dependent plugins must not compile against
 * anything annotated {@code @Internal} — resolve the matching
 * {@link Stable} interface from Bukkit's ServicesManager instead.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Internal {
}
