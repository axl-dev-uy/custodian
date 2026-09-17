package com.axl.custodian.core.presence;
import com.axl.custodian.api.identity.AuthorityHandle;
import com.axl.custodian.api.presence.PresenceReconciliation;
import com.axl.custodian.core.storage.CustodianStore;
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
