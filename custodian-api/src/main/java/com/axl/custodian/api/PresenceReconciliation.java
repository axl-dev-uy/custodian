package com.axl.custodian.api;
import java.util.List;
import java.util.Objects;
public record PresenceReconciliation(ProcessEpoch epoch, String ownerScope, ReconciliationMode mode, List<PhysicalPresence> presences) {
    public PresenceReconciliation { Objects.requireNonNull(epoch); Objects.requireNonNull(ownerScope); Objects.requireNonNull(mode); presences=List.copyOf(presences); if(ownerScope.isBlank()) throw new IllegalArgumentException("ownerScope"); }
}
