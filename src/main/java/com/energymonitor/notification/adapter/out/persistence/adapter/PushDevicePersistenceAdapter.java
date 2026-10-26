package com.energymonitor.notification.adapter.out.persistence.adapter;

import com.energymonitor.notification.adapter.out.persistence.entity.PushDeviceEntity;
import com.energymonitor.notification.adapter.out.persistence.repository.PushDeviceRepository;
import com.energymonitor.notification.application.port.out.PushDevicePort;
import com.energymonitor.notification.domain.model.PushDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * App installations that registered a relay token.
 *
 * <p>Kept out of {@link NotificationChannelsPersistenceAdapter} on purpose: a browser subscription
 * is an https endpoint plus the two keys its payload is encrypted with, and a relay token is none of
 * those. They answer the same question — where can this user be reached — with different shapes, so
 * they get their own table and their own adapter instead of one that has to bend a type to fit both.
 */
@Component
public class PushDevicePersistenceAdapter implements PushDevicePort {

    private final PushDeviceRepository devices;

    public PushDevicePersistenceAdapter(PushDeviceRepository devices) {
        this.devices = devices;
    }

    @Override
    public List<PushDevice> findByUser(String userId) {
        return devices.findByUserId(userId).stream().map(PushDevicePersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<PushDevice> findByToken(String token) {
        return devices.findByToken(token).map(PushDevicePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(PushDevice device) {
        // The token identifies the installation, so registering again replaces the row and changes
        // its owner rather than leaving the same phone listed under two accounts.
        PushDeviceEntity entity = devices.findByToken(device.token())
                .orElseGet(() -> new PushDeviceEntity(device.idDevice(), device.userId(), device.token(),
                        device.platform()));
        entity.setUserId(device.userId());
        entity.setPlatform(device.platform().name());
        devices.save(entity);
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        devices.deleteByToken(token);
    }

    @Override
    @Transactional
    public void deleteByUser(String userId) {
        devices.deleteByUserId(userId);
    }

    private static PushDevice toDomain(PushDeviceEntity e) {
        return new PushDevice(e.getIdDevice(), e.getUserId(), e.getToken(), e.platformEnum());
    }
}