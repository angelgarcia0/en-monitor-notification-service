package com.energymonitor.notification.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Output port for who an account is and how to reach it: the members a message goes to and the
 * accounts an administrator notification is about. This service stores user identifiers only;
 * names and addresses belong to the security service.
 *
 * <p>Answered by the security service and cached 60 seconds.
 */
public interface UserProfileQuery {

    /**
     * Looks up the profiles of the given accounts.
     *
     * @param userIds account identifiers; duplicates and unknown ones are fine
     * @return the profile of every active account found, keyed by its identifier; an identifier
     *         with no active account is simply absent
     * @throws SecurityServiceUnavailableException when the security service does not answer and
     *                                             some of them are not cached
     */
    Map<String, UserProfile> findByIds(Collection<String> userIds);
}
