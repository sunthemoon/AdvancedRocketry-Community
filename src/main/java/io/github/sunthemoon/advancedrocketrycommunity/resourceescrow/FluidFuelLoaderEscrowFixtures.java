package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * W95 (B3-E) GameTest fixtures: far areas under their own tickets, programmatic native rockets, real loaders loaded
 * with an operator's bound root, and the resource, envelope and queue probes the tests compare. Gated test code only.
 */
final class FluidFuelLoaderEscrowFixtures {
    static final ResourceLocation ROCKET_FUEL = ModIdentity.id("rocket_fuel");
    static final ResourceLocation HYDROGEN = ModIdentity.id("hydrogen");
    static final ResourceLocation OXYGEN = ModIdentity.id("oxygen");
    static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");
    static final TicketType<UUID> AREA = TicketType.create("arce_w95_loader_area", UUID::compareTo);
    private FluidFuelLoaderEscrowFixtures() { }

    /** One far chunk that only this fixture's ticket makes entity-ticking. */
    static final class Area {
        final ServerLevel level;
        final ChunkPos chunk;
        private final UUID key = UUID.randomUUID();
        private boolean held;
        Area(ServerLevel level, int chunkX, int chunkZ) { this.level = level; chunk = new ChunkPos(chunkX, chunkZ); }
        /** Fixed test setup, not escrow loading: the ticket, and the chunk itself once. */
        void hold() {
            if (!held) { level.getChunkSource().addRegionTicket(AREA, chunk, 2, key); held = true; }
            level.getChunk(chunk.x, chunk.z);
        }
        void release() { if (held) { level.getChunkSource().removeRegionTicket(AREA, chunk, 2, key); held = false; } }
        boolean ticking() {
            return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null && level.areEntitiesLoaded(chunk.toLong())
                    && level.isPositionEntityTicking(at(0, 100, 0));
        }
        BlockPos at(int x, int y, int z) { return new BlockPos(chunk.getMinBlockX() + x, y, chunk.getMinBlockZ() + z); }
    }

    static RocketPropulsion mono(long capacity) {
        return new RocketPropulsion(RocketPropulsion.Mode.MONO, capacity, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
    }
    static RocketPropulsion bipropellant(long fuel, long oxidizer) {
        return new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, fuel, oxidizer, 1, 1, RocketPropulsion.Settings.DEFAULTS);
    }

    /** A native rocket owned by {@code owner}, not yet added to the Level; its two blocks exist only in its snapshot. */
    static RocketEntity rocket(ServerLevel level, BlockPos origin, UUID owner, RocketPropulsion propulsion) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron), new RocketBlock(new RocketPosition(0, 1, 0), iron)),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, propulsion.primaryCapacity(), 1, 1, 1, 0),
                level.getGameTime(), propulsion);
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), owner);
        return rocket;
    }

    static LoaderBinding binding(RocketEntity rocket) {
        var flight = rocket.flightData().orElseThrow(); var origin = flight.currentOrigin();
        return new LoaderBinding(flight.logicalRocketId(), rocket.level().dimension().location(), rocket.getUUID(),
                new BlockPos(origin.x(), origin.y(), origin.z()), null);
    }

    static LoaderTank tank(ResourceLocation fluid, int amount) { return LoaderTank.of(fluid, null, amount); }

    /** A real loader holding an operator-loaded root: a fresh device, this binding, this tank, a pristine section. */
    static FluidFuelLoaderBlockEntity loader(ServerLevel level, BlockPos at, UUID owner, LoaderTank tank, LoaderBinding binding) {
        return loader(level, at, new LoaderRoot(owner, UUID.randomUUID(), binding, tank, LoaderEscrowSection.neutral()).encode());
    }
    static FluidFuelLoaderBlockEntity loader(ServerLevel level, BlockPos at, CompoundTag root) {
        level.setBlockAndUpdate(at, ModBlocks.FLUID_FUEL_LOADER.get().defaultBlockState());
        var loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
        var outer = new CompoundTag(); outer.put(LoaderRoot.KEY, root); Objects.requireNonNull(loader).load(outer);
        return loader;
    }

    /** Removes a loader without its removal callbacks, so cleanup logs nothing; a protected root is fixture data here. */
    static void clear(ServerLevel level, BlockPos at) {
        level.removeBlockEntity(at); level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    static UUID device(FluidFuelLoaderBlockEntity loader) { return loader.root().orElseThrow().device(); }
    static int amount(FluidFuelLoaderBlockEntity loader) { return loader.root().orElseThrow().tank().amount(); }
    static LoaderEscrowSection section(FluidFuelLoaderBlockEntity loader) { return loader.root().orElseThrow().escrow(); }
    static long bank(RocketEntity rocket, boolean oxidizer) {
        var propulsion = rocket.flightData().orElseThrow().fuel().propulsion().orElseThrow();
        return (oxidizer ? propulsion.oxidizer() : propulsion.primary()).amount();
    }

    /** The coordinator queue, the authority's counts and the fair queue, as one comparable value; no ledger is created. */
    record Counts(UUID ledger, long next, Set<UUID> unresolved, int pending, long forRocket, boolean writerIdle,
                  int waiting, boolean preparing) { }
    static Counts counts(MinecraftServer server, UUID logical) {
        var runtime = EscrowRuntime.of(server); var fills = runtime.fills(server);
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) {
            var authority = runtime.propulsion(server).authority();
            return new Counts(null, 1, Set.of(), authority.pendingCount(), authority.pendingFor(logical), true, fills.size(), fills.preparing());
        }
        var coordinator = data.bind(server); var authority = coordinator.admissionAuthority();
        return new Counts(data.ledger().id(), coordinator.nextSequence(), coordinator.unresolvedIds(), authority.pendingCount(),
                authority.pendingFor(logical), coordinator.writerIdle(), fills.size(), fills.preparing());
    }

    /**
     * Fuel debited from a loader but not yet credited to the rocket: the device holds its operation's SOURCE marker and
     * the rocket lacks the DESTINATION marker. Escrow holds exactly this amount, never more and never twice.
     */
    static long held(MinecraftServer server, RocketEntity rocket, List<FluidFuelLoaderBlockEntity> loaders) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return 0; }
        var coordinator = data.bind(server); long held = 0;
        for (var loader : loaders) {
            for (var op : coordinator.unresolvedNaming(device(loader))) {
                boolean debited = section(loader).markers().stream().anyMatch(marker -> marker.operation().equals(op.id()));
                boolean credited = rocket.escrowSection().markers().stream().anyMatch(marker -> marker.operation().equals(op.id()));
                if (debited && !credited) { held += op.payload().getInt("Amount"); }
            }
        }
        return held;
    }
}
