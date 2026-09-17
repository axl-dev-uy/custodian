package com.axl.custodian.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A single server-process lifetime. Its heartbeat determines whether observations remain live. */
public record ProcessEpoch(UUID id, String serverId, Instant startedAt, Instant heartbeatAt) {
    public ProcessEpoch {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(startedAt, "startedAt"); Objects.requireNonNull(heartbeatAt, "heartbeatAt");
        if (serverId.isBlank()) throw new IllegalArgumentException("serverId must not be blank");
    }
}
