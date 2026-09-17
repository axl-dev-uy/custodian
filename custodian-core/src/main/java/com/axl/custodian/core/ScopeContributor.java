package com.axl.custodian.core;
import com.axl.custodian.api.*;
/** Read-only bridge ingress: no scope registration and no COMPLETE reconciliation. */
public final class ScopeContributor {
 private final PresenceService presences;
 public ScopeContributor(PresenceService presences){this.presences=presences;}
 public void contribute(ProcessEpoch epoch, ScopeContribution contribution){
  for(PhysicalPresence p:contribution.presences()) if(!p.epoch().id().equals(epoch.id())||!p.instance().id().startsWith(contribution.scope().id()+":")) throw new IllegalArgumentException("Contribution outside native scope");
  presences.reconcile(new PresenceReconciliation(epoch,contribution.scope().id(),ReconciliationMode.PARTIAL,contribution.presences()));
 }
}
