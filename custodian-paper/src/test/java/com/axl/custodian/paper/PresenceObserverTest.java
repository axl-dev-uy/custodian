package com.axl.custodian.paper;

import com.axl.custodian.api.PhysicalInstance;
import com.axl.custodian.api.ProcessEpoch;
import com.axl.custodian.api.AuthorityHandle;
import com.axl.custodian.core.PresenceService;
import com.axl.custodian.core.sqlite.SqliteCustodianStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceObserverTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:30Z");
    @Test void repeatedNativePlayerAndContainerScansUseStableInstanceIds() {
        UUID id = UUID.randomUUID(); Fixture fixture = fixture(id); Player player = mock(Player.class); Inventory playerInventory = inventory(player, item(id));
        fixture.observer.scan(playerInventory); fixture.observer.scan(playerInventory);
        assertEquals(1, fixture.store.activePresences(id, NOW.minusSeconds(1)).size());
        DoubleChest chest = mock(DoubleChest.class); Inventory combined = mock(Inventory.class); Location combinedLocation = location(); when(combined.getLocation()).thenReturn(combinedLocation);
        InventoryHolder left = blockSide(1, combined), right = blockSide(2, combined); when(chest.getLeftSide()).thenReturn(left); when(chest.getRightSide()).thenReturn(right); Inventory chestInventory = inventory(chest, item(id)); Location chestLocation = location(); when(chestInventory.getLocation()).thenReturn(chestLocation);
        fixture.observer.scan(chestInventory); fixture.observer.scan(chestInventory);
        var active = fixture.store.activePresences(id, NOW.minusSeconds(1));
        assertEquals(2, active.size()); assertEquals(2, active.stream().map(p -> p.instance().id()).distinct().count());
        assertTrue(active.stream().map(p -> p.instance().id()).anyMatch(idValue -> idValue.equals(
                "double-block:00000000-0000-0000-0000-000000000001:1:64:1:"
                        + "00000000-0000-0000-0000-000000000001:2:64:1:slot:0")));
        fixture.close();
    }
    @Test void entityAndDropScopesAreStableAndDropCleanupIsIdempotent() {
        UUID id = UUID.randomUUID(); Fixture fixture = fixture(id); StorageMinecart cart = mock(StorageMinecart.class); UUID cartId = UUID.randomUUID(); Location cartLocation = location(); when(cart.getUniqueId()).thenReturn(cartId); when(cart.getLocation()).thenReturn(cartLocation);
        Inventory cartInventory = inventory(cart, item(id)); fixture.observer.scan(cartInventory); fixture.observer.scan(cartInventory);
        assertEquals(1, fixture.store.activePresences(id, NOW.minusSeconds(1)).size());
        Item dropped = mock(Item.class); UUID dropId = UUID.randomUUID(); ItemStack droppedStack = item(id); Location dropLocation = location(); when(dropped.getUniqueId()).thenReturn(dropId); when(dropped.getItemStack()).thenReturn(droppedStack); when(dropped.getLocation()).thenReturn(dropLocation);
        fixture.observer.observeDrop(dropped); fixture.observer.observeDrop(dropped); fixture.observer.clearDrop(dropId); fixture.observer.clearDrop(dropId);
        assertEquals(1, fixture.store.activePresences(id, NOW.minusSeconds(1)).size());
        fixture.close();
    }
    @Test void dropAndInventoryOverlapKeepTwoDistinctPhysicalScopes() {
        UUID id=UUID.randomUUID(); Fixture fixture=fixture(id); Player player=mock(Player.class); Inventory inventory=inventory(player,item(id)); fixture.observer.scan(inventory);
        Item drop=mock(Item.class); UUID dropId=UUID.randomUUID(); Location dropLocation=location(); ItemStack droppedItem=item(id); when(drop.getUniqueId()).thenReturn(dropId); when(drop.getItemStack()).thenReturn(droppedItem); when(drop.getLocation()).thenReturn(dropLocation);
        fixture.observer.observeDrop(drop); assertEquals(2,fixture.store.activePresences(id,NOW.minusSeconds(1)).size()); fixture.observer.clearDrop(dropId); assertEquals(1,fixture.store.activePresences(id,NOW.minusSeconds(1)).size()); fixture.close();
    }
    @Test void everyRemainingSupportedNativeHolderUsesAStableScope() {
        UUID id = UUID.randomUUID(); Fixture fixture = fixture(id); Location holderLocation = location();
        Inventory block = inventory(null, item(id)); when(block.getLocation()).thenReturn(holderLocation); when(block.getType()).thenReturn(InventoryType.HOPPER);
        HopperMinecart hopper = mock(HopperMinecart.class); when(hopper.getUniqueId()).thenReturn(UUID.randomUUID()); when(hopper.getLocation()).thenReturn(holderLocation);
        ChestedHorse horse = mock(ChestedHorse.class); when(horse.getUniqueId()).thenReturn(UUID.randomUUID()); when(horse.getLocation()).thenReturn(holderLocation);
        fixture.observer.scan(block); fixture.observer.scan(inventory(hopper, item(id))); fixture.observer.scan(inventory(horse, item(id)));
        fixture.observer.scan(block); fixture.observer.scan(inventory(hopper, item(id))); fixture.observer.scan(inventory(horse, item(id)));
        assertEquals(3, fixture.store.activePresences(id, NOW.minusSeconds(1)).size()); fixture.close();
    }
    @Test void unregisteringProviderStopsItsVirtualInventoryFromBeingObserved() {
        UUID id = UUID.randomUUID(); Fixture fixture = fixture(id); Plugin owner = mock(Plugin.class); Inventory virtual = inventory(mock(org.bukkit.inventory.InventoryHolder.class), item(id));
        fixture.observer.register(owner, new PhysicalInventoryProvider() { public boolean supports(Inventory inventory) { return inventory == virtual; } public String scopeId(Inventory inventory) { return "virtual:test"; } public String location(Inventory inventory) { return "virtual:test"; } });
        fixture.observer.scan(virtual); assertTrue(fixture.store.activePresences(id, NOW.minusSeconds(1)).isEmpty(), "virtual scopes require their own provider authority");
        fixture.observer.unregister(owner); fixture.observer.scan(virtual); assertTrue(fixture.store.activePresences(id, NOW.minusSeconds(1)).isEmpty(), "provider disable clears its active virtual-custody scope");
        fixture.close();
    }
    private Fixture fixture(UUID identity) {
        SqliteCustodianStore store = new SqliteCustodianStore(directory.resolve(UUID.randomUUID() + ".db")); store.adopt(identity, "test", NOW);
        PresenceService service = new PresenceService(store, Clock.fixed(NOW, ZoneOffset.UTC), java.time.Duration.ofSeconds(10)); ProcessEpoch epoch = new ProcessEpoch(UUID.randomUUID(), "alpha", NOW, NOW); AuthorityHandle owner = AuthorityHandle.issuedByHost("custodian-native"); service.bind(owner, epoch);
        return new Fixture(store, new PresenceObserver(service, epoch, Clock.fixed(NOW, ZoneOffset.UTC), owner));
    }
    private static Inventory inventory(InventoryHolder holder, ItemStack item) { Inventory inventory = mock(Inventory.class); when(inventory.getHolder()).thenReturn(holder); when(inventory.getContents()).thenReturn(new ItemStack[]{item}); return inventory; }
    private static ItemStack item(UUID id) { ItemStack item = mock(ItemStack.class); ItemMeta meta = mock(ItemMeta.class); PersistentDataContainer pdc = mock(PersistentDataContainer.class); when(item.hasItemMeta()).thenReturn(true); when(item.getItemMeta()).thenReturn(meta); when(meta.getPersistentDataContainer()).thenReturn(pdc); when(pdc.get(new NamespacedKey("custodian", "identity"), PersistentDataType.STRING)).thenReturn(id.toString()); return item; }
    private static Location location() {
        Location location = mock(Location.class); World world = mock(World.class); UUID worldId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(location.getWorld()).thenReturn(world); when(world.getUID()).thenReturn(worldId);
        when(location.getBlockX()).thenReturn(1); when(location.getBlockY()).thenReturn(64); when(location.getBlockZ()).thenReturn(1);
        return location;
    }
    private static InventoryHolder blockSide(int x, Inventory combinedInventory) {
        Container holder = mock(Container.class); Location location = location(); World world = location.getWorld();
        when(location.getBlockX()).thenReturn(x); when(holder.getLocation()).thenReturn(location);
        when(holder.getWorld()).thenReturn(world); when(holder.getInventory()).thenReturn(combinedInventory);
        return holder;
    }
    private record Fixture(SqliteCustodianStore store, PresenceObserver observer) { void close() { store.close(); } }
}
