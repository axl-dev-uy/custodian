package com.axl.custodian.paper.observation;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Read-only wire parser. It never writes, repairs, or normalizes item PDC. */
final class CustodianIdentityReader {
    private static final NamespacedKey IDENTITY = new NamespacedKey("custodian", "identity");
    private CustodianIdentityReader() { }
    static Optional<UUID> read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        String encoded = item.getItemMeta().getPersistentDataContainer().get(IDENTITY, PersistentDataType.STRING);
        try { return encoded == null ? Optional.empty() : Optional.of(UUID.fromString(encoded)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
}
