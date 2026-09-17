package com.axl.custodian.core;

import com.axl.custodian.api.AuthorityHandle;
import com.axl.custodian.api.CustodianApi;
import com.axl.custodian.api.IdentitySnapshot;
import com.axl.custodian.api.IdentityState;
import com.axl.custodian.api.RegistrationResult;
import com.axl.custodian.api.ValidationReason;
import com.axl.custodian.api.ValidationResult;
import com.axl.custodian.api.PhysicalPresence;
import com.axl.custodian.api.ObservationResult;
import com.axl.custodian.api.DuplicateAssessment;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Narrow core facade. Paper item reads/writes intentionally live outside this class. */
public final class IdentityService implements CustodianApi {
    private final CustodianStore store;
    private final Clock clock;
    private final PresenceService presences;
    public IdentityService(CustodianStore store, Clock clock) { this(store, clock, new PresenceService(store, clock, java.time.Duration.ofSeconds(30))); }
    public IdentityService(CustodianStore store, Clock clock, PresenceService presences) { this.store = Objects.requireNonNull(store); this.clock = Objects.requireNonNull(clock); this.presences = Objects.requireNonNull(presences); }
    public RegistrationResult adopt(AuthorityHandle authority, UUID identity) {
        Objects.requireNonNull(authority); Objects.requireNonNull(identity);
        var existing = store.findIdentity(identity);
        if (existing.isPresent()) {
            var snapshot = existing.get();
            return new RegistrationResult(snapshot.authorityId().equals(authority.id()) ? RegistrationResult.Status.ALREADY_REGISTERED : RegistrationResult.Status.CONFLICT, snapshot);
        }
        return new RegistrationResult(RegistrationResult.Status.CREATED, store.adopt(identity, authority.id(), clock.instant()));
    }
    public ValidationResult validate(UUID identity) {
        if (identity == null) return new ValidationResult(ValidationReason.MALFORMED_IDENTITY, null);
        return store.findIdentity(identity).map(snapshot -> new ValidationResult(switch (snapshot.state()) {
            case ACTIVE -> ValidationReason.VALID; case PENDING -> ValidationReason.PENDING;
            case QUARANTINED -> ValidationReason.QUARANTINED; case REVOKED -> ValidationReason.REVOKED;
        }, snapshot)).orElseGet(() -> new ValidationResult(ValidationReason.UNKNOWN_IDENTITY, null));
    }
    @Override public ObservationResult observe(PhysicalPresence presence) {
        if (!presences.isRegistered(presence.identity())) return new ObservationResult(ObservationResult.Status.UNKNOWN_IDENTITY, new DuplicateAssessment(DuplicateAssessment.Status.NONE, java.util.List.of()));
        presences.observe(presence);
        return new ObservationResult(ObservationResult.Status.OBSERVED, presences.assess(presence.identity()));
    }
    @Override public void startEpoch(com.axl.custodian.api.ProcessEpoch epoch) { presences.start(epoch); }
}
