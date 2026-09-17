package com.axl.custodian.api.identity;
import java.util.Objects;
/** Opaque authority issued by the host only to a registered integration. */
public final class AuthorityHandle {
    private final String id;
    private AuthorityHandle(String id) { this.id = Objects.requireNonNull(id, "id"); }
    public static AuthorityHandle issuedByHost(String id) { if (id.isBlank()) throw new IllegalArgumentException("id must not be blank"); return new AuthorityHandle(id); }
    public String id() { return id; }
}
