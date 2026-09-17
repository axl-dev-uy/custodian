package com.axl.custodian.api;

/**
 * Deliberately powerless external comparison channel. Implementations accept only
 * PARTIAL identity subsets; they expose no scope ownership or COMPLETE operations.
 */
public interface ShadowContributor {
    BridgeHandle startBridgeEpoch(AuthorityHandle authority, String serverId);
    void heartbeat(BridgeHandle bridge);
    DuplicateAssessment contribute(BridgeHandle bridge, ScopeContribution contribution);
}
