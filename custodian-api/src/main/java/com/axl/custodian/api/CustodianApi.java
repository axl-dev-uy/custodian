package com.axl.custodian.api;

import com.axl.custodian.api.identity.AuthorityHandle;
import com.axl.custodian.api.identity.IdentityOrigin;
import com.axl.custodian.api.identity.RegistrationResult;
import com.axl.custodian.api.identity.ValidationResult;
import com.axl.custodian.api.presence.ObservationResult;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.ProcessEpoch;
import java.util.UUID;

/** Stable integration surface. It has no Paper, JDBC, repository, or mutable-item types. */
public interface CustodianApi {
    /**
     * Records an already-existing logical UUID as {@link IdentityOrigin#ADOPTED}.
     * This API intentionally accepts no item or Paper type and cannot mutate item data.
     */
    RegistrationResult adopt(AuthorityHandle authority, UUID identity);
    /** Reads authoritative lifecycle state without mutating identity or item data. */
    ValidationResult validate(UUID identity);
    /** Records an already-extracted physical observation; callers retain all item mutation responsibility. */
    ObservationResult observe(PhysicalPresence presence);
    void startEpoch(ProcessEpoch epoch);
}
