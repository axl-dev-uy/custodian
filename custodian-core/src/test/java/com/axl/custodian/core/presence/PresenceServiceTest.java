package com.axl.custodian.core.presence;

import com.axl.custodian.api.presence.DuplicateAssessment;
import com.axl.custodian.api.presence.PhysicalInstance;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.ProcessEpoch;
import com.axl.custodian.core.storage.sqlite.SqliteCustodianStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {
    @TempDir Path directory;
    @Test void movementReplacesOldInstanceInsteadOfConfirmingDuplicate() {
        Instant now = Instant.parse("2026-01-01T00:00:30Z"); UUID identity = UUID.randomUUID();
        try (var store = new SqliteCustodianStore(directory.resolve("move.db"))) {
            store.adopt(identity, "infinitygear", now); var service = new PresenceService(store, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(10));
            ProcessEpoch epoch = new ProcessEpoch(UUID.randomUUID(), "alpha", now, now); service.start(epoch);
            PhysicalInstance inventory = new PhysicalInstance("player:alice:inventory:0");
            service.observe(new PhysicalPresence(identity, epoch, inventory, "player/alice/slot/0", now));
            service.move(new PhysicalPresence(identity, epoch, new PhysicalInstance("block:world:1,64,1:0"), "chest/world/1,64,1/0", now), inventory);
            assertEquals(DuplicateAssessment.Status.ONE_ACTIVE, service.assess(identity).status());
        }
    }
    @Test void twoFreshDistinctInstancesConfirmDuplicateButAStaleEpochDoesNot() {
        Instant now = Instant.parse("2026-01-01T00:00:30Z"); UUID identity = UUID.randomUUID();
        try (var store = new SqliteCustodianStore(directory.resolve("duplicate.db"))) {
            store.adopt(identity, "infinitygear", now); var service = new PresenceService(store, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(10));
            ProcessEpoch alpha = new ProcessEpoch(UUID.randomUUID(), "alpha", now, now); ProcessEpoch beta = new ProcessEpoch(UUID.randomUUID(), "beta", now, now);
            service.start(alpha); service.start(beta);
            service.observe(new PhysicalPresence(identity, alpha, new PhysicalInstance("player:alice:0"), "alice slot", now));
            service.observe(new PhysicalPresence(identity, beta, new PhysicalInstance("player:bob:0"), "bob slot", now));
            assertTrue(service.assess(identity).duplicateConfirmed());
            store.heartbeat(beta.id(), now.minusSeconds(11));
            assertEquals(DuplicateAssessment.Status.ONE_ACTIVE, service.assess(identity).status());
        }
    }
    @Test void samePhysicalInstanceAcrossEpochsIsNotTwoInstances() {
        Instant now = Instant.parse("2026-01-01T00:00:30Z"); UUID identity = UUID.randomUUID();
        try (var store = new SqliteCustodianStore(directory.resolve("same-instance.db"))) {
            store.adopt(identity, "infinitygear", now); var service = new PresenceService(store, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(10));
            ProcessEpoch first = new ProcessEpoch(UUID.randomUUID(), "alpha", now, now); ProcessEpoch second = new ProcessEpoch(UUID.randomUUID(), "alpha", now, now);
            service.start(first); service.start(second); PhysicalInstance slot = new PhysicalInstance("player:alice:inventory:0");
            service.observe(new PhysicalPresence(identity, first, slot, "alice slot", now)); service.observe(new PhysicalPresence(identity, second, slot, "alice slot", now));
            assertEquals(DuplicateAssessment.Status.ONE_ACTIVE, service.assess(identity).status());
        }
    }
    @Test void clearingVirtualScopeRemovesOnlyPresenceNotIdentity() {
        Instant now = Instant.parse("2026-01-01T00:00:30Z"); UUID identity = UUID.randomUUID();
        try (var store = new SqliteCustodianStore(directory.resolve("virtual-cleanup.db"))) {
            store.adopt(identity, "infinitygear", now); var service = new PresenceService(store, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(10)); ProcessEpoch epoch = new ProcessEpoch(UUID.randomUUID(), "alpha", now, now); service.start(epoch);
            service.reconcile(epoch, "virtual:owner", java.util.List.of(new PhysicalPresence(identity, epoch, new PhysicalInstance("virtual:owner:slot:0"), "virtual", now)));
            service.reconcile(epoch, "virtual:owner", java.util.List.of());
            assertTrue(store.findIdentity(identity).isPresent()); assertTrue(store.activePresences(identity, now.minusSeconds(1)).isEmpty());
        }
    }
}
