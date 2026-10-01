package com.tip.ecommerce.order.security;
import jakarta.servlet.http.*;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class HeaderAuthorizationConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method)) return true;
                Caller caller = Caller.from(request);
                RequireAccess rule = method.getMethodAnnotation(RequireAccess.class);
                if (rule == null) rule = method.getBeanType().getAnnotation(RequireAccess.class);
                // Fail closed for application handlers without an explicit authorization policy.
                if (rule == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No endpoint policy");
                if (!rule.role().isEmpty() && !caller.roles().contains(rule.role()))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role required");
                if (rule.permissions().length > 0 && Arrays.stream(rule.permissions()).noneMatch(caller::has))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission required");
                request.setAttribute("caller", caller);
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns("/actuator/health/**", "/actuator/health", "/error");
    }
}
