package com.axl.custodian.api;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiIsolationTest {
    @Test void rootContractsExposeOnlyApiDomainTypes() {
        assertOnlyApiTypes(CustodianApi.class);
        assertOnlyApiTypes(ShadowContributor.class);
    }
    private static void assertOnlyApiTypes(Class<?> contract) {
        for (Method method : contract.getDeclaredMethods()) {
            assertApiType(method.getReturnType());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertApiType(parameter);
            }
        }
    }
    private static void assertApiType(Class<?> type) {
        String name = type.getName();
        assertFalse(name.startsWith("org.bukkit."));
        assertFalse(name.startsWith("com.axl.custodian.core."));
        assertFalse(name.startsWith("com.axl.custodian.paper."));
    }
}
