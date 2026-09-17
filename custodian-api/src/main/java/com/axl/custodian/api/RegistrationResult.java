package com.axl.custodian.api;
import java.util.Objects;
/** Outcome of native-free, UUID-only identity adoption. */
public record RegistrationResult(Status status, IdentitySnapshot identity) {
    public enum Status { CREATED, ALREADY_REGISTERED, CONFLICT }
    public RegistrationResult { Objects.requireNonNull(status, "status"); Objects.requireNonNull(identity, "identity"); }
}
