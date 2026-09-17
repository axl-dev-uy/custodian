package com.axl.custodian.api;

import java.util.List;
import java.util.Objects;

/** Snapshot of liveness after freshness reconciliation; historical sightings are intentionally excluded. */
public record DuplicateAssessment(Status status, List<PhysicalPresence> activePresences) {
    public enum Status { NONE, ONE_ACTIVE, CONFIRMED_DISTINCT_ACTIVE }
    public DuplicateAssessment { Objects.requireNonNull(status, "status"); activePresences = List.copyOf(activePresences); }
    public boolean duplicateConfirmed() { return status == Status.CONFIRMED_DISTINCT_ACTIVE; }
}
