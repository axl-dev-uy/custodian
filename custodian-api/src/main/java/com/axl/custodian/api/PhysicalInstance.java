package com.axl.custodian.api;

import java.util.Objects;

/** Stable identifier for one physical custody slot/entity, never an inventory wrapper or GUI view. */
public record PhysicalInstance(String id) {
    public PhysicalInstance { Objects.requireNonNull(id, "id"); if (id.isBlank()) throw new IllegalArgumentException("id must not be blank"); }
}
