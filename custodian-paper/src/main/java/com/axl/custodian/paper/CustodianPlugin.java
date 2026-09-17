package com.axl.custodian.paper;

import com.axl.custodian.core.sqlite.SqliteCustodianStore;
import com.axl.custodian.core.PresenceService;
import com.axl.custodian.core.IdentityService;
import com.axl.custodian.api.CustodianApi;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import java.nio.file.Files;

/** Bootstrap only; Paper observation is intentionally deferred to the next slice. */
public final class CustodianPlugin extends JavaPlugin {
    private SqliteCustodianStore store;
    private PaperObservationAdapter observation;
    private IdentityService api;
    private PaperShadowContributor shadow;
    @Override public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            store = new SqliteCustodianStore(getDataFolder().toPath().resolve("custodian.db"));
            saveDefaultConfig();
            var presence = new PresenceService(store, java.time.Clock.systemUTC(), java.time.Duration.ofSeconds(getConfig().getLong("observation.freshness-seconds", 30)));
            api = new IdentityService(store, java.time.Clock.systemUTC(), presence);
            String serverId = getConfig().getString("server-id", "local");
            shadow = new PaperShadowContributor(store, presence, java.time.Clock.systemUTC(), serverId);
            observation = new PaperObservationAdapter(this, presence, com.axl.custodian.api.AuthorityHandle.issuedByHost("custodian-native"),
                    serverId, java.time.Duration.ofSeconds(getConfig().getLong("observation.freshness-seconds", 30)), java.time.Clock.systemUTC());
            getServer().getPluginManager().registerEvents(observation, this);
            PaperServiceRegistry.register(getServer().getServicesManager(), this, api, shadow);
        } catch (Exception failure) {
            getLogger().severe("Custodian could not establish its SQLite authority: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() { PaperServiceRegistry.unregister(getServer().getServicesManager(), this); if(shadow!=null)shadow.shutdown(); if (observation != null) observation.stop(); if (store != null) store.close(); }
    /** Registers a virtual custody provider. Providers are automatically removed when their owner disables. */
    public void registerPhysicalInventoryProvider(Plugin owner, PhysicalInventoryProvider provider) {
        if (observation == null) throw new IllegalStateException("Custodian is not enabled");
        observation.register(java.util.Objects.requireNonNull(owner), java.util.Objects.requireNonNull(provider));
    }
}
