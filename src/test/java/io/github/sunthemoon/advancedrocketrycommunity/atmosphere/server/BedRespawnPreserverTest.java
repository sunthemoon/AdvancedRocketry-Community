package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.util.Either;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BedRespawnPreserverTest {
    @BeforeAll
    static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void nativeDescriptorsAreUniqueWithoutMappedMethodNames() {
        for (MethodType type : new MethodType[] {
                MethodType.methodType(Either.class, BlockPos.class),
                MethodType.methodType(void.class, ResourceKey.class, BlockPos.class, float.class, boolean.class,
                        boolean.class)}) {
            assertEquals(1, Arrays.stream(ServerPlayer.class.getDeclaredMethods()).filter(method ->
                    MethodType.methodType(method.getReturnType(), method.getParameterTypes()).equals(type)).count());
        }
    }

    @Test
    void exactComparisonIncludesAbsenceDimensionForcedAndRawAngleBits() {
        var absent = new BedRespawnPreserver.Tuple(Level.OVERWORLD, null, 0x80000000, false);
        assertEquals(absent, new BedRespawnPreserver.Tuple(Level.OVERWORLD, null, 0x80000000, false));
        assertNotEquals(absent, new BedRespawnPreserver.Tuple(Level.OVERWORLD, null, 0, false));
        assertNotEquals(absent, new BedRespawnPreserver.Tuple(Level.NETHER, null, 0x80000000, false));
        assertNotEquals(absent, new BedRespawnPreserver.Tuple(Level.OVERWORLD, BlockPos.ZERO, 0x80000000, false));
        assertNotEquals(absent, new BedRespawnPreserver.Tuple(Level.OVERWORLD, null, 0x80000000, true));
        assertNotEquals(new BedRespawnPreserver.Tuple(Level.OVERWORLD, null, 0x7fc00001, false),
                new BedRespawnPreserver.Tuple(Level.OVERWORLD, null, 0x7fc00002, false));
    }
}
