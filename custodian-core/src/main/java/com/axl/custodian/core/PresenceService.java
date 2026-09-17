package com.axl.custodian.core;

import com.axl.custodian.api.DuplicateAssessment;
import com.axl.custodian.api.PhysicalInstance;
import com.axl.custodian.api.PhysicalPresence;
import com.axl.custodian.api.ProcessEpoch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Core-only presence reconciliation; adapters must defer unstable cursor/inventory transitions before calling it. */
public final class PresenceService {
    private final CustodianStore store; private final Clock clock; private final Duration freshness;
    public PresenceService(CustodianStore store, Clock clock, Duration freshness) {
        this.store = Objects.requireNonNull(store); this.clock = Objects.requireNonNull(clock); this.freshness = Objects.requireNonNull(freshness);
        if (freshness.isNegative() || freshness.isZero()) throw new IllegalArgumentException("freshness must be positive");
    }
    public void start(ProcessEpoch epoch) { store.startEpoch(epoch); }
    public void bind(com.axl.custodian.api.AuthorityHandle owner, ProcessEpoch epoch) { store.bindEpoch(owner.id(), epoch); }
    public void registerScope(com.axl.custodian.api.AuthorityHandle owner, String scope) { store.registerScope(owner.id(), scope); }
    public void release(com.axl.custodian.api.AuthorityHandle owner) { store.releaseAuthority(owner.id()); }
    public void reconcileAuthorized(com.axl.custodian.api.AuthorityHandle owner, com.axl.custodian.api.PresenceReconciliation request) { new AuthorizedReconciler(store, this).reconcile(owner, request); }
    public void heartbeat(UUID epoch) { store.heartbeat(epoch, clock.instant()); }
    public boolean isRegistered(UUID identity) { return store.findIdentity(identity).isPresent(); }
    public void observe(PhysicalPresence presence) { store.replacePresence(presence); }
    /** Atomically replaces this epoch's observations for one settled physical inventory/entity scope. */
    public void reconcile(ProcessEpoch epoch, String scopeId, List<PhysicalPresence> presences) {
        reconcile(new com.axl.custodian.api.PresenceReconciliation(epoch, scopeId, com.axl.custodian.api.ReconciliationMode.COMPLETE, presences));
    }
    public void reconcile(com.axl.custodian.api.PresenceReconciliation request) {
        if (!store.epochFresh(request.epoch().id(), clock.instant().minus(freshness))) throw new IllegalArgumentException("Inactive or stale process epoch");
        var seen = new java.util.HashSet<String>();
        for (PhysicalPresence p : request.presences()) {
            if (!p.epoch().id().equals(request.epoch().id()) || !p.instance().id().startsWith(request.ownerScope() + ":")) throw new IllegalArgumentException("Presence is outside owner scope");
            if (!seen.add(p.identity() + "\u0000" + p.instance().id())) throw new IllegalArgumentException("Duplicate identity/instance presence");
        }
        if (request.mode() == com.axl.custodian.api.ReconciliationMode.PARTIAL) { for (PhysicalPresence p : request.presences()) store.replacePresence(p); return; }
        store.reconcileScope(request.epoch(), request.ownerScope(), request.presences());
    }
    /** Replaces a known previous physical instance during a settled movement, preventing a false duplicate. */
    public void move(PhysicalPresence presence, PhysicalInstance previous) {
        store.movePresence(presence, previous);
    }
    public DuplicateAssessment assess(UUID identity) {
        List<PhysicalPresence> active = store.activePresences(identity, clock.instant().minus(freshness));
        long distinct = active.stream().map(p -> p.epoch().serverId() + ":" + p.instance().id()).distinct().count();
        return new DuplicateAssessment(distinct == 0 ? DuplicateAssessment.Status.NONE : distinct == 1
                ? DuplicateAssessment.Status.ONE_ACTIVE : DuplicateAssessment.Status.CONFIRMED_DISTINCT_ACTIVE, active);
    }
}
