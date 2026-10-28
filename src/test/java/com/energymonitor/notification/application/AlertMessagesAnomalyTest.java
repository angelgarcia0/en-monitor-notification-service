package com.energymonitor.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.notification.application.usecase.AlertMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertMessagesAnomalyTest {

    @Test
    @DisplayName("an unusual-consumption alert names the device and the home")
    void wordsTheAnomaly() {
        var message = AlertMessages.render("alert.anomaly.device", "Casa", "Nevera");

        assertEquals("Consumo inusual", message.title());
        assertTrue(message.body().contains("«Nevera»"));
        assertTrue(message.body().contains("«Casa»"));
    }
}
