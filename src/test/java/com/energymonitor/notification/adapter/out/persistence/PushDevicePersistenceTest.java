package com.energymonitor.notification.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.energymonitor.notification.adapter.out.persistence.adapter.PushDevicePersistenceAdapter;
import com.energymonitor.notification.domain.model.PushDevice;
import com.energymonitor.notification.domain.model.PushDevicePlatform;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Round trips of {@code push_device}.
 *
 * <p>No fixtures are needed: the user is an application-level reference with no SQL foreign key,
 * so an installation persists against a bare identifier.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushDevicePersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PushDevicePersistenceAdapter devices;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * One identifier per installation, the way {@code ManageNotificationChannels#registerDevice}
     * does it: a new token gets a new id, and a token already stored keeps the row it has.
     */
    private static PushDevice installation(String id, String token, String userId) {
        return new PushDevice(id, userId, token, PushDevicePlatform.ANDROID);
    }

    @Test
    void insertAndFindByToken() {
        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000001"));
        flushAndClear();

        PushDevice found = devices.findByToken("ExponentPushToken[one]").orElseThrow();

        assertEquals("usr0000001", found.userId());
        assertEquals(PushDevicePlatform.ANDROID, found.platform());
    }

    @Test
    void findByTokenOnAnUnknownTokenIsEmpty() {
        assertTrue(devices.findByToken("ExponentPushToken[nothing]").isEmpty());
    }

    @Test
    void findByUserReturnsOnlyThatUsersInstallations() {
        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000001"));
        devices.save(installation("dev0000002", "ExponentPushToken[two]", "usr0000001"));
        devices.save(installation("dev0000003", "ExponentPushToken[three]", "usr0000002"));
        flushAndClear();

        List<String> tokens = devices.findByUser("usr0000001").stream().map(PushDevice::token).sorted().toList();

        assertEquals(List.of("ExponentPushToken[one]", "ExponentPushToken[two]"), tokens);
    }

    /**
     * The token identifies the installation, so registering again has to replace the row rather
     * than add one: a phone that signs out of an account and into another must stop receiving the
     * first account's alerts, and it would keep doing so if both rows existed.
     */
    @Test
    void registeringTheSameTokenAgainChangesItsOwner() {
        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000001"));
        flushAndClear();

        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000002"));
        flushAndClear();

        assertEquals("usr0000002", devices.findByToken("ExponentPushToken[one]").orElseThrow().userId());
        assertTrue(devices.findByUser("usr0000001").isEmpty());
        assertEquals(1, devices.findByUser("usr0000002").size());
    }

    /**
     * The platform is a property of the installation, so a re-registering app may correct it. The
     * identifier stays the one the row already had: the app calls this on every start, and an id
     * that moved each time would make the row look like a different installation every time.
     */
    @Test
    void registeringTheSameTokenAgainUpdatesItsPlatformAndKeepsItsIdentity() {
        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000001"));
        flushAndClear();

        devices.save(new PushDevice("dev9999999", "usr0000001", "ExponentPushToken[one]", PushDevicePlatform.IOS));
        flushAndClear();

        PushDevice found = devices.findByToken("ExponentPushToken[one]").orElseThrow();
        assertEquals(PushDevicePlatform.IOS, found.platform());
        assertEquals("dev0000001", found.idDevice());
    }

    @Test
    void deleteByTokenDropsOnlyThatInstallation() {
        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000001"));
        devices.save(installation("dev0000002", "ExponentPushToken[two]", "usr0000001"));
        flushAndClear();

        devices.deleteByToken("ExponentPushToken[one]");
        flushAndClear();

        assertEquals(List.of("ExponentPushToken[two]"),
                devices.findByUser("usr0000001").stream().map(PushDevice::token).toList());
    }

    @Test
    void deleteByUserDropsEveryInstallationOfThatUser() {
        devices.save(installation("dev0000001", "ExponentPushToken[one]", "usr0000001"));
        devices.save(installation("dev0000002", "ExponentPushToken[two]", "usr0000001"));
        devices.save(installation("dev0000003", "ExponentPushToken[three]", "usr0000002"));
        flushAndClear();

        devices.deleteByUser("usr0000001");
        flushAndClear();

        assertTrue(devices.findByUser("usr0000001").isEmpty());
        assertEquals(1, devices.findByUser("usr0000002").size());
    }
}