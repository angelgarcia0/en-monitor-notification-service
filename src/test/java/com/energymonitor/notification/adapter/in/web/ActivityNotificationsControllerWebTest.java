package com.energymonitor.notification.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.notification.application.usecase.ActivityInbox;
import com.energymonitor.notification.domain.model.Notification;
import com.energymonitor.notification.domain.model.NotificationSourceType;
import com.energymonitor.notification.infrastructure.JwtKeyedTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Route protection and shape of the activity notification API: any signed-in account, and every
 * call works on the token subject's own notifications.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityNotificationsControllerWebTest extends JwtKeyedTest {

    private static final String CALLER = "use0000001";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ActivityInbox inbox;

    private static Notification joined() {
        Notification n = Notification.pendingInApp("not0000001", CALLER, "hom0000001",
                NotificationSourceType.HOME, "msg0000001", "activity.home.member-joined.v1",
                Map.of("actorName", "Ana", "homeName", "Casa"));
        n.markAsSent(LocalDateTime.parse("2026-10-27T09:00:00"));
        return n;
    }

    @Test
    void refusesWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/notifications/activity"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(inbox);
    }

    @Test
    void listsTheCallersActivityWithTheNamesToWordIt() throws Exception {
        given(inbox.list(CALLER, null)).willReturn(List.of(joined()));

        mvc.perform(get("/api/v1/notifications/activity").header("Authorization", bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("not0000001"))
                .andExpect(jsonPath("$[0].source").value("HOME"))
                .andExpect(jsonPath("$[0].messageKey").value("activity.home.member-joined.v1"))
                .andExpect(jsonPath("$[0].homeId").value("hom0000001"))
                .andExpect(jsonPath("$[0].params.actorName").value("Ana"))
                .andExpect(jsonPath("$[0].params.homeName").value("Casa"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void filtersByHome() throws Exception {
        given(inbox.list(CALLER, "hom0000001")).willReturn(List.of(joined()));

        mvc.perform(get("/api/v1/notifications/activity").param("homeId", "hom0000001")
                        .header("Authorization", bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void marksReadAndDeletesForTheTokenSubject() throws Exception {
        mvc.perform(put("/api/v1/notifications/activity/not0000001/read")
                        .header("Authorization", bearer(CALLER)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/notifications/activity/not0000001")
                        .header("Authorization", bearer(CALLER)))
                .andExpect(status().isNoContent());

        verify(inbox).markRead(CALLER, "not0000001");
        verify(inbox).delete(CALLER, "not0000001");
    }

    @Test
    void marksEveryUnreadOneOfAHomeRead() throws Exception {
        given(inbox.markAllRead(CALLER, "hom0000001")).willReturn(3);

        mvc.perform(put("/api/v1/notifications/activity/read").param("homeId", "hom0000001")
                        .header("Authorization", bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(3));
    }

    @Test
    void answersNotFoundForAnotherUsersNotification() throws Exception {
        willThrow(new NoSuchElementException("no")).given(inbox).markRead(CALLER, "not0000002");

        mvc.perform(put("/api/v1/notifications/activity/not0000002/read")
                        .header("Authorization", bearer(CALLER)))
                .andExpect(status().isNotFound());
    }
}
