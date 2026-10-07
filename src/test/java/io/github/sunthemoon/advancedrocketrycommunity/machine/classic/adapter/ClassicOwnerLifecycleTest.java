package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Modifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicOwnerLifecycleTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void engineRetirementInvalidatesLifetimeEvenWhileExclusiveGuardIsHeld() {
        var state = ClassicRawPermitTest.state(); Object life = state.lifetime(); Object token = state.acquire();
        state.retire(); assertNotSame(life, state.lifetime()); assertTrue(state.heldBy(token)); assertFalse(state.available());
        state.release(token); assertFalse(state.busy());
    }

    @Test void ownCutFacadeRetirementDoesNotRefreshEngineLifetime() {
        var state = ClassicRawPermitTest.state(); Object life = state.lifetime(), facade = state.facadeEpoch();
        state.retireFacades(); assertSame(life, state.lifetime()); assertNotSame(facade, state.facadeEpoch());
    }

    @Test void controllerNativeLifetimeOverridesAreFinalAndHaveNoPublicFrameSetter() throws Exception {
        for (String name : new String[] {"onLoad", "onChunkUnloaded", "setRemoved", "invalidateCaps", "reviveCaps"}) {
            assertTrue(Modifier.isFinal(ClassicControllerBlockEntity.class.getDeclaredMethod(name).getModifiers()));
        }
        assertTrue(Modifier.isFinal(ClassicControllerBlockEntity.class.getDeclaredMethod("load", CompoundTag.class).getModifiers()));
        assertTrue(Modifier.isFinal(ClassicControllerBlockEntity.class.getDeclaredMethod("setLevel", Level.class).getModifiers()));
        for (var method : ClassicControllerBlockEntity.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertFalse(java.util.Arrays.asList(method.getParameterTypes()).contains(ClassicControllerFrame.class));
                assertFalse(java.util.Arrays.asList(method.getParameterTypes()).contains(GuardTicket.class));
            }
        }
        assertTrue(Modifier.isFinal(ClassicHatchBlockEntity.class.getModifiers()));
    }
}
