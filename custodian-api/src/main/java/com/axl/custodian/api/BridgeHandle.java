package com.axl.custodian.api;

import java.util.Objects;
import java.util.UUID;

/** Opaque, epoch-bound capability for a shadow contributor. */
public record BridgeHandle(UUID epochId, String contributorId) {
    public BridgeHandle { Objects.requireNonNull(epochId, "epochId"); Objects.requireNonNull(contributorId, "contributorId"); if (contributorId.isBlank()) throw new IllegalArgumentException("contributorId"); }
}
