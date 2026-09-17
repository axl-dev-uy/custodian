package com.axl.custodian.core;
import com.axl.custodian.api.*;
/** Fail-closed authorization facade for future external scanners. */
public final class AuthorizedReconciler {
 private final CustodianStore store; private final PresenceService presences;
 public AuthorizedReconciler(CustodianStore store, PresenceService presences){this.store=store;this.presences=presences;}
 public void reconcile(AuthorityHandle owner, PresenceReconciliation request){
  if(!store.scopeOwner(request.ownerScope()).filter(owner.id()::equals).equals(java.util.Optional.of(owner.id()))) throw new IllegalArgumentException("Unowned or foreign scope");
  if(!store.epochOwner(request.epoch().id()).filter(owner.id()::equals).equals(java.util.Optional.of(owner.id()))) throw new IllegalArgumentException("Foreign or unbound epoch");
  presences.reconcile(request);
 }
}
