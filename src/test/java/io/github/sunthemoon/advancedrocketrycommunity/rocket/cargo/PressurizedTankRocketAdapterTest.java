package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.RocketAdapterRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PressurizedTankRocketAdapterTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void defaultsAndFreezeSupportOnlyCanonicalSchemaOnePayloads() {
        var adapter = new PressurizedTankRocketAdapter();
        var valid = PressurizedTankRocketAdapter.payload(new FluidStack(Fluids.WATER, 749));
        assertEquals(Set.of("tank"), valid.data().getAllKeys());
        assertEquals(Set.of("schema", "fluid"), valid.data().getCompound("tank").getAllKeys());
        assertTrue(RocketBlockEntityAdapters.defaults().supportsPayload(valid));
        assertTrue(new RocketAdapterRegistry(id -> true).freeze().supportsPayload(valid));
        for (String field : new String[] {"schema", "fluid"}) {
            var bad = valid.data(); bad.getCompound("tank").remove(field);
            assertFalse(adapter.canRestorePayload(new RocketBlockEntityPayload(adapter.id(), bad)));
        }
        var future = valid.data(); future.getCompound("tank").putInt("schema", 2);
        assertFalse(adapter.canRestorePayload(new RocketBlockEntityPayload(adapter.id(), future)));
        var unknown = valid.data(); unknown.getCompound("tank").getCompound("fluid").putString("FluidName", "missing:fluid");
        assertFalse(adapter.canRestorePayload(new RocketBlockEntityPayload(adapter.id(), unknown)));
        var extra = valid.data(); extra.putInt("capacity", 64000);
        assertFalse(adapter.canRestorePayload(new RocketBlockEntityPayload(adapter.id(), extra)));
    }
    @Test void externalProvidersCannotClaimBuiltInIdOrType() {
        var callback = new io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter() {
            public boolean canMove(BlockEntity entity) { throw new AssertionError("No callbacks during registration"); }
            public CompoundTag capture(BlockEntity entity) { throw new AssertionError(); }
            public boolean restore(BlockEntity entity, CompoundTag data) { throw new AssertionError(); }
        };
        var registry = new RocketAdapterRegistry(id -> true);
        var own = registry.forOwner("advancedrocketrycommunity");
        assertThrows(IllegalArgumentException.class, () -> own.register(PressurizedTankRocketAdapter.ID,
                Set.of(ResourceLocation.tryParse("fixture:host")), 1, callback));
        var external = registry.forOwner("fixture");
        assertThrows(IllegalArgumentException.class, () -> external.register(ResourceLocation.tryParse("fixture:adapter"),
                Set.of(PressurizedTankRocketAdapter.TYPE), 1, callback));
    }
}
