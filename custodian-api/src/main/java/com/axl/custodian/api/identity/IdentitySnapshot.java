package com.axl.custodian.api.identity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Immutable, storage-neutral view of one logical Custodian identity. */
public record IdentitySnapshot(UUID identity, IdentityState state, IdentityOrigin origin, String authorityId, Instant createdAt, Instant updatedAt, String reason) {
    public IdentitySnapshot {
        Objects.requireNonNull(identity, "identity"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(authorityId, "authorityId"); Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
        if (authorityId.isBlank()) throw new IllegalArgumentException("authorityId must not be blank");
    }
}
