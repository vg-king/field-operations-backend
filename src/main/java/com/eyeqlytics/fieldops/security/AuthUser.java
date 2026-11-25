package com.eyeqlytics.fieldops.security;

import com.eyeqlytics.fieldops.identity.Role;

/** The authenticated principal, rebuilt from JWT claims on every request (stateless). */
public record AuthUser(Long id, String email, Role role, Long zoneId) {
    public boolean isAdmin() { return role == Role.ADMIN; }
}
