package com.axl.custodian.api;

import com.axl.custodian.api.identity.AuthorityHandle;
import com.axl.custodian.api.presence.DuplicateAssessment;
import com.axl.custodian.api.shadow.BridgeHandle;
import com.axl.custodian.api.shadow.ScopeContribution;

/**
 * Deliberately powerless external comparison channel. Implementations accept only
 * PARTIAL identity subsets; they expose no scope ownership or COMPLETE operations.
 */
public interface ShadowContributor {
    BridgeHandle startBridgeEpoch(AuthorityHandle authority, String serverId);
    void heartbeat(BridgeHandle bridge);
    DuplicateAssessment contribute(BridgeHandle bridge, ScopeContribution contribution);
}
