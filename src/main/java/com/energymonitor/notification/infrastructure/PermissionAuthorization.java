package com.energymonitor.notification.infrastructure;

import com.energymonitor.notification.adapter.out.security.RemotePermissionCheck;
import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Enforces a global permission code on a route, asking the security service which codes the
 * caller holds.
 *
 * <p>The permissions are not carried in the access token. The answer is cached 30 seconds, so a
 * role granted or revoked, or an account blocked, takes effect within that time instead of when
 * the token expires.
 *
 * <p>An anonymous caller is denied, which the filter chain turns into 401; an authenticated
 * caller without the code, or whose account is gone, is denied with 403. When the security
 * service does not answer, {@link SecurityServiceUnavailableException} goes up to
 * {@link SecurityServiceUnavailableFilter}, which answers 503: the caller is neither let in nor
 * told it lacks a permission it may well hold.
 */
@Component
public class PermissionAuthorization {

    private final RemotePermissionCheck permissions;

    public PermissionAuthorization(RemotePermissionCheck permissions) {
        this.permissions = permissions;
    }

    /**
     * @param permissionCode the code the caller must hold
     * @return a rule granting access only to callers holding that code
     */
    public AuthorizationManager<RequestAuthorizationContext> require(String permissionCode) {
        return (authentication, context) ->
                new AuthorizationDecision(holds(authentication.get(), permissionCode));
    }

    private boolean holds(Authentication authentication, String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return permissions.holds(authentication.getName(), permissionCode);
    }
}
