package com.axl.custodian.core.rekey;

import com.axl.custodian.api.identity.IdentityOrigin;
import com.axl.custodian.api.identity.IdentitySnapshot;
import com.axl.custodian.api.identity.IdentityState;
import com.axl.custodian.api.identity.RekeyEligibility;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RekeyPolicyTest {
    @Test void adoptedActiveIdentityRequiresFutureAliasIntegration() {
        var identity = new IdentitySnapshot(UUID.randomUUID(), IdentityState.ACTIVE, IdentityOrigin.ADOPTED,
                "infinitygear", Instant.EPOCH, Instant.EPOCH, null);
        var result = RekeyPolicy.eligibility(identity);
        assertFalse(result.allowed());
        assertEquals(RekeyEligibility.Reason.ADOPTED_IDENTITY_ALIAS_REQUIRED, result.reason());
    }
    @Test void onlyActiveCustodianNativeIdentityIsEligible() {
        var identity = new IdentitySnapshot(UUID.randomUUID(), IdentityState.ACTIVE, IdentityOrigin.CUSTODIAN_NATIVE,
                "custodian", Instant.EPOCH, Instant.EPOCH, null);
        assertTrue(RekeyPolicy.eligibility(identity).allowed());
    }
}
