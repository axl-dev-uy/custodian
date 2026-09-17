package com.axl.custodian.core.presence;
import com.axl.custodian.api.identity.AuthorityHandle;
import com.axl.custodian.api.presence.PhysicalInstance;
import com.axl.custodian.api.presence.PhysicalPresence;
import com.axl.custodian.api.presence.PresenceReconciliation;
import com.axl.custodian.api.presence.ProcessEpoch;
import com.axl.custodian.api.presence.ReconciliationMode;
import com.axl.custodian.core.storage.sqlite.SqliteCustodianStore;
import java.nio.file.Path; import java.time.*; import java.util.*;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class AuthorizedReconcilerTest {
 @TempDir Path dir; private final Instant now=Instant.parse("2026-01-01T00:00:30Z");
 @Test void ownerCanReconcileButForeignUnownedAndStaleRequestsFailBeforeWrite(){
  try(var store=new SqliteCustodianStore(dir.resolve("auth.db"))){
   UUID id=UUID.randomUUID(); store.adopt(id,"a",now); var service=new PresenceService(store,Clock.fixed(now,ZoneOffset.UTC),Duration.ofSeconds(10)); var authA=AuthorityHandle.issuedByHost("a"); var authB=AuthorityHandle.issuedByHost("b"); var epoch=new ProcessEpoch(UUID.randomUUID(),"server",now,now); store.bindEpoch("a",epoch); store.registerScope("a","native:player"); var r=new AuthorizedReconciler(store,service); var request=new PresenceReconciliation(epoch,"native:player",ReconciliationMode.COMPLETE,List.of(new PhysicalPresence(id,epoch,new PhysicalInstance("native:player:slot:0"),"slot",now)));
   r.reconcile(authA,request); assertEquals(1,store.activePresences(id,now.minusSeconds(1)).size());
   assertThrows(IllegalArgumentException.class,()->r.reconcile(authB,request));
   assertThrows(IllegalArgumentException.class,()->r.reconcile(authA,new PresenceReconciliation(epoch,"unknown",ReconciliationMode.COMPLETE,List.of())));
   store.heartbeat(epoch.id(),now.minusSeconds(11)); assertThrows(IllegalArgumentException.class,()->r.reconcile(authA,request));
   assertEquals(1,store.activePresences(id,now.minusSeconds(20)).size());
  }
 }
 @Test void conflictingScopeRegistrationIsRejectedAndReleaseRetainsIdentity(){
  try(var store=new SqliteCustodianStore(dir.resolve("release.db"))){ UUID id=UUID.randomUUID(); store.adopt(id,"a",now); store.registerScope("a","virtual:a"); assertThrows(IllegalArgumentException.class,()->store.registerScope("b","virtual:a")); store.releaseAuthority("a"); assertTrue(store.scopeOwner("virtual:a").isEmpty()); assertTrue(store.findIdentity(id).isPresent()); }
 }
 @Test void providerScopeRejectsForeignHandleAndReleaseClosesOnlyScopePresence(){
  try(var store=new SqliteCustodianStore(dir.resolve("provider.db"))){
   UUID id=UUID.randomUUID(); store.adopt(id,"provider-a",now); var service=new PresenceService(store,Clock.fixed(now,ZoneOffset.UTC),Duration.ofSeconds(10)); var a=AuthorityHandle.issuedByHost("provider-a"); var b=AuthorityHandle.issuedByHost("provider-b"); var epoch=new ProcessEpoch(UUID.randomUUID(),"server",now,now); store.bindEpoch("provider-a",epoch); store.registerScope("provider-a","virtual:a"); var request=new PresenceReconciliation(epoch,"virtual:a",ReconciliationMode.COMPLETE,List.of(new PhysicalPresence(id,epoch,new PhysicalInstance("virtual:a:slot:0"),"virtual",now))); var reconciler=new AuthorizedReconciler(store,service);
   reconciler.reconcile(a,request); assertThrows(IllegalArgumentException.class,()->reconciler.reconcile(b,request)); assertEquals(1,store.activePresences(id,now.minusSeconds(1)).size());
   store.releaseAuthority("provider-a"); assertTrue(store.scopeOwner("virtual:a").isEmpty()); assertTrue(store.activePresences(id,now.minusSeconds(1)).isEmpty()); assertTrue(store.findIdentity(id).isPresent());
  }
 }
}
