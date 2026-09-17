package com.axl.custodian.core;
import java.time.Instant; import java.util.UUID;
public record BridgeLifecycle(String authorityId, UUID epochId, String serverId, Instant startedAt, Instant heartbeatAt, boolean active) { }
