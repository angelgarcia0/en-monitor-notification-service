package com.energymonitor.notification.adapter.in.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.energymonitor.notification.application.usecase.ManageNotificationChannels;
import com.energymonitor.notification.domain.model.NotificationPreference;
import com.energymonitor.notification.domain.model.PushDevicePlatform;
import com.energymonitor.notification.infrastructure.JwtKeyedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Route protection and shape of the notification channel API: any signed-in account, and every
 * call works on the token subject's own channels.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationChannelsControllerWebTest extends JwtKeyedTest {

    private static final String CALLER = "use0000001";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ManageNotificationChannels channels;

    @Test
    void refusesWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(channels);
    }

    @Test
    void readsTheCallersPreferences() throws Exception {
        given(channels.preferences(CALLER)).willReturn(new NotificationPreference(CALLER, true, false));
        given(channels.pushAvailable()).willReturn(true);
        given(channels.browsers(CALLER)).willReturn(2);

        mvc.perform(get("/api/v1/notifications/preferences").header("Authorization", bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false))
                .andExpect(jsonPath("$.pushAvailable").value(true))
                .andExpect(jsonPath("$.pushBrowsers").value(2));
    }

    @Test
    void updatesTheCallersPreferences() throws Exception {
        given(channels.update(CALLER, false, true)).willReturn(new NotificationPreference(CALLER, false, true));

        mvc.perform(put("/api/v1/notifications/preferences").header("Authorization", bearer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"pushEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushEnabled").value(true));
    }

    @Test
    void refusesPreferencesWithoutBothSwitches() throws Exception {
        mvc.perform(put("/api/v1/notifications/preferences").header("Authorization", bearer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(channels);
    }

    @Test
    void hasNoPublicKeyWhileWebPushIsNotConfigured() throws Exception {
        given(channels.webPushAvailable()).willReturn(false);

        mvc.perform(get("/api/v1/notifications/push/public-key").header("Authorization", bearer(CALLER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersThePublicKeyOfWebPush() throws Exception {
        given(channels.webPushAvailable()).willReturn(true);
        given(channels.pushPublicKey()).willReturn("BPublicKey");

        mvc.perform(get("/api/v1/notifications/push/public-key").header("Authorization", bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BPublicKey"));
    }

    @Test
    void subscribesABrowserForTheTokenSubject() throws Exception {
        mvc.perform(post("/api/v1/notifications/push/subscriptions").header("Authorization", bearer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example/abc\","
                                + "\"keys\":{\"p256dh\":\"key\",\"auth\":\"secret\"}}"))
                .andExpect(status().isNoContent());

        verify(channels).subscribe(CALLER, "https://push.example/abc", "key", "secret");
    }

    @Test
    void registersAnAppInstallationForTheTokenSubject() throws Exception {
        mvc.perform(post("/api/v1/notifications/push/devices").header("Authorization", bearer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ExponentPushToken[abc]\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isNoContent());

        verify(channels).registerDevice(CALLER, "ExponentPushToken[abc]", PushDevicePlatform.ANDROID);
    }

    @Test
    void unregistersAnAppInstallationForTheTokenSubject() throws Exception {
        mvc.perform(post("/api/v1/notifications/push/devices/remove").header("Authorization", bearer(CALLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ExponentPushToken[abc]\"}"))
                .andExpect(status().isNoContent());

        verify(channels).unregisterDevice(CALLER, "ExponentPushToken[abc]");
    }

    @Test
    void saysWhetherAppPushIsAvailable() throws Exception {
        given(channels.appPushAvailable()).willReturn(true);

        mvc.perform(get("/api/v1/notifications/push/devices/available").header("Authorization", bearer(CALLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
