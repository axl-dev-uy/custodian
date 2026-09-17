package com.axl.custodian.api.identity;
import java.util.Objects;
import java.util.Optional;
public record ValidationResult(ValidationReason reason, IdentitySnapshot identity) {
    public ValidationResult { Objects.requireNonNull(reason, "reason"); }
    public boolean usable() { return reason == ValidationReason.VALID; }
    public Optional<IdentitySnapshot> snapshot() { return Optional.ofNullable(identity); }
}
