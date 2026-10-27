package com.energymonitor.notification.domain.model;

/**
 * Which context a notification belongs to.
 *
 * <p>This is not the same question as what the message says.
 * An alert and the message about an alert are related, but they are not the same fact, and the
 * documented model keeps them apart: {@code sourceType} plus {@code sourceId} locate the row this
 * message is about, while {@code messageKey} names the wording that was used.
 *
 * <p>Those two facts can disagree, and are meant to. A message about an alert can be worded as
 * "your alert" or as "we noticed something"; the source says what it refers to and the key says how
 * it was phrased. Collapsing them would force a wording change to be a data migration.
 *
 * <p>{@link #SECURITY} exists because recovery codes are sent by this module on behalf of another
 * one: the source row lives in the security service and is referenced by identifier, with no foreign
 * key, which is what keeps the two independent.
 */
public enum NotificationSourceType {

    /** A message about an alert raised for a home. */
    ALERT,

    /** A message about a recommendation issued for a home. */
    RECOMMENDATION,

    /** A message sent by the security service, such as a password recovery code. */
    SECURITY,

    /** A change on the platform an administrator is told about. */
    ADMINISTRATION,

    /** Something about the account itself, such as the welcome after its first sign-in. */
    ACCOUNT,

    /**
     * Something that happened to a home: created, joined, left, a member removed, the owner
     * changed, the thresholds changed, the home deleted.
     */
    HOME,

    /** A device linked to or unlinked from a home. */
    DEVICE
}