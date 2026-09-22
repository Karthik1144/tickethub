package com.tickethub.auth.security;

import com.tickethub.user.domain.Role;

/** Principal placed in the SecurityContext by the JWT filter. */
public record AuthenticatedUser(Long id, String email, Role role) {
}
