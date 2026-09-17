package com.axl.custodian.paper;

import org.bukkit.inventory.Inventory;

/** Opt-in boundary for another plugin's virtual inventory that represents real item custody. */
public interface PhysicalInventoryProvider {
    boolean supports(Inventory inventory);
    String scopeId(Inventory inventory);
    String location(Inventory inventory);
}
