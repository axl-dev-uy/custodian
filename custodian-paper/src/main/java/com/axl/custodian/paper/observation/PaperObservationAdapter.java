package com.axl.custodian.paper.observation;

import com.axl.custodian.api.presence.ProcessEpoch;
import com.axl.custodian.api.identity.AuthorityHandle;
import com.axl.custodian.core.presence.PresenceService;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

/** Conservative event capture: observe now, reconcile affected scopes on the next settled server tick. */
/** Coordinates deferred Paper observation without exposing storage or reconciliation details. */
public final class PaperObservationAdapter implements Listener {
    private final JavaPlugin plugin; private final PresenceService service; private final PresenceObserver observer;
    private final ProcessEpoch epoch; private final Set<Inventory> deferred = Collections.newSetFromMap(new IdentityHashMap<>());
    private final AuthorityHandle owner;
    private int heartbeatTask = -1;
    public PaperObservationAdapter(JavaPlugin plugin, PresenceService service, AuthorityHandle owner, String serverId, Duration freshness, Clock clock) {
        this.plugin = plugin; this.service = service; this.epoch = new ProcessEpoch(UUID.randomUUID(), serverId, clock.instant(), clock.instant());
        this.owner=owner; this.observer = new PresenceObserver(service, epoch, clock, owner); service.bind(owner, epoch);
        long ticks = Math.max(1L, freshness.toSeconds() * 10L); heartbeatTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> service.heartbeat(epoch.id()), ticks, ticks);
    }
    public void stop() { if (heartbeatTask != -1) Bukkit.getScheduler().cancelTask(heartbeatTask); service.release(owner); }
    public void register(Plugin owner, PhysicalInventoryProvider provider) { observer.register(owner, provider); }
    @EventHandler public void onPluginDisable(PluginDisableEvent event) { observer.unregister(event.getPlugin()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { deferPlayer(event.getPlayer()); }
    @EventHandler public void onOpen(InventoryOpenEvent event) { defer(event.getInventory()); if (event.getPlayer() instanceof Player player) deferPlayer(player); }
    @EventHandler public void onClose(InventoryCloseEvent event) { defer(event.getInventory()); if (event.getPlayer() instanceof Player player) deferPlayer(player); }
    @EventHandler public void onClick(InventoryClickEvent event) { defer(event.getInventory()); defer(event.getWhoClicked().getInventory()); }
    @EventHandler public void onDrag(InventoryDragEvent event) { defer(event.getInventory()); defer(event.getWhoClicked().getInventory()); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { safely(() -> observer.observeDrop(event.getItemDrop())); deferPlayer(event.getPlayer()); }
    @EventHandler public void onPickup(EntityPickupItemEvent event) {
        UUID dropped = event.getItem().getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> { if (!event.isCancelled()) safely(() -> observer.clearDrop(dropped)); if (event.getEntity() instanceof Player player) deferPlayer(player); });
    }
    private void deferPlayer(Player player) { defer(player.getInventory()); defer(player.getEnderChest()); }
    private void defer(Inventory inventory) {
        if (!deferred.add(inventory)) return;
        Bukkit.getScheduler().runTask(plugin, () -> { deferred.remove(inventory); safely(() -> observer.scan(inventory)); });
    }
    private void safely(Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException failure) {
            plugin.getLogger().warning("Custodian observation storage failure; reconciliation will retry: " + failure.getMessage());
            Bukkit.getScheduler().runTaskLater(plugin, operation, 20L);
        }
    }
}
