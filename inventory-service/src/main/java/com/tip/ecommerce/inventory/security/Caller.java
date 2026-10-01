package com.tip.ecommerce.inventory.security;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Trusted gateway headers for this POC; this class does not validate JWTs. */
public record Caller(String subject, String username, Set<String> roles,
                     Set<String> permissions, String tenant) {
    public static Caller from(HttpServletRequest request) {
        String subject = request.getHeader("X-Auth-Subject");
        String username = request.getHeader("X-Auth-Username");
        String tenant = request.getHeader("X-Auth-Tenant");
        if (subject == null || subject.isBlank() || username == null || username.isBlank()
                || tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Gateway identity headers required");
        }
        return new Caller(subject, username, values(request, "X-Auth-Roles"),
                values(request, "X-Auth-Permissions"), tenant);
    }
    private static Set<String> values(HttpServletRequest r, String name) {
        String value = r.getHeader(name);
        if (value == null || value.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(value.split(",")));
    }
    public boolean has(String permission) { return permissions.contains(permission); }
    public boolean admin() { return roles.contains("admin"); }
    public void requireOwner(String owner, String resourceTenant, String anyPermission) {
        if (!tenant.equals(resourceTenant) || (!username.equals(owner) && !has(anyPermission))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner/tenant policy denied access");
        }
    }
}
