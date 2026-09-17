package com.axl.custodian.api.presence;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** An active observation, not permanent ownership or historical evidence. */
public record PhysicalPresence(UUID identity, ProcessEpoch epoch, PhysicalInstance instance, String location, Instant observedAt) {
    public PhysicalPresence {
        Objects.requireNonNull(identity, "identity"); Objects.requireNonNull(epoch, "epoch"); Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(location, "location"); Objects.requireNonNull(observedAt, "observedAt");
        if (location.isBlank()) throw new IllegalArgumentException("location must not be blank");
    }
}
