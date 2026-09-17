package com.axl.custodian.paper.services;

import com.axl.custodian.api.CustodianApi;
import com.axl.custodian.api.ShadowContributor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class PaperServiceRegistryTest {
    @Test
    void registersPublicServicesAndUnregistersEverythingOwnedByPlugin() {
        ServicesManager services = mock(ServicesManager.class);
        Plugin owner = mock(Plugin.class);
        CustodianApi api = mock(CustodianApi.class);
        ShadowContributor shadow = mock(ShadowContributor.class);

        PaperServiceRegistry.register(services, owner, api, shadow);
        PaperServiceRegistry.unregister(services, owner);

        verify(services).register(CustodianApi.class, api, owner, ServicePriority.Normal);
        verify(services).register(ShadowContributor.class, shadow, owner, ServicePriority.Normal);
        verify(services).unregisterAll(same(owner));
    }
}
