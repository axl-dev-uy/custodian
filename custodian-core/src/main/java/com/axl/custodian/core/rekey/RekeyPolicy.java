package com.axl.custodian.core.rekey;

import com.axl.custodian.api.identity.IdentityOrigin;
import com.axl.custodian.api.identity.IdentitySnapshot;
import com.axl.custodian.api.identity.IdentityState;
import com.axl.custodian.api.identity.RekeyEligibility;

/** Domain gate used by the future rekey operation before it can touch an item. */
final class RekeyPolicy {
    private RekeyPolicy() { }
    static RekeyEligibility eligibility(IdentitySnapshot identity) {
        if (identity.state() != IdentityState.ACTIVE) return new RekeyEligibility(false, RekeyEligibility.Reason.IDENTITY_NOT_ACTIVE);
        return identity.origin() == IdentityOrigin.CUSTODIAN_NATIVE
                ? new RekeyEligibility(true, RekeyEligibility.Reason.ELIGIBLE)
                : new RekeyEligibility(false, RekeyEligibility.Reason.ADOPTED_IDENTITY_ALIAS_REQUIRED);
    }
}
