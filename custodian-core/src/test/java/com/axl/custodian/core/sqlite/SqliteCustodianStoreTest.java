package com.axl.custodian.core.sqlite;

import com.axl.custodian.api.IdentityState;
import com.axl.custodian.api.IdentityOrigin;
import com.axl.custodian.api.PhysicalPresence;
import com.axl.custodian.api.PhysicalInstance;
import com.axl.custodian.api.ProcessEpoch;
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
}
