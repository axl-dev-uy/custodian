package com.axl.custodian.paper;
import com.axl.custodian.api.*; import com.axl.custodian.core.*; import java.time.*; import java.util.*;
/** Paper service; handles are in-memory capabilities backed by persisted bridge epoch state. */
final class PaperShadowContributor implements ShadowContributor {
 private final CustodianStore store; private final PresenceService presences; private final Map<UUID,ProcessEpoch> handles=new HashMap<>();
 PaperShadowContributor(CustodianStore store,PresenceService presences){this.store=store;this.presences=presences;}
 public synchronized BridgeHandle startBridgeEpoch(AuthorityHandle authority,String server){ProcessEpoch e=new ProcessEpoch(UUID.randomUUID(),server,Instant.now(),Instant.now());store.startBridge(authority.id(),e);store.bindEpoch(authority.id(),e);UUID token=UUID.randomUUID();handles.put(token,e);return new BridgeHandle(token,authority.id());}
 public synchronized void heartbeat(BridgeHandle h){ProcessEpoch e=require(h);store.heartbeatBridge(e.id(),Instant.now());store.heartbeat(e.id(),Instant.now());}
 public synchronized DuplicateAssessment contribute(BridgeHandle h,ScopeContribution c){ProcessEpoch e=require(h);var b=store.bridge(e.id()).orElseThrow(()->new IllegalArgumentException("Unknown bridge"));if(!b.active())throw new IllegalArgumentException("Inactive bridge");new ScopeContributor(presences).contribute(e,c);UUID id=c.presences().isEmpty()?null:c.presences().get(0).identity();return id==null?new DuplicateAssessment(DuplicateAssessment.Status.NONE,List.of()):presences.assess(id);}
 synchronized void shutdown(){for(ProcessEpoch e:handles.values())store.invalidateBridge(e.id());handles.clear();}
 private ProcessEpoch require(BridgeHandle h){ProcessEpoch e=handles.get(h.epochId());if(e==null||!h.contributorId().equals(store.bridge(e.id()).map(BridgeLifecycle::authorityId).orElse(null)))throw new IllegalArgumentException("Invalid bridge handle");return e;}
}
