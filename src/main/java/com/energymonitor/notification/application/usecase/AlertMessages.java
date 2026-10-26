package com.energymonitor.notification.application.usecase;

/**
 * Wording of the alert and recommendation notifications (mail and push), in Spanish like the rest of the mail.
 *
 * <p>The key is the {@code message_key} of the alert. An unknown key still produces a generic
 * message rather than nothing, so a new alert kind is never silently dropped.
 */
public final class AlertMessages {

    private AlertMessages() {
    }

    /**
     * @param title short line: mail subject and push title
     * @param body  one or two sentences, plain text
     */
    public record Message(String title, String body) {
    }

    public static Message render(String messageKey, String homeName, String deviceName) {
        String home = homeName == null ? "tu hogar" : "«" + homeName + "»";
        String device = deviceName == null ? "un dispositivo" : "«" + deviceName + "»";
        return switch (messageKey) {
            case "alert.device.linked" -> new Message("Nuevo dispositivo vinculado",
                    device + " ya está conectado en " + home + " y enviando mediciones.");
            case "alert.connectivity.offline" -> new Message("Dispositivo desconectado",
                    device + " de " + home + " dejó de enviar datos. Revisa que siga enchufado y con Wi-Fi.");
            case "alert.threshold.critical" -> new Message("Consumo crítico",
                    device + " de " + home + " está consumiendo a un nivel crítico.");
            case "alert.limit.daily" -> new Message("Límite diario alcanzado",
                    home + " ya consumió hoy el límite diario que configuraste.");
            case "alert.limit.monthly" -> new Message("Límite mensual alcanzado",
                    home + " ya consumió este mes el límite mensual que configuraste.");
            case "alert.threshold.high" -> new Message("Consumo alto",
                    device + " de " + home + " está consumiendo por encima de lo habitual.");
            case "recommendation.peakHours" -> new Message("Recomendación: horas pico",
                    "En " + home + " buena parte del consumo de la última semana fue entre las 6 y las 10 p. m. "
                            + "Usar la lavadora, la plancha o el horno en otro horario alivia la factura.");
            case "recommendation.standby" -> new Message("Recomendación: consumo en reposo",
                    device + " de " + home + " sigue consumiendo energía de madrugada. "
                            + "Desconéctalo o usa una regleta con interruptor cuando no lo uses.");
            case "recommendation.aboveAverage" -> new Message("Recomendación: consumo en aumento",
                    home + " consumió esta semana bastante más que la anterior. Revisa qué cambió en el uso de tus equipos.");
            case "recommendation.limitProjection" -> new Message("Recomendación: vas a superar tu límite",
                    "Al ritmo actual " + home + " superará el límite de consumo que configuraste. "
                            + "Reduce el uso de los equipos que más consumen.");
            case "recommendation.deviceIncrease" -> new Message("Recomendación: equipo consumiendo más",
                    device + " de " + home + " consume más que en las semanas anteriores. "
                            + "Puede necesitar mantenimiento o estar usándose más de lo normal.");
            default -> messageKey.startsWith("recommendation.")
                    ? new Message("Nueva recomendación", "Hay una nueva recomendación para " + home + ".")
                    : new Message("Nueva alerta", "Hay una nueva alerta en " + home + ".");
        };
    }
}
