package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * W131 gated A1 scaffolding for real fluid cargo: far cells under this fixture's own tickets, an isolated first ledger
 * (W124's pattern) restored afterwards, rockets whose snapshot holds one pressurized tank, and bound cargo devices.
 */
final class EscrowCargoGameTestWorld implements AutoCloseable {
    private static final TicketType<UUID> AREA = TicketType.create("arce_w131_cargo_area", UUID::compareTo);
    final GameTestHelper helper;
    final ServerLevel level;
    final MinecraftServer server;
    final List<RocketFluidCargoBlockEntity> devices = new ArrayList<>();
    final List<io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity> itemDevices = new ArrayList<>();
    final List<RocketEntity> rockets = new ArrayList<>();
    final List<UUID> transfers = new ArrayList<>();
    final List<ServerPlayer> players = new ArrayList<>();
    private final UUID key = UUID.randomUUID();
    private final List<ChunkPos> chunks;
    private final Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
    private ResourceEscrowSavedData original;
    private Path file, backup;
    private boolean detached, aside, closed;

    EscrowCargoGameTestWorld(GameTestHelper helper, List<BlockPos> cells) {
        this.helper = helper; level = helper.getLevel(); server = level.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        chunks = cells.stream().map(ChunkPos::new).distinct().toList();
        for (var chunk : chunks) { level.getChunkSource().addRegionTicket(AREA, chunk, 2, key); level.getChunk(chunk.x, chunk.z); }
    }

    /** Every cell entity-ticking, a checked journal and no admission work held by the shared delegate. */
    boolean ready() {
        return chunks.stream().allMatch(chunk -> level.areEntitiesLoaded(chunk.toLong())
                && level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX(), 100, chunk.getMinBlockZ())))
                && RocketTransferSavedData.get(server).retryChecked(server) && !EscrowRuntime.of(server).propulsion(server).holdsWork();
    }

    /** Detach the idle world ledger and set its file aside; the first cargo admission then creates its own. */
    void isolate() {
        original = ResourceEscrowSavedData.existing(server);
        helper.assertTrue(EscrowRuntime.of(server).replaceLedgerForTests(server, null), "Cannot detach the idle ledger");
        detached = true;
        file = ResourceEscrowSavedData.file(server); backup = file.resolveSibling(file.getFileName() + ".w131-backup");
        try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } aside = true; }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    ResourceEscrowSavedData data() { return ResourceEscrowSavedData.existing(server); }

    void clear(BlockPos at) {
        helper.assertTrue(level.getBlockEntity(at) == null || terrain.containsKey(at), "Cargo fixture overlaps a block entity");
        terrain.putIfAbsent(at, level.getBlockState(at));
        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** A stationary rocket at {@code at}: one pressurized tank holding {@code cargo}, a seat, and 1,000 mB of rocket fuel. */
    RocketEntity rocket(BlockPos at, FluidStack cargo) {
        clear(at); clear(at.above());
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(at.getX(), at.getY(), at.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                                PressurizedTankRocketAdapter.payload(cargo)),
                        new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 1), level.getGameTime(),
                new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS));
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000), level.getGameTime()));
        helper.assertTrue(level.addFreshEntity(rocket), "Cargo rocket fixture was not published");
        rockets.add(rocket); return rocket;
    }

    /** A real device holding an operator-loaded root bound to {@code rocket}. */
    RocketFluidCargoBlockEntity device(BlockPos at, RocketEntity rocket, RocketCargoRole role, int amount) {
        clear(at);
        level.setBlockAndUpdate(at, (role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_FLUID_LOADER : ModBlocks.ROCKET_FLUID_UNLOADER).get().defaultBlockState());
        var device = (RocketFluidCargoBlockEntity) Objects.requireNonNull(level.getBlockEntity(at));
        var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), role,
                amount == 0 ? LoaderTank.EMPTY : LoaderTank.of(new FluidStack(Fluids.WATER, amount)))
                .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
        var tag = new CompoundTag(); tag.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(tag);
        devices.add(device); return device;
    }

    static UUID id(RocketFluidCargoBlockEntity device) { return device.root().orElseThrow().device(); }
    static int tank(RocketFluidCargoBlockEntity device) { return device.root().orElseThrow().tank().amount(); }
    static RocketEscrowSection section(RocketFluidCargoBlockEntity device) { return device.root().orElseThrow().escrow(); }
    /** The cargo held by the rocket's one tank payload. */
    static int cargo(RocketEntity rocket) {
        var payload = rocket.snapshot().orElseThrow().blocks().get(0).blockEntityPayload().orElseThrow();
        return PressurizedTankRocketAdapter.fluid(payload.data()).getAmount();
    }

    void enable(RocketFluidCargoBlockEntity device, boolean enabled) {
        var root = device.root().orElseThrow();
        EscrowCargoSwitches.forTests(server, root.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.FLUID_CARGO_LOAD
                : EscrowOperation.Kind.FLUID_CARGO_UNLOAD, root.device(), enabled);
    }
    void pause(RocketFluidCargoBlockEntity device, boolean paused) { EscrowCargoSwitches.pauseReconciliationForTests(server, id(device), paused); }

    /** The one record that names this device, in the isolated ledger. */
    EscrowOperation member(RocketFluidCargoBlockEntity device) {
        var data = data(); if (data == null) { return null; }
        return data.ledger().records().stream().filter(op -> op.device().identity().equals(id(device))).findFirst().orElse(null);
    }

    /** Item counterpart, sharing the same isolated ledger, tickets and teardown. */
    RocketEntity itemRocket(BlockPos at, int count) {
        clear(at); clear(at.above());
        var entries = new net.minecraft.nbt.ListTag();
        if (count > 0) { var stack = ItemCargoReconciliationFixtures.stack(count); stack.putByte("Slot", (byte) 0); entries.add(stack); }
        var base = ItemCargoReconciliationFixtures.snapshot(level, at, entries);
        var snapshot = RocketStructureSnapshot.create(base.snapshotId(), base.sourceDimension(), base.sourceOrigin(),
                base.blocks(), base.passengerAnchors(), base.stats(), level.getGameTime(),
                new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS));
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000), level.getGameTime()));
        helper.assertTrue(level.addFreshEntity(rocket), "Item cargo rocket fixture was not published");
        rockets.add(rocket); return rocket;
    }

    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity itemDevice(
            BlockPos at, RocketEntity rocket, RocketCargoRole role, int count) {
        clear(at);
        level.setBlockAndUpdate(at, (role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_ITEM_LOADER : ModBlocks.ROCKET_ITEM_UNLOADER).get().defaultBlockState());
        var device = (io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity) Objects.requireNonNull(level.getBlockEntity(at));
        var root = io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot.placed(
                rocket.ownerId().orElseThrow(), UUID.randomUUID(), role).withBuffer(new EscrowItemBuffer(List.of(
                        count == 0 ? new CompoundTag() : ItemCargoReconciliationFixtures.stack(count),
                        new CompoundTag(), new CompoundTag(), new CompoundTag()))).withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
        var tag = new CompoundTag(); tag.put(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot.KEY, root.encode());
        device.load(tag); itemDevices.add(device); return device;
    }
    static UUID id(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device) { return device.root().orElseThrow().device(); }
    static RocketEscrowSection section(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device) { return device.root().orElseThrow().escrow(); }
    void enable(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device, boolean enabled) {
        var root = device.root().orElseThrow();
        EscrowCargoSwitches.forTests(server, root.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.ITEM_CARGO_LOAD
                : EscrowOperation.Kind.ITEM_CARGO_UNLOAD, root.device(), enabled);
    }
    void pause(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device, boolean paused) {
        EscrowCargoSwitches.pauseReconciliationForTests(server, id(device), paused);
    }
    EscrowOperation member(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity device) {
        var data = data(); if (data == null) { return null; }
        return data.ledger().records().stream().filter(op -> op.device().identity().equals(id(device))).findFirst().orElse(null);
    }

    @Override public void close() {
        if (closed) { return; }
        closed = true;
        var runtime = EscrowRuntime.of(server);
        for (var device : itemDevices) {
            if (device.root().isPresent()) { enable(device, false); pause(device, false); }
            level.removeBlockEntity(device.getBlockPos()); level.setBlock(device.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (var device : devices) {
            if (device.root().isPresent()) { enable(device, false); pause(device, false); }
            // No removal callbacks, so cleanup of a protected fixture root logs nothing.
            level.removeBlockEntity(device.getBlockPos()); level.setBlock(device.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (var player : players) { player.stopRiding(); server.getPlayerList().remove(player); }
        rockets.forEach(rocket -> { if (!rocket.isRemoved()) { rocket.discard(); } });
        var journal = RocketTransferSavedData.get(server); boolean removed = false;
        for (var transfer : transfers) { if (journal.find(transfer).isPresent()) { journal.remove(transfer); removed = true; } }
        if (removed) { RocketJournalGameTestSupport.acknowledgeFixture(helper); }
        // A materialized tank is fixture output here; drop its block entity first so restoring the cell spawns nothing.
        terrain.forEach((pos, state) -> { level.removeBlockEntity(pos); level.setBlock(pos, state, Block.UPDATE_ALL); });
        chunks.forEach(chunk -> level.getChunkSource().removeRegionTicket(AREA, chunk, 2, key));
        var isolated = detached ? ResourceEscrowSavedData.existing(server) : null;
        if (isolated != null && isolated != original) { isolated.stop(); }
        if (aside) {
            try { Files.deleteIfExists(file); if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); } }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        if (detached && !runtime.replaceLedgerForTests(server, original)) { throw new IllegalStateException("W131 ledger restore refused"); }
    }
}
