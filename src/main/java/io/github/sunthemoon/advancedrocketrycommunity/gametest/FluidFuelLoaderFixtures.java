package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderCarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Shared W82 fixtures: loaders placed by players or loaded with an operator's root, rockets, drops and ports. */
final class FluidFuelLoaderFixtures {
    static final String BATCH = "fluid_fuel_loader";
    static final String TEMPLATE = "rocket_test";
    static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");

    private FluidFuelLoaderFixtures() {
    }

    /** A bare loader block: no root, unclaimed, as {@code /setblock} leaves it. */
    static FluidFuelLoaderBlockEntity place(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ModBlocks.FLUID_FUEL_LOADER.get());
        return (FluidFuelLoaderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
    }

    /** A loader that holds {@code root}, loaded as an operator's NBT would load it. */
    static FluidFuelLoaderBlockEntity place(GameTestHelper helper, BlockPos relative, Tag root) {
        FluidFuelLoaderBlockEntity loader = place(helper, relative);
        loader.load(outer(root));
        return loader;
    }

    static CompoundTag outer(Tag root) {
        CompoundTag outer = new CompoundTag();
        outer.put(LoaderRoot.KEY, root);
        return outer;
    }

    /** An owned root with a fresh device, no binding and a pristine section. */
    static CompoundTag owned(UUID owner, LoaderTank tank) {
        return LoaderRoot.placed(owner, UUID.randomUUID(), tank).encode();
    }

    /** An owned root whose section holds one SOURCE marker: escrow holds this device. */
    static CompoundTag locked(UUID owner, LoaderTank tank) {
        var section = new LoaderEscrowSection(UUID.randomUUID(), 1L,
                List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1L, LoaderEscrowSection.ROLE, 1L)));
        return new LoaderRoot(owner, UUID.randomUUID(), null, tank, section).encode();
    }

    /** A real placement through the player's game mode, as a client's use-item-on packet does it. */
    static InteractionResult placeBy(GameTestHelper helper, ServerPlayer placer, BlockPos absolute, ItemStack stack) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        placer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = placer.gameMode.useItemOn(placer, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
        placer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result;
    }

    static ItemStack carrier(UUID owner, LoaderTank tank) {
        ItemStack stack = new ItemStack(ModItems.FLUID_FUEL_LOADER.get());
        stack.getOrCreateTag().put(LoaderCarrier.KEY, new LoaderCarrier(owner, tank).encode());
        return stack;
    }

    static IFluidHandler port(FluidFuelLoaderBlockEntity loader) {
        return loader.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElseThrow();
    }

    /** Removes a loader without its removal callbacks, so fixture cleanup logs nothing. */
    static void clear(ServerLevel level, BlockPos absolute) {
        level.removeBlockEntity(absolute);
        level.setBlock(absolute, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    static List<ItemEntity> drops(ServerLevel level, BlockPos absolute) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(3.0D));
    }

    static void discardDrops(ServerLevel level, BlockPos absolute) {
        drops(level, absolute).forEach(ItemEntity::discard);
    }

    /**
     * A real rocket built from scanned blocks (motor, seat, guidance computer and two fuel tanks) at {@code origin}, the
     * scanned blocks removed again so they cannot touch a loader, and the entity owned by {@code owner}.
     */
    static RocketEntity rocket(GameTestHelper helper, BlockPos origin, UUID owner) {
        List<BlockPos> structure = List.of(origin, origin.above(), origin.above(2), origin.east(), origin.west());
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        helper.setBlock(origin.east(), ModBlocks.ROCKET_FUEL_TANK.get());
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        BlockPos at = helper.absolutePos(origin);
        var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(helper.getLevel(),
                RocketBlockEntityAdapters.defaults()), helper.getLevel().dimension().location(),
                new RocketPosition(at.getX(), at.getY(), at.getZ()), UUID.randomUUID(), helper.getLevel().getGameTime());
        var snapshot = task.step(256).snapshot().orElseThrow();
        structure.forEach(relative -> helper.setBlock(relative, Blocks.AIR));
        RocketEntity rocket = ModEntities.ROCKET.get().create(helper.getLevel());
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Loader fixture rocket admission failed");
        return rocket;
    }

    /** A second loaded rocket entity with the same logical identity, as a duplicated entity would be. */
    static RocketEntity duplicate(GameTestHelper helper, RocketEntity original) {
        RocketEntity copy = ModEntities.ROCKET.get().create(helper.getLevel());
        copy.initializeTransferred(original.snapshot().orElseThrow(), original.assemblyTransactionId().orElseThrow(),
                original.ownerId().orElseThrow(), original.flightData().orElseThrow());
        helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Duplicate rocket admission failed");
        return copy;
    }
}
