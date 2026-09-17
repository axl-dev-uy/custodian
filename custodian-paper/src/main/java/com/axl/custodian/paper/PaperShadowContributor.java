package com.axl.custodian.paper;

import com.axl.custodian.api.AuthorityHandle;
import com.axl.custodian.api.BridgeHandle;
import com.axl.custodian.api.DuplicateAssessment;
import com.axl.custodian.api.PhysicalPresence;
import com.axl.custodian.api.ProcessEpoch;
import com.axl.custodian.api.ScopeContribution;
import com.axl.custodian.core.BridgeLifecycle;
import com.axl.custodian.core.CustodianStore;
import com.axl.custodian.core.PresenceService;
import com.axl.custodian.core.ScopeContributor;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Paper service; handles are in-memory capabilities backed by persisted bridge epoch state. */
final class PaperShadowContributor implements com.axl.custodian.api.ShadowContributor {
    private final CustodianStore store;
    private final PresenceService presences;
    private final ScopeContributor contributor;
    private final Clock clock;
    private final String expectedServerId;
    private final Map<UUID, Session> handles = new HashMap<>();
    private boolean shutdown;

    PaperShadowContributor(CustodianStore store, PresenceService presences) {
        this(store, presences, Clock.systemUTC(), null);
    }

    PaperShadowContributor(CustodianStore store, PresenceService presences, Clock clock) {
        this(store, presences, clock, null);
    }

    PaperShadowContributor(
            CustodianStore store, PresenceService presences, Clock clock, String expectedServerId) {
        this.store = Objects.requireNonNull(store, "store");
        this.presences = Objects.requireNonNull(presences, "presences");
        this.contributor = new ScopeContributor(presences);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.expectedServerId = expectedServerId;
    }

    @Override
    public synchronized BridgeHandle startBridgeEpoch(AuthorityHandle authority, String serverId) {
        requireRunning();
        Objects.requireNonNull(authority, "authority");
        if (expectedServerId != null && !expectedServerId.equals(serverId)) {
            throw new IllegalArgumentException("Bridge server ID does not match this Custodian server");
        }
        Instant now = clock.instant();
        ProcessEpoch epoch = new ProcessEpoch(UUID.randomUUID(), serverId, now, now);
        store.startBridge(authority.id(), epoch);

        UUID token = UUID.randomUUID();
        handles.put(token, new Session(authority.id(), epoch));
        return new BridgeHandle(token, authority.id());
    }

    @Override
    public synchronized void heartbeat(BridgeHandle handle) {
        Session session = require(handle);
        store.heartbeatBridge(session.epoch().id(), clock.instant());
    }

    @Override
    public synchronized DuplicateAssessment contribute(BridgeHandle handle, ScopeContribution contribution) {
        Session session = require(handle);
        Objects.requireNonNull(contribution, "contribution");
        List<PhysicalPresence> translated = contribution.presences().stream()
                .map(presence -> translate(handle, session.epoch(), presence))
                .toList();
        contributor.contribute(session.epoch(), new ScopeContribution(contribution.scope(), translated));

        if (translated.isEmpty()) {
            return new DuplicateAssessment(DuplicateAssessment.Status.NONE, List.of());
        }
        return presences.assess(translated.get(0).identity());
    }

    synchronized void shutdown() {
        if (shutdown) return;
        shutdown = true;
        for (Session session : handles.values()) store.invalidateBridge(session.epoch().id());
        handles.clear();
    }

    private Session require(BridgeHandle handle) {
        requireRunning();
        Objects.requireNonNull(handle, "handle");
        Session session = handles.get(handle.epochId());
        if (session == null || !session.authorityId().equals(handle.contributorId())) {
            throw new IllegalArgumentException("Invalid bridge handle");
        }
        BridgeLifecycle lifecycle = store.bridge(session.epoch().id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown bridge epoch"));
        boolean correctlyBound = lifecycle.active()
                && session.authorityId().equals(lifecycle.authorityId())
                && store.epochOwner(session.epoch().id()).filter(session.authorityId()::equals).isPresent();
        if (!correctlyBound) throw new IllegalArgumentException("Inactive or foreign bridge epoch");
        return session;
    }

    private static PhysicalPresence translate(
            BridgeHandle handle, ProcessEpoch persistedEpoch, PhysicalPresence presence) {
        if (!presence.epoch().id().equals(handle.epochId())) {
            throw new IllegalArgumentException("Contribution does not use its bridge handle epoch");
        }
        return new PhysicalPresence(presence.identity(), persistedEpoch, presence.instance(),
                presence.location(), presence.observedAt());
    }

    private void requireRunning() {
        if (shutdown) throw new IllegalStateException("Shadow contributor is shut down");
    }

    private record Session(String authorityId, ProcessEpoch epoch) { }
}
