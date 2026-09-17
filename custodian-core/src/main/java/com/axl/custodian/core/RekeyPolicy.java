package com.axl.custodian.core;

import com.axl.custodian.api.IdentityOrigin;
import com.axl.custodian.api.IdentitySnapshot;
import com.axl.custodian.api.IdentityState;
import com.axl.custodian.api.RekeyEligibility;

/** Domain gate used by the future rekey operation before it can touch an item. */
public final class RekeyPolicy {
    private RekeyPolicy() { }
    public static RekeyEligibility eligibility(IdentitySnapshot identity) {
        if (identity.state() != IdentityState.ACTIVE) return new RekeyEligibility(false, RekeyEligibility.Reason.IDENTITY_NOT_ACTIVE);
        return identity.origin() == IdentityOrigin.CUSTODIAN_NATIVE
                ? new RekeyEligibility(true, RekeyEligibility.Reason.ELIGIBLE)
                : new RekeyEligibility(false, RekeyEligibility.Reason.ADOPTED_IDENTITY_ALIAS_REQUIRED);
    }
}
