package com.energymonitor.notification.application.usecase;

import java.util.Map;

/**
 * Keys and wording of the activity notifications: what happened to a user's account, homes and
 * devices.
 *
 * <p>The apps word the in-app notification themselves, from the key and the stored values, in the
 * user's language. This is the wording of the mail and the push, in Spanish like the rest of the
 * mail. An unknown key still produces a generic message rather than nothing.
 *
 * <p>The values are named {@link #HOME_NAME}, {@link #ACTOR_NAME} (who did it),
 * {@link #SUBJECT_NAME} (the member it is about) and {@link #DEVICE_NAME}; any of them may be
 * missing, and the text then names nobody rather than leaving a gap.
 */
public final class ActivityMessages {

    public static final String HOME_NAME = "homeName";
    public static final String ACTOR_NAME = "actorName";
    public static final String SUBJECT_NAME = "subjectName";
    public static final String DEVICE_NAME = "deviceName";

    /** The first sign-in of a verified account. */
    public static final String WELCOME = "activity.account.welcome.v1";
    /** To who created a home. */
    public static final String HOME_CREATED = "activity.home.created.v1";
    /** To who joined a home. */
    public static final String HOME_JOINED = "activity.home.joined.v1";
    /** To the other members, when someone joined. */
    public static final String MEMBER_JOINED = "activity.home.member-joined.v1";
    /** To the owner and the members, when the thresholds changed. */
    public static final String THRESHOLDS_UPDATED = "activity.home.thresholds-updated.v1";
    /** To the member the owner removed. */
    public static final String REMOVED_FROM_HOME = "activity.home.removed-you.v1";
    /** To the other members, when the owner removed one. */
    public static final String MEMBER_REMOVED = "activity.home.member-removed.v1";
    /** To the other members, when one left. */
    public static final String MEMBER_LEFT = "activity.home.member-left.v1";
    /** To the member who became the owner. */
    public static final String OWNERSHIP_RECEIVED = "activity.home.ownership-received.v1";
    /** To the previous owner. */
    public static final String OWNERSHIP_GIVEN = "activity.home.ownership-given.v1";
    /** To the other members, when the owner changed. */
    public static final String OWNER_CHANGED = "activity.home.owner-changed.v1";
    /** To who deleted the home. */
    public static final String HOME_DELETED_BY_YOU = "activity.home.deleted-by-you.v1";
    /** To the other members of a deleted home. */
    public static final String HOME_DELETED = "activity.home.deleted.v1";
    /** To the members, when a device was linked to the home. */
    public static final String DEVICE_LINKED = "activity.device.linked.v1";
    /** To the members, when a device was unlinked from the home. */
    public static final String DEVICE_UNLINKED = "activity.device.unlinked.v1";

    private ActivityMessages() {
    }

    /**
     * @param messageKey one of the keys above
     * @param params     the names the message is worded with
     * @return the subject and push title, and the body
     */
    public static AlertMessages.Message render(String messageKey, Map<String, String> params) {
        String home = quoted(params.get(HOME_NAME), "tu hogar");
        String actor = params.getOrDefault(ACTOR_NAME, "Alguien");
        String subject = params.getOrDefault(SUBJECT_NAME, "Un miembro");
        String device = quoted(params.get(DEVICE_NAME), "un dispositivo");
        return switch (messageKey) {
            case WELCOME -> message("Bienvenido a Energy Monitor",
                    "Tu cuenta está lista. Crea un hogar o únete a uno con su código para empezar a "
                            + "ver el consumo de tus dispositivos.");
            case HOME_CREATED -> message("Creaste un hogar",
                    "Creaste " + home + ". Comparte su código para que otros se unan y vincula tus "
                            + "dispositivos.");
            case HOME_JOINED -> message("Te uniste a un hogar",
                    "Ahora eres miembro de " + home + ".");
            case MEMBER_JOINED -> message("Nuevo miembro",
                    actor + " se unió a " + home + ".");
            case THRESHOLDS_UPDATED -> message("Umbrales actualizados",
                    actor + " cambió los umbrales de consumo de " + home + ".");
            case REMOVED_FROM_HOME -> message("Ya no eres miembro de un hogar",
                    actor + " te quitó de " + home + ".");
            case MEMBER_REMOVED -> message("Un miembro salió del hogar",
                    subject + " ya no es miembro de " + home + ".");
            case MEMBER_LEFT -> message("Un miembro salió del hogar",
                    actor + " salió de " + home + ".");
            case OWNERSHIP_RECEIVED -> message("Ahora eres el propietario",
                    actor + " te transfirió " + home + ". Ahora eres su propietario.");
            case OWNERSHIP_GIVEN -> message("Transferiste un hogar",
                    subject + " es ahora el propietario de " + home + ".");
            case OWNER_CHANGED -> message("Nuevo propietario",
                    subject + " es ahora el propietario de " + home + ".");
            case HOME_DELETED_BY_YOU -> message("Eliminaste un hogar",
                    "Eliminaste " + home + ". Sus dispositivos quedaron desvinculados.");
            case HOME_DELETED -> message("Se eliminó un hogar",
                    actor + " eliminó " + home + ". Ya no eres miembro.");
            case DEVICE_LINKED -> message("Dispositivo vinculado",
                    params.containsKey(ACTOR_NAME)
                            ? actor + " vinculó " + device + " a " + home + "."
                            : capitalized(device) + " se vinculó a " + home + ".");
            case DEVICE_UNLINKED -> message("Dispositivo desvinculado",
                    capitalized(device) + " se desvinculó de " + home + ".");
            default -> message("Novedades en Energy Monitor", "Hay novedades en " + home + ".");
        };
    }

    private static AlertMessages.Message message(String title, String body) {
        return new AlertMessages.Message(title, body);
    }

    private static String quoted(String name, String fallback) {
        return name == null ? fallback : "«" + name + "»";
    }

    private static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
