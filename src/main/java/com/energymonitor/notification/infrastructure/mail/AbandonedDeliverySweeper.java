package com.energymonitor.notification.infrastructure.mail;

import com.energymonitor.notification.application.port.out.NotificationRecordPort;
import com.energymonitor.notification.infrastructure.MailDeliveryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes delivery records that a previous run opened and never finished.
 *
 * <p>When a process dies with mail in flight, the record for that delivery stays {@code PENDING}
 * forever, claiming a send that will never happen. This closes those rows as failed, so the log
 * tells the truth about what went out.
 *
 * <p><strong>It does not resend anything, and the reason is not caution.</strong> Reconstructing the
 * message would mean this service generating a recovery code, which is the security service's job and
 * its secret: the code is hashed there, stored there and bound to an account there. A retry here
 * could only send a code this service invented, which no stored hash would match, or resend a code
 * nobody still holds. Both would be worse than an honest failure, because the first would look
 * delivered and the second would deliver a secret that is no longer redeemable. So the sweep
 * records the failure and stops.
 *
 * <p>The threshold is generous compared with any real SMTP conversation, so a slow send is not
 * mistaken for a dead one; the residue is that a genuinely stuck row stays {@code PENDING} for that
 * long before anyone learns it was.
 */
@Component
public class AbandonedDeliverySweeper {

    private static final Logger LOG = LoggerFactory.getLogger(AbandonedDeliverySweeper.class);

    /** Recorded on the rows it closes, so the log says why without needing the sweep's own output. */
    public static final String ABANDONED_REASON =
            "the application stopped before this delivery finished";

    private final NotificationRecordPort notifications;
    private final MailDeliveryProperties properties;
    private final Clock clock;

    public AbandonedDeliverySweeper(NotificationRecordPort notifications,
                                    MailDeliveryProperties properties, Clock clock) {
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Runs once at startup, before anything is served.
     *
     * <p>At startup rather than only on a schedule, because the rows this finds are by definition
     * from a previous run and nothing this instance does will ever close them.
     *
     * @param event the readiness event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup(ApplicationReadyEvent event) {
        sweep();
    }

    /**
     * Runs on a schedule, to catch a process that is killed while running rather than at startup.
     */
    @Scheduled(fixedDelayString = "${app.mail.sweep-delay:PT5M}")
    public void sweepOnSchedule() {
        sweep();
    }

    /**
     * Closes every pending row older than the configured threshold.
     *
     * @return how many rows were closed
     */
    public int sweep() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                Instant.now(clock).minus(properties.pendingThreshold()), ZoneOffset.UTC);
        int closed = notifications.failAbandoned(cutoff, ABANDONED_REASON);
        if (closed > 0) {
            LOG.warn("Closed {} delivery record(s) left pending by a previous run", closed);
        }
        return closed;
    }
}