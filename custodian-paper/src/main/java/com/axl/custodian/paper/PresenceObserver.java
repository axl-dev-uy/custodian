package com.axl.custodian.paper;

import com.axl.custodian.api.PhysicalInstance;
import com.axl.custodian.api.PhysicalPresence;
import com.axl.custodian.api.ProcessEpoch;
import com.axl.custodian.core.PresenceService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.Plugin;
import com.axl.custodian.api.AuthorityHandle;
import com.axl.custodian.api.PresenceReconciliation;
import com.axl.custodian.api.ReconciliationMode;

/** Main-thread scanner for explicit physical custody boundaries only. */
final class PresenceObserver {
    private final PresenceService service; private final ProcessEpoch epoch; private final Clock clock; private final AuthorityHandle nativeOwner;
    private final Map<Plugin, PhysicalInventoryProvider> virtualProviders = new LinkedHashMap<>();
    private final Map<Plugin, Set<String>> providerScopes = new LinkedHashMap<>();
    PresenceObserver(PresenceService service, ProcessEpoch epoch, Clock clock, AuthorityHandle nativeOwner) { this.service = service; this.epoch = epoch; this.clock = clock; this.nativeOwner=nativeOwner; }
    void register(Plugin owner, PhysicalInventoryProvider provider) { virtualProviders.put(owner, provider); }
    void unregister(Plugin owner) {
        virtualProviders.remove(owner);
        for (String scope : providerScopes.getOrDefault(owner, Set.of())) service.reconcile(epoch, scope, List.of());
        providerScopes.remove(owner);
    }
    void scan(Inventory inventory) {
        Scope scope = scope(inventory); if (scope == null || scope.virtual) return;
        List<PhysicalPresence> observed = new ArrayList<>(); ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            UUID identity = CustodianIdentityReader.read(contents[slot]).orElse(null);
            if (identity != null && service.isRegistered(identity)) observed.add(new PhysicalPresence(identity, epoch,
                    new PhysicalInstance(scope.id + ":slot:" + slot), scope.location + "/slot/" + slot, clock.instant()));
        }
        service.registerScope(nativeOwner, scope.id); service.reconcileAuthorized(nativeOwner, new PresenceReconciliation(epoch, scope.id, ReconciliationMode.COMPLETE, observed));
    }
    void scanPlayer(Player player) { scan(player.getInventory()); scan(player.getEnderChest()); }
    void observeDrop(org.bukkit.entity.Item item) {
        UUID identity = CustodianIdentityReader.read(item.getItemStack()).orElse(null); String scope = "drop:" + item.getUniqueId();
        service.registerScope(nativeOwner, scope);
        if (identity == null || !service.isRegistered(identity)) { service.reconcileAuthorized(nativeOwner, new PresenceReconciliation(epoch, scope, ReconciliationMode.COMPLETE, List.of())); return; }
        service.reconcileAuthorized(nativeOwner, new PresenceReconciliation(epoch, scope, ReconciliationMode.COMPLETE, List.of(new PhysicalPresence(identity, epoch, new PhysicalInstance(scope + ":item"), location(item.getLocation()), clock.instant()))));
    }
    void clearDrop(UUID entityId) { String scope="drop:"+entityId; service.registerScope(nativeOwner, scope); service.reconcileAuthorized(nativeOwner, new PresenceReconciliation(epoch, scope, ReconciliationMode.COMPLETE, List.of())); }
    private Scope scope(Inventory inventory) {
        for (Map.Entry<Plugin, PhysicalInventoryProvider> entry : virtualProviders.entrySet()) if (entry.getValue().supports(inventory)) {
            String id = entry.getValue().scopeId(inventory); providerScopes.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).add(id);
            return new Scope(id, entry.getValue().location(inventory), true);
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Player player) return new Scope("player:" + player.getUniqueId() + (inventory.equals(player.getEnderChest()) ? ":ender" : ":inventory"), "player:" + player.getUniqueId(), false);
        if (holder instanceof DoubleChest chest) {
            String left = blockLocation(chest.getLeftSide()), right = blockLocation(chest.getRightSide());
            if (left == null || right == null) return null;
            String first = left.compareTo(right) <= 0 ? left : right, second = left.compareTo(right) <= 0 ? right : left;
            return new Scope("double-block:" + first + ":" + second, first + ":" + second, false);
        }
        Location inventoryLocation = inventory.getLocation();
        if (inventoryLocation != null && nativeBlockType(inventory.getType())) { String location = location(inventoryLocation); return new Scope("block:" + location, location, false); }
        if (holder instanceof Entity entity && (entity instanceof StorageMinecart || entity instanceof HopperMinecart || entity instanceof ChestedHorse)) return new Scope("entity:" + entity.getUniqueId(), location(entity.getLocation()), false);
        return null;
    }
    private static String location(Location location) { return location.getWorld() == null ? "unknown" : location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ(); }
    private static String blockLocation(InventoryHolder holder) {
        if (!(holder instanceof BlockState blockState)
                || blockState.getWorld() == null || blockState.getLocation() == null) return null;
        Location location = blockState.getLocation();
        return blockState.getWorld().getUID() + ":" + location.getBlockX() + ":"
                + location.getBlockY() + ":" + location.getBlockZ();
    }
    private static boolean nativeBlockType(InventoryType type) {
        return type == InventoryType.CHEST || type == InventoryType.DISPENSER || type == InventoryType.DROPPER
                || type == InventoryType.FURNACE || type == InventoryType.BREWING || type == InventoryType.HOPPER
                || type == InventoryType.SHULKER_BOX || type == InventoryType.BARREL || type == InventoryType.BLAST_FURNACE
                || type == InventoryType.SMOKER;
    }
    private record Scope(String id, String location, boolean virtual) { }
}
