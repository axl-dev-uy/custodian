package com.axl.custodian.api;
import java.util.Objects;
public record ScopeRegistration(AuthorityHandle owner, ScannerScope scope) { public ScopeRegistration { Objects.requireNonNull(owner); Objects.requireNonNull(scope); } }
