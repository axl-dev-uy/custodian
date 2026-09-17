package com.axl.custodian.core;

import com.axl.custodian.api.IdentitySnapshot;
import com.axl.custodian.api.PhysicalPresence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.axl.custodian.api.ProcessEpoch;
import com.axl.custodian.api.PhysicalInstance;

/** Storage port. Implementations are authoritative independently; they are never fallback peers. */
public interface CustodianStore extends AutoCloseable {
    Optional<IdentitySnapshot> findIdentity(UUID identity);
    IdentitySnapshot adopt(UUID identity, String authorityId, Instant now);
    void startEpoch(ProcessEpoch epoch);
    void heartbeat(UUID epochId, Instant observedAt);
    void replacePresence(PhysicalPresence presence);
    void closePresence(UUID identity, UUID epochId, PhysicalInstance instance);
    void movePresence(PhysicalPresence presence, PhysicalInstance previous);
    void reconcileScope(ProcessEpoch epoch, String scopeId, List<PhysicalPresence> presences);
    boolean epochFresh(UUID epochId, Instant freshAfter);
    void registerScope(String authorityId, String scopeId);
    void releaseAuthority(String authorityId);
    void bindEpoch(String authorityId, ProcessEpoch epoch);
    java.util.Optional<String> scopeOwner(String scopeId);
    java.util.Optional<String> epochOwner(UUID epochId);
    void startBridge(String authorityId, ProcessEpoch epoch);
    void heartbeatBridge(UUID epochId, Instant observedAt);
    void invalidateBridge(UUID epochId);
    java.util.Optional<BridgeLifecycle> bridge(UUID epochId);
    List<PhysicalPresence> activePresences(UUID identity, Instant freshAfter);
    void removeEpoch(UUID epoch);
    @Override void close();
}
