package com.tickethub.auth.security;

import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Small helper so services never talk to SecurityContextHolder directly. */
@Component
public class CurrentUser {

    public AuthenticatedUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        return user;
    }

    public Long requireId() {
        return require().id();
    }
}
