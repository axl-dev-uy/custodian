package com.axl.custodian.api.shadow;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.ScannerScope;
import java.util.List;
import java.util.Objects;
/** Identity subset supplied to the native scope owner; contributions can never close scope presence. */
public record ScopeContribution(ScannerScope scope, List<PhysicalPresence> presences) { public ScopeContribution { Objects.requireNonNull(scope); presences=List.copyOf(presences); } }
