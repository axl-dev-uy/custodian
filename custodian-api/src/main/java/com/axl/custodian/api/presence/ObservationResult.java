package com.axl.custodian.api.presence;

import java.util.Objects;

/** Result of an accepted read-only physical observation. */
public record ObservationResult(Status status, DuplicateAssessment assessment) {
    public enum Status { OBSERVED, UNKNOWN_IDENTITY }
    public ObservationResult { Objects.requireNonNull(status, "status"); Objects.requireNonNull(assessment, "assessment"); }
}
