package com.energymonitor.notification.application.port.out;

import java.util.List;

/**
 * Output port for who the platform administrators are, the recipients of the administrator
 * notifications.
 */
public interface AdministratorQuery {

    /**
     * @return the accounts holding the global {@code ADMIN} role that can currently sign in
     * @throws SecurityServiceUnavailableException when the security service does not answer and
     *                                             the list is not cached
     */
    List<String> activeAdministratorIds();
}
