package com.axl.custodian.core;

import com.axl.custodian.api.AuthorityHandle;
import com.axl.custodian.api.IdentityOrigin;
import com.axl.custodian.api.ValidationReason;
import com.axl.custodian.core.sqlite.SqliteCustodianStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IdentityServiceTest {
    @TempDir Path directory;
    @Test void infinityGearAdoptionPreservesUuidAndIsReadOnlyAtTheApiBoundary() {
        try (var store = new SqliteCustodianStore(directory.resolve("service.db"))) {
            var service = new IdentityService(store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)); UUID id = UUID.randomUUID();
            var created = service.adopt(AuthorityHandle.issuedByHost("infinitygear"), id);
            assertEquals("CREATED", created.status().name());
            assertEquals(id, created.identity().identity());
            assertEquals(IdentityOrigin.ADOPTED, created.identity().origin());
            assertEquals("infinitygear", created.identity().authorityId());
            assertEquals("ALREADY_REGISTERED", service.adopt(AuthorityHandle.issuedByHost("infinitygear"), id).status().name());
            assertEquals("CONFLICT", service.adopt(AuthorityHandle.issuedByHost("other"), id).status().name());
            assertEquals(ValidationReason.VALID, service.validate(id).reason());
        }
    }
}
