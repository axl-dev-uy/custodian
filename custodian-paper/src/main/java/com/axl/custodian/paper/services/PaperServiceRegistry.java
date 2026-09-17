package com.axl.custodian.paper.services;

import com.axl.custodian.api.CustodianApi;
import com.axl.custodian.api.ShadowContributor;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/** Registers the intentionally small public Custodian service surface with Paper. */
public final class PaperServiceRegistry {
    private PaperServiceRegistry() { }

    public static void register(
            ServicesManager services, Plugin owner, CustodianApi api, ShadowContributor shadowContributor) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(owner, "owner");
        services.register(CustodianApi.class, Objects.requireNonNull(api, "api"), owner, ServicePriority.Normal);
        services.register(ShadowContributor.class, Objects.requireNonNull(shadowContributor, "shadowContributor"),
                owner, ServicePriority.Normal);
    }

    public static void unregister(ServicesManager services, Plugin owner) {
        Objects.requireNonNull(services, "services").unregisterAll(Objects.requireNonNull(owner, "owner"));
    }
}
