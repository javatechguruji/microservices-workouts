package com.tip.ecommerce.notification.security;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireAccess {
    String[] permissions() default {}; // any listed permission is sufficient
    String role() default "";
}
