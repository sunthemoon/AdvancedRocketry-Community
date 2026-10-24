package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.RocketAdapterRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Gated fixture setup only; publication still uses the installed production coordinator and provider wrapper. */
public final class SealedProductionTestAccess {
    public static BlockEntity stage(ServerLevel level, BlockEntity source, ResourceLocation adapterId,
            RocketBlockEntityAdapter provider) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        source.setLevel(level);
        var registry = new RocketAdapterRegistry(ForgeRegistries.BLOCK_ENTITY_TYPES::containsKey);
        registry.forOwner(adapterId.getNamespace()).register(adapterId,
                Set.of(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(source.getType())), 1, provider);
        var payload = registry.freeze().capture(source).optionalPayload().orElseThrow();
        var pos = source.getBlockPos();
        var member = new SealedRocketMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "0".repeat(64),
                level.dimension().location(), new RocketPosition(pos.getX(), pos.getY(), pos.getZ()), 0,
                RocketBlockStateAdapter.capture(source.getBlockState()), payload);
        if (source instanceof Container container) { container.clearContent(); }
        source.setRemoved();
        var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState(); level.setBlock(pos, state, 2);
        var carrier = new SealedRocketBlockEntity(pos, state); carrier.initialize(SealedRocketRoot.stage(member));
        level.setBlockEntity(carrier); carrier.setChanged();
        RocketTransactionSavedData.get(level.getServer()).checkpoint(level.getServer());
        return carrier;
    }
    public static boolean unseal(BlockEntity carrier) {
        var level = (ServerLevel) carrier.getLevel(); EscrowTestEndpoints.requireEnabled(level.getServer());
        return RocketRuntime.unsealForReleaseTest(level, (SealedRocketBlockEntity) carrier);
    }
    private SealedProductionTestAccess() { }
}
