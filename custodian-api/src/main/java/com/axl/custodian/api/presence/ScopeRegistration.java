package com.axl.custodian.api.presence;
import com.axl.custodian.api.identity.AuthorityHandle;
import java.util.Objects;
public record ScopeRegistration(AuthorityHandle owner, ScannerScope scope) { public ScopeRegistration { Objects.requireNonNull(owner); Objects.requireNonNull(scope); } }
