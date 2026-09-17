package com.axl.custodian.api;

/** Explicit v1 boundary: adopted identities cannot be rekeyed. */
public record RekeyEligibility(boolean allowed, Reason reason) {
    public enum Reason { ELIGIBLE, ADOPTED_IDENTITY_ALIAS_REQUIRED, IDENTITY_NOT_ACTIVE }
}
