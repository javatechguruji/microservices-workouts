package com.tip.ecommerce.order.security;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireAccess {
    String[] permissions() default {}; // any listed permission is sufficient
    String role() default "";
}
