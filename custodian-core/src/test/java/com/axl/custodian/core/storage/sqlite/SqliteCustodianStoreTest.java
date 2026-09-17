package com.axl.custodian.core.storage.sqlite;

import com.axl.custodian.api.identity.IdentityState;
import com.axl.custodian.api.identity.IdentityOrigin;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.PhysicalInstance;
import com.axl.custodian.api.presence.ProcessEpoch;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SqliteCustodianStoreTest {
    @TempDir Path directory;
    @Test void adoptionPreservesExistingLogicalIdentityAndAuthority() {
        UUID id = UUID.randomUUID(); Instant now = Instant.parse("2026-01-01T00:00:00Z");
        try (var store = new SqliteCustodianStore(directory.resolve("custodian.db"))) {
            var adopted = store.adopt(id, "infinitygear", now);
            var repeated = store.adopt(id, "other", now.plusSeconds(1));
            assertEquals(IdentityState.ACTIVE, adopted.state());
            assertEquals(IdentityOrigin.ADOPTED, adopted.origin());
            assertEquals("infinitygear", repeated.authorityId());
            assertEquals(now, repeated.createdAt());
        }
    }
    @Test void stalePresenceIsNotReturnedAsActive() {
        UUID id = UUID.randomUUID(); UUID epochId = UUID.randomUUID(); Instant now = Instant.parse("2026-01-01T00:00:30Z");
        try (var store = new SqliteCustodianStore(directory.resolve("presence.db"))) {
            store.adopt(id, "infinitygear", now);
            ProcessEpoch epoch = new ProcessEpoch(epochId, "alpha", now.minusSeconds(30), now);
            store.startEpoch(epoch);
            store.replacePresence(new PhysicalPresence(id, epoch, new PhysicalInstance("player:" + UUID.randomUUID()), "player inventory", now.minusSeconds(20)));
            store.replacePresence(new PhysicalPresence(id, epoch, new PhysicalInstance("ender:" + UUID.randomUUID()), "ender chest", now));
            assertEquals(1, store.activePresences(id, now.minusSeconds(10)).size());
            store.removeEpoch(epochId);
            assertTrue(store.activePresences(id, now.minusSeconds(60)).isEmpty());
        }
    }
    @Test void failedMoveRollsBackOldPresenceInsteadOfLosingIt() {
        UUID known = UUID.randomUUID(); Instant now = Instant.parse("2026-01-01T00:00:30Z"); ProcessEpoch epoch = new ProcessEpoch(UUID.randomUUID(), "alpha", now, now);
        PhysicalInstance old = new PhysicalInstance("player:alice:inventory:0");
        try (var store = new SqliteCustodianStore(directory.resolve("atomic-move.db"))) {
            store.adopt(known, "infinitygear", now); store.startEpoch(epoch);
            store.replacePresence(new PhysicalPresence(known, epoch, old, "alice slot", now));
            assertThrows(StorageException.class, () -> store.movePresence(new PhysicalPresence(UUID.randomUUID(), epoch,
                    new PhysicalInstance("block:world:1,64,1:0"), "chest slot", now), old));
            assertEquals(List.of(old), store.activePresences(known, now.minusSeconds(1)).stream().map(PhysicalPresence::instance).toList());
        }
    }
    @Test void newerIdentitySnapshotSupersedesOldInstancesWhileEqualTimeContributionsAccumulate() {
        UUID identity = UUID.randomUUID(); Instant first = Instant.parse("2026-01-01T00:00:30Z");
        Instant second = first.plusSeconds(1); ProcessEpoch epoch = new ProcessEpoch(UUID.randomUUID(), "alpha", first, second);
        try (var store = new SqliteCustodianStore(directory.resolve("identity-snapshot.db"))) {
            store.adopt(identity, "infinitygear", first); store.startEpoch(epoch);
            PhysicalPresence inventory = presence(identity, epoch, "player:alice:inventory:slot:0", first);
            PhysicalPresence drop = presence(identity, epoch, "drop:item:one", second);
            PhysicalPresence chest = presence(identity, epoch, "block:chest:slot:0", second);

            store.mergeIdentitySnapshot(epoch, identity, first, List.of(inventory));
            store.mergeIdentitySnapshot(epoch, identity, second, List.of(drop));
            store.mergeIdentitySnapshot(epoch, identity, second, List.of(chest));
            store.mergeIdentitySnapshot(epoch, identity, second, List.of(drop));

            assertEquals(List.of("block:chest:slot:0", "drop:item:one"), store.activePresences(identity, first)
                    .stream().map(p -> p.instance().id()).sorted().toList());
            assertThrows(IllegalArgumentException.class,
                    () -> store.mergeIdentitySnapshot(epoch, identity, first, List.of(inventory)));
            assertEquals(2, store.activePresences(identity, first).size());
        }
    }
    private static PhysicalPresence presence(
            UUID identity, ProcessEpoch epoch, String instance, Instant observedAt) {
        return new PhysicalPresence(identity, epoch, new PhysicalInstance(instance), instance, observedAt);
    }
}
