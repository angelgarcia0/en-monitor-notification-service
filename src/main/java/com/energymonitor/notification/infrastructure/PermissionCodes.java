package com.energymonitor.notification.infrastructure;

/**
 * The permission codes the routes of this service require. The security service seeds and grants
 * them ({@code security-025-seed-admin-role}); this service only asks whether a caller holds one.
 *
 * <p>The code is the stable contract (INV-022): the filter chain enforces it and the web client
 * hard-codes it to decide what to render, so a value here must never change once released.
 *
 * <p>These are global permissions. They say nothing about any home: being an owner or a member
 * of a home is decided by the Home Management context, and none of these codes grants access to
 * a home's data.
 */
final class PermissionCodes {

    /** Read the user directory and the role catalogue. */
    static final String USER_READ = "USER.READ";

    private PermissionCodes() {
    }
}
