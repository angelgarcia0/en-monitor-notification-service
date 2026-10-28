/**
 * Notification service: the Notification bounded context of EnergyMonitor, deployed on its own.
 *
 * <p>Owns the delivery of messages to people: the alerts and recommendations of a home, sent to
 * its members by mail, Web Push and mobile push according to each member's preferences; the
 * verification and recovery codes the security service hands over to be mailed; the in-app
 * notifications of the administrators; and the delivery log, which records that a message was
 * attempted, over which channel and whether it succeeded. The message body is never stored.
 *
 * <p>Internal layout: {@code domain}, {@code application}, {@code adapter},
 * {@code infrastructure}.
 */
package com.energymonitor.notification;
