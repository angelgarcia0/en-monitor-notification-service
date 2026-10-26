package com.energymonitor.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.notification.adapter.out.security.RemotePermissionCheck;
import com.energymonitor.notification.application.port.out.SecurityServiceUnavailableException;
import com.energymonitor.notification.application.port.out.UserProfile;
import com.energymonitor.notification.application.port.out.UserProfileQuery;
import com.energymonitor.notification.application.usecase.AdminNotifications;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationChannel;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.JwtKeyedTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Route protection and shape of the administrator notification API: {@code USER.READ} is
 * required, as the security service answers it, and every call works on the token subject's own
 * notifications.
 */
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AdminNotificationsControllerWebTest extends JwtKeyedTest {

    private static final String CALLER = "adm0000001";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RemotePermissionCheck permissions;

    @MockitoBean
    private AdminNotifications adminNotifications;

    @MockitoBean
    private UserProfileQuery profiles;

    private static String bearer() {
        return bearer(CALLER);
    }

    private void holdsUserRead(boolean holds) {
        given(permissions.holds(anyString(), anyString())).willAnswer(invocation ->
                holds && invocation.getArgument(0).equals(CALLER)
                        && invocation.getArgument(1).equals("USER.READ"));
    }

    @Test
    void listsTheCallersNotificationsWithTheAffectedAccount() throws Exception {
        holdsUserRead(true);
        Notification n = Notification.pending("not0000001", CALLER, null, NotificationChannel.IN_APP,
                NotificationSourceType.ADMINISTRATION, "use0000009", "admin.accountRegistered");
        n.markAsSent(LocalDateTime.parse("2026-10-14T12:00:00"));
        given(adminNotifications.list(CALLER)).willReturn(List.of(n));
        given(profiles.findByIds(anyCollection())).willReturn(Map.of("use0000009",
                new UserProfile("use0000009", "Ada", "Lovelace", "ada@example.com")));

        mvc.perform(get("/api/v1/admin/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("not0000001"))
                .andExpect(jsonPath("$[0].messageKey").value("admin.accountRegistered"))
                .andExpect(jsonPath("$[0].subjectName").value("Ada Lovelace"))
                .andExpect(jsonPath("$[0].subjectEmail").value("ada@example.com"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void refusesACallerWithoutUserRead() throws Exception {
        holdsUserRead(false);

        mvc.perform(get("/api/v1/admin/notifications").header("Authorization", bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/notifications/not0000001/read").header("Authorization", bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/notifications/not0000001").header("Authorization", bearer()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminNotifications);
    }

    @Test
    void listsWithoutNamesWhileTheSecurityServiceDoesNotAnswer() throws Exception {
        holdsUserRead(true);
        Notification n = Notification.pending("not0000001", CALLER, null, NotificationChannel.IN_APP,
                NotificationSourceType.ADMINISTRATION, "use0000009", "admin.accountRegistered");
        n.markAsSent(LocalDateTime.parse("2026-10-14T12:00:00"));
        given(adminNotifications.list(CALLER)).willReturn(List.of(n));
        given(profiles.findByIds(anyCollection()))
                .willThrow(new SecurityServiceUnavailableException("down", null));

        mvc.perform(get("/api/v1/admin/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("not0000001"))
                .andExpect(jsonPath("$[0].subjectName").isEmpty());
    }

    @Test
    void answersServiceUnavailableWhenThePermissionCannotBeChecked() throws Exception {
        given(permissions.holds(anyString(), anyString()))
                .willThrow(new SecurityServiceUnavailableException("down", null));

        mvc.perform(get("/api/v1/admin/notifications").header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/notifications"));

        verifyNoInteractions(adminNotifications);
    }

    @Test
    void refusesWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void marksReadAndDeletesForTheTokenSubject() throws Exception {
        holdsUserRead(true);

        mvc.perform(put("/api/v1/admin/notifications/not0000001/read").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/notifications/not0000001").header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        verify(adminNotifications).markRead(CALLER, "not0000001");
        verify(adminNotifications).delete(CALLER, "not0000001");
    }

    @Test
    void answersNotFoundForAnotherAdministratorsNotification() throws Exception {
        holdsUserRead(true);
        willThrow(new NoSuchElementException("no")).given(adminNotifications)
                .markRead(CALLER, "not0000002");

        mvc.perform(put("/api/v1/admin/notifications/not0000002/read").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }
}
