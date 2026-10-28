package com.energymonitor.notification.application.port.out;

/**
 * What this service may know about an account: who it is and how to reach it.
 *
 * <p>Deliberately narrow. Status, password data, sessions and roles stay in the security service.
 *
 * @param userId   the account identifier
 * @param name     first name of the person behind the account
 * @param lastName last name of the person behind the account
 * @param email    the account's address
 */
public record UserProfile(String userId, String name, String lastName, String email) {
}
