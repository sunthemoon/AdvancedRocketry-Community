package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.RegistryObject;

/** Real admission/eligibility code on unstarted native objects; only world lookup and capabilities are stubbed. */
final class EscrowTurnYieldFixture implements AutoCloseable {
    static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    final MinecraftServer server = UnstartedServer.allocate(GameTestServer.class);
    final TestLevel level = UnstartedServer.allocate(TestLevel.class);
    final EscrowRuntime runtime = new EscrowRuntime();
    final ResourceEscrowSavedData data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(UUID.randomUUID())));
    final EscrowLedger before = data.ledger();
    final EscrowLedgerWriter writer;
    final EscrowCoordinator coordinator;
    final RocketTransferSavedData journal = new RocketTransferSavedData();
    final RocketEntity rocket = UnstartedServer.allocate(RocketEntity.class);
    final UUID logical = UUID.randomUUID(), owner = UUID.randomUUID();
    final EscrowDeviceTurns active = new EscrowDeviceTurns();
    final List<EscrowFluidCargoAdmission> admissions = new ArrayList<>();
    final List<EscrowDeviceTurns> registrations = new ArrayList<>();
    final Object previousManager = UnstartedServer.get(RocketRuntime.class, null, "service");
    final Block previousFuelBlock = UnstartedServer.get(RegistryObject.class, ModBlocks.FLUID_FUEL_LOADER, "value");

    EscrowTurnYieldFixture(Path directory) throws Exception {
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        level.server = server; level.storage = TestDataStorage.over(directory);
        level.capabilities = new IdentityHashMap<>();
        level.capabilities.put(EscrowRuntime.CAPABILITY, LazyOptional.of(() -> runtime));
        level.capabilities.put(RocketFluidCargoIndex.CAPABILITY, LazyOptional.of(RocketFluidCargoIndex.Devices::new));
        level.capabilities.put(FluidFuelLoaderIndex.CAPABILITY, LazyOptional.of(FluidFuelLoaderIndex.Devices::new));
        level.border = new WorldBorder(); level.rocket = rocket;
        level.chunks = UnstartedServer.allocate(TestChunks.class);
        level.chunks.chunk = UnstartedServer.allocate(TestChunk.class);
        level.chunks.chunk.entities = new HashMap<>(); level.chunks.chunk.states = new HashMap<>();
        writer = new EscrowLedgerWriter(directory.resolve("escrow"), 0);
        coordinator = new EscrowCoordinator(data, writer); data.attach(coordinator, () -> true);
        level.storage.set(ManagedSavedDataType.RESOURCE_ESCROW.dataName(), data);
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        level.storage.set(ManagedSavedDataType.ROCKET_TRANSFERS.dataName(), journal);
        var snapshot = EscrowCargoPreparationGameTests.snapshot(Level.OVERWORLD.location(), ORIGIN, false,
                List.of(new FluidStack(Fluids.WATER, 1000)));
        var flight = RocketFlightData.initial(logical, 2000, 0,
                new net.minecraft.resources.ResourceLocation("advancedrocketrycommunity", "earth"),
                Level.OVERWORLD.location(), new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(0, 64, 0), 0);
        flight = flight.withFuel(RocketFuelState.empty(snapshot.propulsion().orElseThrow()), 0);
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", snapshot);
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", logical);
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", owner);
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", flight);
        UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", RocketEscrowSection.neutral());
        UnstartedServer.set(Entity.class, rocket, "uuid", UUID.randomUUID());
        UnstartedServer.set(Entity.class, rocket, "level", level);
        UnstartedServer.set(Entity.class, rocket, "passengers", ImmutableList.of());
        UnstartedServer.set(Entity.class, rocket, "position", new Vec3(.5, 64, .5));
        UnstartedServer.set(Entity.class, rocket, "blockPosition", ORIGIN);
        UnstartedServer.set(Entity.class, rocket, "chunkPosition", new ChunkPos(0, 0));
        UnstartedServer.set(Entity.class, rocket, "bb", new AABB(ORIGIN));
        UnstartedServer.set(Entity.class, rocket, "deltaMovement", Vec3.ZERO);
        UnstartedServer.set(RegistryObject.class, ModBlocks.FLUID_FUEL_LOADER, "value", Blocks.STONE);
        RocketRuntime.install(new RocketManager());
        assertTrue(active.bind(coordinator, UUID.randomUUID(), logical));
    }

    LoaderBinding binding() { return new LoaderBinding(logical, Level.OVERWORLD.location(), rocket.getUUID(), ORIGIN, null); }
    RocketFluidCargoBlockEntity cargo(RocketCargoRole role, int amount) {
        var device = UnstartedServer.allocate(RocketFluidCargoBlockEntity.class);
        var turns = new EscrowDeviceTurns(); registrations.add(turns);
        var root = RocketFluidCargoRoot.placed(owner, UUID.randomUUID(), role, water(amount)).withBinding(binding());
        UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "role", role);
        UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "root", root);
        UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "status", RocketFluidCargoRoot.Status.VALID);
        UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "turns", turns);
        var admission = new EscrowFluidCargoAdmission(device); admissions.add(admission);
        UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "admission", admission);
        place(device, new RocketFluidCargoBlock(role, BlockBehaviour.Properties.of()).defaultBlockState());
        assertTrue(device.refreshTurns());
        EscrowCargoSwitches.forTests(server, admission.kind(), root.device(), true);
        return device;
    }
    FluidFuelLoaderBlockEntity fuel(int amount) {
        var device = UnstartedServer.allocate(FluidFuelLoaderBlockEntity.class);
        var turns = new EscrowDeviceTurns(); registrations.add(turns);
        var root = new LoaderRoot(owner, UUID.randomUUID(), binding(), water(amount), LoaderEscrowSection.neutral());
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "root", root);
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "status", LoaderRoot.Status.VALID);
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "turns", turns);
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "progressCheckedAt", Long.MIN_VALUE);
        place(device, Blocks.STONE.defaultBlockState());
        assertTrue(turns.bind(coordinator, root.device(), logical));
        runtime.loaderAdmissionForTests(server, root.device(), true);
        return device;
    }
    private void place(BlockEntity device, BlockState state) {
        var pos = ORIGIN.offset(level.chunks.chunk.entities.size() + 1, 0, 0);
        UnstartedServer.set(BlockEntity.class, device, "worldPosition", pos);
        UnstartedServer.set(BlockEntity.class, device, "blockState", state);
        UnstartedServer.set(BlockEntity.class, device, "level", level);
        level.chunks.chunk.entities.put(pos, device); level.chunks.chunk.states.put(pos, state);
    }
    void tank(RocketFluidCargoBlockEntity device, int amount) {
        UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "root", device.root().orElseThrow().withTank(water(amount)));
    }
    void occupied(boolean value) {
        UnstartedServer.set(Entity.class, rocket, "passengers", value ? ImmutableList.of(UnstartedServer.allocate(RocketEntity.class)) : ImmutableList.of());
    }
    void unchanged() {
        assertSame(before, data.ledger()); assertFalse(data.isDirty()); assertTrue(writer.idle());
        assertEquals(1, coordinator.nextSequence()); assertTrue(coordinator.pendingAdmissions().isEmpty());
        assertFalse(runtime.cargoPreparing(logical)); assertFalse(coordinator.logicalLocked(logical));
    }
    static LoaderTank water(int amount) { return amount == 0 ? LoaderTank.EMPTY : LoaderTank.of(new FluidStack(Fluids.WATER, amount)); }
    @Override public void close() {
        admissions.forEach(EscrowFluidCargoAdmission::close); runtime.fills(server).clear();
        registrations.forEach(EscrowDeviceTurns::close); active.close(); coordinator.close(); writer.close(); journal.stop();
        level.capabilities.values().forEach(LazyOptional::invalidate);
        UnstartedServer.set(RocketRuntime.class, null, "service", previousManager);
        UnstartedServer.set(RegistryObject.class, ModBlocks.FLUID_FUEL_LOADER, "value", previousFuelBlock);
    }

    private static final class TestLevel extends ServerLevel {
        MinecraftServer server; DimensionDataStorage storage; TestChunks chunks; WorldBorder border; RocketEntity rocket;
        Map<Capability<?>, LazyOptional<?>> capabilities;
        TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public MinecraftServer getServer() { return server; }
        @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public ServerChunkCache getChunkSource() { return chunks; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public boolean isInWorldBounds(BlockPos pos) { return pos.getY() >= -64 && pos.getY() < 320; }
        @Override public boolean areEntitiesLoaded(long chunk) { return chunk == 0; }
        @Override public Entity getEntity(UUID id) { return rocket.getUUID().equals(id) ? rocket : null; }
        @Override public long getGameTime() { return 1; }
        @Override public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB area, Predicate<? super T> predicate) {
            return type.isInstance(rocket) && predicate.test(type.cast(rocket)) ? List.of(type.cast(rocket)) : List.of();
        }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capabilities.getOrDefault(capability, LazyOptional.empty()).cast();
        }
    }
    private static final class TestChunks extends ServerChunkCache {
        TestChunk chunk;
        TestChunks() { super(null, null, null, null, null, null, 0, 0, false, null, null, null); }
        @Override public LevelChunk getChunkNow(int x, int z) { return x == 0 && z == 0 ? chunk : null; }
    }
    private static final class TestChunk extends LevelChunk {
        Map<BlockPos, BlockEntity> entities; Map<BlockPos, BlockState> states;
        TestChunk() { super(null, new ChunkPos(0, 0)); }
        @Override public Map<BlockPos, BlockEntity> getBlockEntities() { return entities; }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    }
}
