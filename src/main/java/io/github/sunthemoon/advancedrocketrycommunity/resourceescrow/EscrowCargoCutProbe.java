package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;

/**
 * W142's gated native cargo cuts: C-S4 settlement ({@code FS_}) and C-S5 creation, flight and disassembly ({@code FF_}).
 * Inert unless the release-test hooks, an escrow scenario and one of these cases are all set. Between the BEFORE fixture
 * save and a cut only production entry points act; a held state is saved once before the operator previews, as an
 * autosave would.
 */
public abstract class EscrowCargoCutProbe {
    static final String CASE = "advancedrocketrycommunity.escrowCase";
    static final String SCENARIO = "advancedrocketrycommunity.escrowScenario";
    static final BlockPos DEVICE = new BlockPos(64, 100, 64), ROCKET = new BlockPos(68, 100, 64), PAD = new BlockPos(80, 100, 64);
    static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(StandardCharsets.US_ASCII));
    private static final AtomicBoolean STOPPED = new AtomicBoolean();
    final MinecraftServer server;
    final ServerLevel level;
    final String name;
    final boolean recovery;
    boolean done;

    EscrowCargoCutProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        name = System.getProperty(CASE, ""); recovery = "recover".equals(System.getProperty(SCENARIO));
        for (var position : List.of(DEVICE, PAD)) {
            var chunk = new ChunkPos(position);
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z);
        }
    }

    static boolean accepts(String name) {
        return name != null && (EscrowCargoSettlementCuts.CASES.containsKey(name)
                || EscrowCargoFlightCuts.CASES.containsKey(name) || EscrowCargoRecoveryCuts.CASES.containsKey(name));
    }
    /** The whole gate: release-test hooks, a start or recover scenario, and an FS_ or FF_ case. */
    static boolean selected(boolean hooks, String scenario, String name) {
        return hooks && ("start".equals(scenario) || "recover".equals(scenario)) && accepts(name);
    }
    /** Returns the existing probe, a new one once the gate is open, or null on every ordinary server. */
    static EscrowCargoCutProbe attach(MinecraftServer server, EscrowCargoCutProbe current) {
        if (current != null || !selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty(SCENARIO), System.getProperty(CASE))) {
            return current;
        }
        String name = System.getProperty(CASE);
        if (name.startsWith("FS_")) { return new EscrowCargoSettlementCuts(server); }
        if (EscrowCargoFlightCuts.CASES.containsKey(name)) { return new EscrowCargoFlightCuts(server); }
        if (EscrowCargoRecoveryCuts.CASES.containsKey(name)) { return new EscrowCargoRecoveryCuts(server); }
        throw new IllegalArgumentException("Unknown W142 cargo cut " + name);
    }

    abstract void checkpoint(String label);
    /** Runs after the barrier tick and before the reconciliation scheduler, on the server thread. */
    void beforeReconciliation() { }
    abstract void tick();
    /** The ordinary reconciliation's store selection; true keeps the production ordering. */
    boolean permitsStore(EscrowOperation op, EscrowEndpoint endpoint) { return true; }

    /**
     * Journal committer seam for the O3J cuts: it returns {@code policy} unchanged unless the gate is open, the scenario is
     * {@code start} and the case is an O3J cut. Its IO closure reads only the staged and target files.
     */
    public static CheckedSavedDataPolicy journalPolicy(MinecraftServer server, CheckedSavedDataPolicy policy) {
        String name = System.getProperty(CASE, "");
        if (server == null || !journalCut(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty(SCENARIO), name)) { return policy; }
        EscrowTestEndpoints.requireEnabled(server);
        return policy.withCommitter((staged, target) -> {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            boolean removal = removal(CheckedSavedDataFile.readPayload(staged, type).orElseThrow(), CheckedSavedDataFile.readPayload(target, type).orElse(null));
            if (removal && name.equals("FS_O3J_BEFORE")) { hardStop(name, "O3J_BEFORE"); }
            policy.committer().commit(staged, target);
            if (removal && name.equals("FS_O3J_AFTER")) { hardStop(name, "O3J_REPLACED"); }
        });
    }
    static boolean journalCut(boolean hooks, String scenario, String name) {
        return hooks && "start".equals(scenario) && ("FS_O3J_BEFORE".equals(name) || "FS_O3J_AFTER".equals(name));
    }
    /** The candidate drops every record that the file on disk holds: the settlement's checked journal removal. */
    static boolean removal(CompoundTag candidate, CompoundTag previous) {
        return previous != null && !previous.getList("transfers", 10).isEmpty() && candidate.getList("transfers", 10).isEmpty();
    }

    boolean stopped() { return STOPPED.get(); }
    void pause(String boundary) { hardStop(name, boundary); }
    /** The runner kills the process during the fixed wait; a late kill fails the run instead of continuing. */
    static void hardStop(String name, String boundary) {
        if (!STOPPED.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_CUT_PAUSED cut={} boundary={}", name, boundary);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); throw new IllegalStateException(stopped); }
        throw new IllegalStateException("W142 cargo forced stop did not arrive within 30 seconds");
    }
    void result(String state, String details) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_CUT_RESULT cut={} state={} {}", name, state, details);
        done = true;
    }

    boolean loaded() {
        return List.of(DEVICE, PAD).stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos));
    }
    void save() {
        EscrowTags.require(server.saveEverything(false, true, true), "W142 cargo fixture save refused");
        level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
    }

    /** A stationary rocket at {@link #ROCKET}: one pressurized tank holding {@code cargo} mB of water, a seat, 1,000 mB of rocket fuel. */
    RocketEntity cargoRocket(int cargo) {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(ROCKET.getX(), ROCKET.getY(), ROCKET.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                                PressurizedTankRocketAdapter.payload(cargo == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, cargo))),
                        new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 1), level.getGameTime(),
                new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS));
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000), level.getGameTime()));
        EscrowTags.require(level.addFreshEntity(rocket), "W142 cargo rocket fixture was not published");
        return rocket;
    }
    /** A real device holding an operator-loaded root bound to {@code rocket}. */
    RocketFluidCargoBlockEntity device(BlockPos at, RocketEntity rocket, RocketCargoRole role, int amount) {
        level.setBlockAndUpdate(at, (role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_FLUID_LOADER : ModBlocks.ROCKET_FLUID_UNLOADER).get().defaultBlockState());
        var device = (RocketFluidCargoBlockEntity) Objects.requireNonNull(level.getBlockEntity(at));
        var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), role,
                amount == 0 ? LoaderTank.EMPTY : LoaderTank.of(new FluidStack(Fluids.WATER, amount))).withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
        var tag = new CompoundTag(); tag.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(tag);
        return device;
    }
    RocketFluidCargoBlockEntity existingDevice(BlockPos at) {
        return level.getBlockEntity(at) instanceof RocketFluidCargoBlockEntity device ? device : null;
    }
    /** The rockets standing on the fixture pads; restart never creates one. */
    List<RocketEntity> rockets() {
        return level.getEntitiesOfClass(RocketEntity.class, new AABB(ROCKET.getX() - 8, 90, ROCKET.getZ() - 8, PAD.getX() + 8, 110, PAD.getZ() + 8));
    }
    /** Every rocket over both fixture chunks and the far cell, at any height, including one rebuilt at altitude. */
    List<RocketEntity> allRockets() {
        return level.getEntitiesOfClass(RocketEntity.class, new AABB(ROCKET.getX() - 8, level.getMinBuildHeight(), ROCKET.getZ() - 8,
                PAD.getX() + 16, level.getMaxBuildHeight(), PAD.getZ() + 8));
    }
    static int cargo(RocketEntity rocket) {
        var payload = rocket.snapshot().orElseThrow().blocks().get(0).blockEntityPayload().orElseThrow();
        return PressurizedTankRocketAdapter.fluid(payload.data()).getAmount();
    }
    static UUID id(RocketFluidCargoBlockEntity device) { return device.root().orElseThrow().device(); }
    static int tank(RocketFluidCargoBlockEntity device) { return device.root().orElseThrow().tank().amount(); }
    static RocketEscrowSection section(RocketFluidCargoBlockEntity device) { return device.root().orElseThrow().escrow(); }
}
