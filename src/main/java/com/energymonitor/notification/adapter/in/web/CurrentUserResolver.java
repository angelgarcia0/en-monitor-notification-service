package com.energymonitor.notification.adapter.in.web;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated caller from the access token ({@code sub} = {@code id_user}).
 */
@Component
public class CurrentUserResolver {

    public String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || token.getToken().getSubject() == null || token.getToken().getSubject().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("no authenticated user in the request");
        }
        return token.getToken().getSubject();
    }
}
