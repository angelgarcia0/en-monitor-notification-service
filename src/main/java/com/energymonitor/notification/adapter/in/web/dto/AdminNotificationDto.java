package com.energymonitor.notification.adapter.in.web.dto;

import java.time.LocalDateTime;

/**
 * One administrator notification. The web turns {@code messageKey} into text with the affected
 * account's name and address.
 *
 * @param id             the notification
 * @param messageKey     what happened, such as {@code admin.statusChanged.BLOCKED}
 * @param subjectUserId  the account it is about
 * @param subjectName    that account's full name, {@code null} when the account is gone
 * @param subjectEmail   that account's address, {@code null} when the account is gone
 * @param createdAt      when it was delivered
 * @param read           whether the administrator has read it
 */
public record AdminNotificationDto(String id, String messageKey, String subjectUserId,
                                   String subjectName, String subjectEmail,
                                   LocalDateTime createdAt, boolean read) {
}
