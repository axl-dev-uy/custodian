package com.axl.custodian.api;
import java.util.Objects;
public record ScannerScope(String id) { public ScannerScope { Objects.requireNonNull(id); if(id.isBlank()) throw new IllegalArgumentException("id"); } }
