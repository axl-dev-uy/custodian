package com.axl.custodian.core.storage.sqlite;

import com.axl.custodian.api.presence.PhysicalInstance;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.ProcessEpoch;
import com.axl.custodian.core.presence.BridgeLifecycle;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeLifecycleStoreTest {
    private static final String AUTHORITY = "infinitygear";
    private static final String SERVER = "paper-alpha";
    private static final Instant STARTED_AT = Instant.parse("2026-09-17T12:00:00Z");

    @TempDir
    Path directory;

    @Test
    void migrationSixUpgradesVersionFiveAndPreservesExistingState() throws Exception {
        Path database = directory.resolve("version-five.db");
        UUID identity = UUID.randomUUID();
        createVersionFiveDatabase(database, identity);
        ProcessEpoch bridgeEpoch = epoch(STARTED_AT.plusSeconds(1));

        try (var store = new SqliteCustodianStore(database)) {
            assertTrue(store.findIdentity(identity).isPresent());
            assertEquals("existing", store.scopeOwner("virtual:existing").orElseThrow());
            store.startBridge(AUTHORITY, bridgeEpoch);
            assertEquals(AUTHORITY, store.epochOwner(bridgeEpoch.id()).orElseThrow());
            assertTrue(store.bridge(bridgeEpoch.id()).orElseThrow().active());
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var query = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version=6")) {
            assertTrue(query.executeQuery().next());
        }
    }

    @Test
    void bridgeAuthorityEpochAndHeartbeatSurviveReopen() {
        Path database = directory.resolve("bridge.db");
        ProcessEpoch epoch = epoch(STARTED_AT);
        Instant heartbeat = STARTED_AT.plusSeconds(5);

        try (var store = new SqliteCustodianStore(database)) {
            store.startBridge(AUTHORITY, epoch);
            assertEquals(AUTHORITY, store.epochOwner(epoch.id()).orElseThrow());
            assertEquals(new BridgeLifecycle(AUTHORITY, epoch.id(), SERVER, STARTED_AT, STARTED_AT, true),
                    store.bridge(epoch.id()).orElseThrow());

            store.heartbeatBridge(epoch.id(), heartbeat);
            assertTrue(store.epochFresh(epoch.id(), heartbeat));
        }

        try (var reopened = new SqliteCustodianStore(database)) {
            assertEquals(new BridgeLifecycle(AUTHORITY, epoch.id(), SERVER, STARTED_AT, heartbeat, true),
                    reopened.bridge(epoch.id()).orElseThrow());
            assertEquals(AUTHORITY, reopened.epochOwner(epoch.id()).orElseThrow());
            assertTrue(reopened.epochFresh(epoch.id(), heartbeat));
        }
    }

    @Test
    void newerEpochEndsTheOldEpochAndEndedEpochRejectsHeartbeats() {
        ProcessEpoch oldEpoch = epoch(STARTED_AT);
        ProcessEpoch newEpoch = epoch(STARTED_AT.plusSeconds(10));
        UUID identity = UUID.randomUUID();

        try (var store = new SqliteCustodianStore(directory.resolve("superseded.db"))) {
            store.adopt(identity, AUTHORITY, STARTED_AT);
            store.startBridge(AUTHORITY, oldEpoch);
            store.replacePresence(new PhysicalPresence(identity, oldEpoch,
                    new PhysicalInstance("entity:minecart:slot:0"), "minecart", STARTED_AT));
            store.startBridge(AUTHORITY, newEpoch);

            assertFalse(store.bridge(oldEpoch.id()).orElseThrow().active());
            assertTrue(store.bridge(newEpoch.id()).orElseThrow().active());
            assertTrue(store.activePresences(identity, STARTED_AT).isEmpty(),
                    "superseding a bridge must retire its fresh presences immediately");
            assertThrows(IllegalArgumentException.class,
                    () -> store.heartbeatBridge(oldEpoch.id(), STARTED_AT.plusSeconds(20)));
            ProcessEpoch staleEpoch = epoch(STARTED_AT.plusSeconds(5));
            assertThrows(IllegalArgumentException.class, () -> store.startBridge(AUTHORITY, staleEpoch));
            assertTrue(store.bridge(staleEpoch.id()).isEmpty());
            assertTrue(store.epochOwner(staleEpoch.id()).isEmpty());

            store.replacePresence(new PhysicalPresence(identity, newEpoch,
                    new PhysicalInstance("player:alice:inventory:slot:0"), "inventory", STARTED_AT.plusSeconds(10)));
            store.invalidateBridge(newEpoch.id());
            store.invalidateBridge(newEpoch.id());
            assertFalse(store.bridge(newEpoch.id()).orElseThrow().active());
            assertTrue(store.activePresences(identity, STARTED_AT).isEmpty(),
                    "invalidation must retire bridge presences idempotently");
            assertThrows(IllegalArgumentException.class,
                    () -> store.heartbeatBridge(newEpoch.id(), STARTED_AT.plusSeconds(20)));
            assertThrows(IllegalArgumentException.class,
                    () -> store.heartbeatBridge(UUID.randomUUID(), STARTED_AT.plusSeconds(20)));
        }
    }

    @Test
    void heartbeatRejectsTimeRegressionButAcceptsExactRetry() {
        ProcessEpoch epoch = epoch(STARTED_AT);
        Instant heartbeat = STARTED_AT.plusSeconds(5);

        try (var store = new SqliteCustodianStore(directory.resolve("heartbeat.db"))) {
            store.startBridge(AUTHORITY, epoch);
            store.heartbeatBridge(epoch.id(), heartbeat);
            store.heartbeatBridge(epoch.id(), heartbeat);

            assertThrows(IllegalArgumentException.class,
                    () -> store.heartbeatBridge(epoch.id(), heartbeat.minusNanos(1)));
            assertEquals(heartbeat, store.bridge(epoch.id()).orElseThrow().heartbeatAt());
            assertTrue(store.epochFresh(epoch.id(), heartbeat));
        }
    }

    @Test
    void epochCannotBeReboundByConflictingStartRetry() {
        ProcessEpoch epoch = epoch(STARTED_AT);

        try (var store = new SqliteCustodianStore(directory.resolve("binding.db"))) {
            store.startBridge(AUTHORITY, epoch);
            store.startBridge(AUTHORITY, epoch);

            assertThrows(IllegalArgumentException.class, () -> store.startBridge("other", epoch));
            assertThrows(IllegalArgumentException.class, () -> store.startBridge(AUTHORITY,
                    new ProcessEpoch(epoch.id(), "other-server", STARTED_AT, STARTED_AT)));
            assertEquals(AUTHORITY, store.epochOwner(epoch.id()).orElseThrow());
            assertEquals(AUTHORITY, store.bridge(epoch.id()).orElseThrow().authorityId());
        }
    }

    @Test
    void invalidationAndAuthorityReleaseAreIdempotentAndPreserveDurableState() {
        UUID bridgeIdentity = UUID.randomUUID();
        UUID unrelatedIdentity = UUID.randomUUID();
        ProcessEpoch bridgeEpoch = epoch(STARTED_AT);
        ProcessEpoch explicitlyInvalidatedEpoch = new ProcessEpoch(
                UUID.randomUUID(), "paper-beta", STARTED_AT, STARTED_AT);
        ProcessEpoch unrelatedEpoch = new ProcessEpoch(
                UUID.randomUUID(), "other-server", STARTED_AT, STARTED_AT);
        Path database = directory.resolve("release.db");

        try (var store = new SqliteCustodianStore(database)) {
            store.adopt(bridgeIdentity, AUTHORITY, STARTED_AT);
            store.adopt(unrelatedIdentity, "other", STARTED_AT);
            store.registerScope(AUTHORITY, "virtual:infinitygear");
            store.registerScope("other", "virtual:other");
            store.startBridge(AUTHORITY, bridgeEpoch);
            store.startBridge(AUTHORITY, explicitlyInvalidatedEpoch);
            store.startBridge("other", unrelatedEpoch);
            store.replacePresence(new PhysicalPresence(bridgeIdentity, bridgeEpoch,
                    new PhysicalInstance("virtual:infinitygear:slot:0"), "bridge slot", STARTED_AT));
            store.replacePresence(new PhysicalPresence(unrelatedIdentity, unrelatedEpoch,
                    new PhysicalInstance("virtual:other:slot:0"), "other slot", STARTED_AT));

            store.invalidateBridge(explicitlyInvalidatedEpoch.id());
            store.invalidateBridge(explicitlyInvalidatedEpoch.id());
            store.releaseAuthority(AUTHORITY);
            store.releaseAuthority(AUTHORITY);

            assertFalse(store.bridge(bridgeEpoch.id()).orElseThrow().active());
            assertFalse(store.bridge(explicitlyInvalidatedEpoch.id()).orElseThrow().active());
            assertTrue(store.bridge(unrelatedEpoch.id()).orElseThrow().active());
            assertTrue(store.scopeOwner("virtual:infinitygear").isEmpty());
            assertEquals("other", store.scopeOwner("virtual:other").orElseThrow());
            assertTrue(store.findIdentity(bridgeIdentity).isPresent());
            assertTrue(store.findIdentity(unrelatedIdentity).isPresent());
            assertTrue(store.activePresences(bridgeIdentity, STARTED_AT).isEmpty());
            assertEquals(1, store.activePresences(unrelatedIdentity, STARTED_AT).size());
        }

        try (var reopened = new SqliteCustodianStore(database)) {
            assertFalse(reopened.bridge(bridgeEpoch.id()).orElseThrow().active());
            assertTrue(reopened.bridge(unrelatedEpoch.id()).orElseThrow().active());
            assertTrue(reopened.findIdentity(bridgeIdentity).isPresent());
            assertEquals("other", reopened.scopeOwner("virtual:other").orElseThrow());
        }
    }

    private static ProcessEpoch epoch(Instant startedAt) {
        return new ProcessEpoch(UUID.randomUUID(), SERVER, startedAt, startedAt);
    }

    private static void createVersionFiveDatabase(Path database, UUID identity) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE identities (identity TEXT PRIMARY KEY, state TEXT NOT NULL, authority_id TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, reason TEXT, origin TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE process_epochs (epoch_id TEXT PRIMARY KEY, server_id TEXT NOT NULL, started_at TEXT NOT NULL, heartbeat_at TEXT NOT NULL, authority_id TEXT)");
            statement.executeUpdate("CREATE TABLE current_presence (identity TEXT NOT NULL REFERENCES identities(identity), server_id TEXT NOT NULL, process_epoch TEXT NOT NULL REFERENCES process_epochs(epoch_id), physical_instance TEXT NOT NULL, location TEXT NOT NULL, observed_at TEXT NOT NULL, PRIMARY KEY(identity, process_epoch, physical_instance))");
            statement.executeUpdate("CREATE INDEX current_presence_freshness ON current_presence(identity, observed_at)");
            statement.executeUpdate("CREATE TABLE scope_owners (scope_id TEXT PRIMARY KEY, authority_id TEXT NOT NULL)");
            statement.executeUpdate("INSERT INTO schema_migrations(version,applied_at) VALUES (1,'2026-01-01'),(2,'2026-01-01'),(3,'2026-01-01'),(4,'2026-01-01'),(5,'2026-01-01')");
            statement.executeUpdate("INSERT INTO identities(identity,state,authority_id,created_at,updated_at,reason,origin) VALUES ('"
                    + identity + "','ACTIVE','existing','" + STARTED_AT + "','" + STARTED_AT
                    + "',NULL,'ADOPTED')");
            statement.executeUpdate("INSERT INTO scope_owners(scope_id,authority_id) VALUES ('virtual:existing','existing')");
        }
    }
}
