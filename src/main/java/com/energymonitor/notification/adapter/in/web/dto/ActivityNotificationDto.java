package com.energymonitor.notification.adapter.in.web.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One activity notification. The apps turn {@code messageKey} into text with {@code params}, in
 * the user's language.
 *
 * @param id         the notification
 * @param source     {@code ACCOUNT}, {@code HOME} or {@code DEVICE}
 * @param messageKey what happened, such as {@code activity.home.member-joined.v1}
 * @param homeId     the home it is about, {@code null} for the account
 * @param params     the names the text is built with: {@code homeName}, {@code actorName},
 *                   {@code subjectName}, {@code deviceName}; a missing one is absent
 * @param createdAt  when it was delivered
 * @param read       whether the user has read it
 */
public record ActivityNotificationDto(String id, String source, String messageKey, String homeId,
                                      Map<String, String> params, LocalDateTime createdAt,
                                      boolean read) {
}
