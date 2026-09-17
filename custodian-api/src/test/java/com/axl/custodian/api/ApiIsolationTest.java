package com.axl.custodian.api;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiIsolationTest {
    @Test void adoptionAndValidationExposeNoPaperOrMutableItemTypes() {
        assertNoBukkitTypes(CustodianApi.class);
        assertNoBukkitTypes(ShadowContributor.class);
    }
    private static void assertNoBukkitTypes(Class<?> contract) {
        for (Method method : contract.getDeclaredMethods()) {
            assertFalse(method.getReturnType().getName().startsWith("org.bukkit."));
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().startsWith("org.bukkit."));
            }
        }
    }
}
