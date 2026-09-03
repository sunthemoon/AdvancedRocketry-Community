package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ElectrolyzerRecipeContractTest {
    @Test
    void machineRecipeOverridesTheVanillaRecipeBookContract() throws ReflectiveOperationException {
        Method method = ElectrolyzerRecipe.class.getMethod("isSpecial");

        assertEquals(ElectrolyzerRecipe.class, method.getDeclaringClass());
        assertEquals(boolean.class, method.getReturnType());
    }
}
