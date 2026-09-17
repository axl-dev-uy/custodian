package com.axl.custodian.paper.shadow;

import com.axl.custodian.api.identity.AuthorityHandle;
import com.axl.custodian.api.shadow.BridgeHandle;
import com.axl.custodian.api.presence.DuplicateAssessment;
import com.axl.custodian.api.presence.PhysicalInstance;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.ProcessEpoch;
import com.axl.custodian.api.presence.ScannerScope;
import com.axl.custodian.api.shadow.ScopeContribution;
import com.axl.custodian.core.presence.PresenceService;
import com.axl.custodian.core.storage.sqlite.SqliteCustodianStore;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperShadowContributorTest {
    private static final String AUTHORITY = "infinitygear";
    private static final String SERVER = "paper-alpha";
    private static final String SCOPE = "native:player:alice";
    private static final Instant NOW = Instant.parse("2026-09-17T15:00:00Z");

    @TempDir
    Path directory;

    @Test
    void serverIdMismatchIsRejectedBeforeCreatingAHandle() {
        Path database = directory.resolve("server-mismatch.db");
        try (var store = new SqliteCustodianStore(database)) {
            MutableClock clock = new MutableClock(NOW);
            PresenceService presences = new PresenceService(store, clock, Duration.ofSeconds(30));
            PaperShadowContributor shadow = new PaperShadowContributor(
                    store, presences, clock, "paper-alpha");

            assertThrows(IllegalArgumentException.class, () -> shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), "paper-beta"));
            assertEquals(0, activeBridgeCount(database, AUTHORITY));
        }
    }

    @Test
    void partialContributionUsesOpaqueEpochAndPreservesNativePresence() {
        try (Fixture fixture = fixture("partial.db")) {
            UUID identity = UUID.randomUUID();
            fixture.store.adopt(identity, AUTHORITY, NOW);
            ProcessEpoch nativeEpoch = new ProcessEpoch(UUID.randomUUID(), SERVER, NOW, NOW);
            fixture.store.startEpoch(nativeEpoch);
            fixture.store.replacePresence(new PhysicalPresence(identity, nativeEpoch,
                    new PhysicalInstance(SCOPE + ":slot:0"), "native slot 0", NOW));

            BridgeHandle handle = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            assertTrue(fixture.store.bridge(handle.epochId()).isEmpty(), "public handle UUID must be opaque");
            DuplicateAssessment assessment = fixture.shadow.contribute(handle,
                    contribution(handle, identity, SCOPE + ":slot:1"));

            assertEquals(DuplicateAssessment.Status.CONFIRMED_DISTINCT_ACTIVE, assessment.status());
            assertEquals(2, fixture.store.activePresences(identity, NOW.minusSeconds(1)).size());
        }
    }

    @Test
    void newerSettledSnapshotRetiresOnlyOldBridgePresenceAndPreservesNativeObservation() {
        try (Fixture fixture = fixture("settled-movement.db")) {
            UUID identity = UUID.randomUUID();
            fixture.store.adopt(identity, AUTHORITY, NOW);
            BridgeHandle handle = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            fixture.shadow.contribute(handle, contribution(
                    handle, identity, "player:alice:inventory", "player:alice:inventory:slot:0", NOW));

            Instant movedAt = NOW.plusSeconds(1);
            fixture.clock.advance(Duration.ofSeconds(1));
            ProcessEpoch nativeEpoch = new ProcessEpoch(UUID.randomUUID(), SERVER, NOW, movedAt);
            fixture.store.startEpoch(nativeEpoch);
            fixture.store.replacePresence(new PhysicalPresence(identity, nativeEpoch,
                    new PhysicalInstance("drop:item-one:item"), "drop", movedAt));
            DuplicateAssessment assessment = fixture.shadow.contribute(handle, contribution(
                    handle, identity, "drop:item-one", "drop:item-one:item", movedAt));

            assertEquals(DuplicateAssessment.Status.ONE_ACTIVE, assessment.status());
            List<PhysicalPresence> active = fixture.store.activePresences(identity, NOW.minusSeconds(1));
            assertEquals(2, active.size(), "native and bridge rows for one physical instance are retained");
            assertTrue(active.stream().allMatch(p -> p.instance().id().equals("drop:item-one:item")));
        }
    }

    @Test
    void equalTimeScopesStillConfirmRealDuplicateAndRetriesAreIdempotent() {
        try (Fixture fixture = fixture("same-snapshot-duplicate.db")) {
            UUID identity = UUID.randomUUID();
            fixture.store.adopt(identity, AUTHORITY, NOW);
            BridgeHandle handle = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            ScopeContribution inventory = contribution(
                    handle, identity, "player:alice:inventory", "player:alice:inventory:slot:0", NOW);
            ScopeContribution ender = contribution(
                    handle, identity, "player:alice:ender", "player:alice:ender:slot:0", NOW);

            fixture.shadow.contribute(handle, inventory);
            DuplicateAssessment duplicate = fixture.shadow.contribute(handle, ender);
            DuplicateAssessment retry = fixture.shadow.contribute(handle, ender);

            assertEquals(DuplicateAssessment.Status.CONFIRMED_DISTINCT_ACTIVE, duplicate.status());
            assertEquals(DuplicateAssessment.Status.CONFIRMED_DISTINCT_ACTIVE, retry.status());
            assertEquals(2, fixture.store.activePresences(identity, NOW.minusSeconds(1)).size());
        }
    }

    @Test
    void forgedForeignAndWrongEpochHandlesFailBeforeWriting() {
        try (Fixture fixture = fixture("forged.db")) {
            UUID identity = UUID.randomUUID();
            fixture.store.adopt(identity, AUTHORITY, NOW);
            BridgeHandle valid = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            BridgeHandle forged = new BridgeHandle(UUID.randomUUID(), AUTHORITY);
            BridgeHandle foreign = new BridgeHandle(valid.epochId(), "other");

            assertThrows(IllegalArgumentException.class,
                    () -> fixture.shadow.contribute(forged, contribution(forged, identity, SCOPE + ":slot:0")));
            assertThrows(IllegalArgumentException.class, () -> fixture.shadow.heartbeat(foreign));
            ScopeContribution wrongEpoch = contribution(
                    new BridgeHandle(UUID.randomUUID(), AUTHORITY), identity, SCOPE + ":slot:0");
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.shadow.contribute(valid, wrongEpoch));
            assertTrue(fixture.store.activePresences(identity, NOW.minusSeconds(1)).isEmpty());
        }
    }

    @Test
    void supersededHandleIsStaleWhileReplacementRemainsUsable() {
        try (Fixture fixture = fixture("stale.db")) {
            BridgeHandle stale = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            fixture.clock.advance(Duration.ofSeconds(1));
            BridgeHandle replacement = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);

            assertThrows(IllegalArgumentException.class, () -> fixture.shadow.heartbeat(stale));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.shadow.contribute(stale,
                            new ScopeContribution(new ScannerScope(SCOPE), List.of())));
            fixture.shadow.heartbeat(replacement);
            assertEquals(1, activeBridgeCount(fixture.database, AUTHORITY));
        }
    }

    @Test
    void restartedServiceSupersedesFreshPersistedPresenceBeforeMovementAssessment() {
        try (Fixture fixture = fixture("restart-movement.db")) {
            UUID identity = UUID.randomUUID();
            fixture.store.adopt(identity, AUTHORITY, NOW);
            BridgeHandle oldHandle = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            fixture.shadow.contribute(oldHandle, contribution(
                    oldHandle, identity, "entity:minecart", "entity:minecart:slot:0", NOW));

            fixture.clock.advance(Duration.ofSeconds(1));
            PresenceService restartedPresences = new PresenceService(
                    fixture.store, fixture.clock, Duration.ofSeconds(30));
            PaperShadowContributor restarted = new PaperShadowContributor(
                    fixture.store, restartedPresences, fixture.clock);
            BridgeHandle newHandle = restarted.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            DuplicateAssessment moved = restarted.contribute(newHandle, contribution(
                    newHandle, identity, "player:alice:inventory",
                    "player:alice:inventory:slot:0", NOW.plusSeconds(1)));

            assertEquals(DuplicateAssessment.Status.ONE_ACTIVE, moved.status());
            assertEquals(List.of("player:alice:inventory:slot:0"),
                    fixture.store.activePresences(identity, NOW.minusSeconds(1)).stream()
                            .map(p -> p.instance().id()).toList());
            assertThrows(IllegalArgumentException.class, () -> fixture.shadow.heartbeat(oldHandle));
            restarted.shutdown();
        }
    }

    @Test
    void releasedBridgeRejectsHeartbeatAndContribution() {
        try (Fixture fixture = fixture("ended.db")) {
            BridgeHandle handle = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            fixture.store.releaseAuthority(AUTHORITY);

            assertThrows(IllegalArgumentException.class, () -> fixture.shadow.heartbeat(handle));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.shadow.contribute(handle,
                            new ScopeContribution(new ScannerScope(SCOPE), List.of())));
            assertEquals(0, activeBridgeCount(fixture.database, AUTHORITY));
        }
    }

    @Test
    void shutdownInvalidatesPersistedEpochsAndAllInMemoryHandles() {
        try (Fixture fixture = fixture("shutdown.db")) {
            UUID identity = UUID.randomUUID();
            fixture.store.adopt(identity, AUTHORITY, NOW);
            BridgeHandle handle = fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER);
            fixture.shadow.contribute(handle, contribution(handle, identity, SCOPE + ":slot:0"));

            fixture.shadow.shutdown();
            fixture.shadow.shutdown();

            assertThrows(IllegalStateException.class, () -> fixture.shadow.heartbeat(handle));
            assertThrows(IllegalStateException.class, () -> fixture.shadow.startBridgeEpoch(
                    AuthorityHandle.issuedByHost(AUTHORITY), SERVER));
            assertEquals(0, activeBridgeCount(fixture.database, AUTHORITY));
            assertTrue(fixture.store.activePresences(identity, NOW.minusSeconds(1)).isEmpty());
        }
    }

    private Fixture fixture(String databaseName) {
        Path database = directory.resolve(databaseName);
        SqliteCustodianStore store = new SqliteCustodianStore(database);
        MutableClock clock = new MutableClock(NOW);
        PresenceService presences = new PresenceService(store, clock, Duration.ofSeconds(30));
        return new Fixture(database, store, clock, new PaperShadowContributor(store, presences, clock));
    }

    private static ScopeContribution contribution(
            BridgeHandle handle, UUID identity, String physicalInstance) {
        return contribution(handle, identity, SCOPE, physicalInstance, NOW);
    }

    private static ScopeContribution contribution(
            BridgeHandle handle, UUID identity, String scope, String physicalInstance, Instant observedAt) {
        ProcessEpoch publicEpoch = new ProcessEpoch(handle.epochId(), "opaque", observedAt, observedAt);
        return new ScopeContribution(new ScannerScope(scope), List.of(new PhysicalPresence(
                identity, publicEpoch, new PhysicalInstance(physicalInstance), "shadow slot", observedAt)));
    }

    private static int activeBridgeCount(Path database, String authority) {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM bridge_epochs WHERE authority_id=? AND active=1")) {
            query.setString(1, authority);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private record Fixture(
            Path database, SqliteCustodianStore store, MutableClock clock, PaperShadowContributor shadow)
            implements AutoCloseable {
        @Override public void close() { store.close(); }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("Only UTC is supported");
            return this;
        }
        @Override public Instant instant() { return instant; }
    }
}
