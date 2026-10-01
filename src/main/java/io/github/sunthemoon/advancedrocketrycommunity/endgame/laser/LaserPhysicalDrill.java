package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.EndgameProtection;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;

/**
 * ADR-055 section 3: one evaluation of a physical link from the controller's tick. A contact (both chunks
 * {@code FULL}) settles a debt first, layer by layer, whatever the switches; then, only while breaking is allowed,
 * a paid layer runs (credit) or a new one is paid and run in the same tick. A layer is planned completely before
 * anything changes: the protection chain, the classification of every cell, a break event per breakable cell and the
 * whole drop set against the marker's buffer. Any stop leaves the layer untouched. Every read goes through
 * {@code getChunkNow}, so no chunk is loaded.
 */
final class LaserPhysicalDrill {
    private static final String FAKE_PLAYER_NAME = "[ARCE laser]";

    private LaserPhysicalDrill() {
    }

    /** What the controller knows for this evaluation. */
    record Context(ServerLevel drillLevel, EndgameDevices devices, EndgameService service, UUID controller, UUID owner,
                   LaserDrillSettings settings, EndgameCode common, boolean breakingAllowed,
                   Optional<CelestialBodyDefinition> orbitBody, long now) {
        Context {
            Objects.requireNonNull(drillLevel, "drillLevel");
            Objects.requireNonNull(common, "common");
            Objects.requireNonNull(orbitBody, "orbitBody");
        }
    }

    /** The code to show and whether the drill waits for a layer grant next tick. */
    record Outcome(EndgameCode code, boolean waitingForBudget, boolean settled, boolean lost) {
        static Outcome of(EndgameCode code, boolean settled, boolean lost) {
            return new Outcome(code, false, settled, lost);
        }
    }

    static Outcome evaluate(Context context, LaserLink link, LaserDrillStorage storage) {
        EndgameRoot root = context.service().root().orElseThrow();
        Optional<EndpointRecord> record = root.endpoint(link.marker())
                .filter(found -> found.state() == EndpointRecord.State.ACTIVE);
        if (record.isEmpty()) {
            return Outcome.of(EndgameCode.LINK_LOST, false, true);
        }
        ServerLevel markerLevel = context.drillLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                record.get().level()));
        BlockPos markerPos = BlockPos.of(record.get().pos());
        if (markerLevel == null || !neighbourhoodFull(markerLevel, markerPos)) {
            return Outcome.of(context.common() != EndgameCode.OK ? context.common() : EndgameCode.TARGET_UNLOADED,
                    false, false);
        }
        if (!(markerLevel.getChunkSource().getChunkNow(markerPos.getX() >> 4, markerPos.getZ() >> 4)
                .getBlockEntity(markerPos) instanceof LaserTargetBlockEntity marker)
                || !marker.deviceId().filter(link.marker()::equals).isPresent() || marker.frozen()
                || !marker.accepts(context.controller(), link.linkId())) {
            return Outcome.of(EndgameCode.LINK_LOST, false, true);
        }
        marker.adopt(context.controller(), link.linkId());

        // Contact: settle a debt layer by layer as energy allows; no new layer until it is paid (review R1-M7).
        int cost = context.settings().costFe();
        boolean paidDebt = false;
        while (marker.opsDone() > link.opsPaid() && storage.energy() >= cost) {
            storage.spend(cost);
            link.paid();
            paidDebt = true;
        }
        if (marker.opsDone() > link.opsPaid()) {
            return Outcome.of(EndgameCode.ENERGY_DEBT, false, false);
        }
        if (paidDebt) {
            audit(context, "DEBT_SETTLED", EndgameCode.OK, "marker=" + link.marker() + " ops=" + link.opsPaid());
        }
        boolean credit = link.opsPaid() > marker.opsDone();

        // Breaking: the shared conditions, the switch, then link rules 1 and 2, the footprint and the floor.
        EndgameCode stop = breakingStop(context, link, record.get(), marker, markerLevel, markerPos);
        if (stop != EndgameCode.OK) {
            return Outcome.of(stop, !credit, false);
        }
        if (!credit && storage.energy() < cost) {
            return Outcome.of(EndgameCode.INSUFFICIENT_ENERGY, true, false);
        }
        Layer layer = plan(context, markerLevel, markerPos, marker.nextLayer());
        if (layer.code() != EndgameCode.OK) {
            marker.reportStop(layer.code());
            if (layer.code() == EndgameCode.TARGET_PROTECTED
                    && context.service().audit().protectionNoticeDue(context.now(), context.controller(), layer.code())) {
                audit(context, "protection", layer.code(), "marker=" + link.marker() + " y=" + marker.nextLayer());
            }
            return Outcome.of(layer.code(), !credit, false);
        }
        if (!context.devices().laserLayers().take(context.controller())) {
            context.devices().laserLayers().request(context.controller());
            return new Outcome(EndgameCode.OK, true, !credit, false);
        }
        if (!credit) {
            storage.spend(cost);
            link.paid();
        }
        int y = marker.nextLayer();
        for (BlockPos cell : layer.breakable()) {
            markerLevel.removeBlock(cell, false);
        }
        marker.layerDone(layer.drops(), context.now(), context.settings().operationIntervalTicks()
                + LaserBeam.AFTERGLOW_TICKS);
        int items = layer.drops().stream().mapToInt(ItemStack::getCount).sum();
        audit(context, credit ? "CREDIT_USED" : "layer", EndgameCode.OK, "marker=" + link.marker() + " y=" + y
                + " cells=" + layer.breakable().size() + " items=" + items);
        return Outcome.of(EndgameCode.OK, link.opsPaid() == marker.opsDone(), false);
    }

    private static EndgameCode breakingStop(Context context, LaserLink link, EndpointRecord record,
                                            LaserTargetBlockEntity marker, ServerLevel markerLevel, BlockPos markerPos) {
        if (context.common() != EndgameCode.OK) {
            return context.common();
        }
        if (!context.breakingAllowed()) {
            return EndgameCode.PHYSICAL_DISABLED;
        }
        if (context.orbitBody().isEmpty()) {
            return EndgameCode.ORBIT_BODY_UNAVAILABLE;
        }
        // Rule 1: the marker is the drill owner's, or an operator linked it.
        if (!record.owner().equals(context.owner()) && !link.operatorLink()) {
            return EndgameCode.TARGET_FOREIGN;
        }
        // Rule 2: the marker is on the station's live orbit body, a surface body, in that body's Level.
        CelestialBodyDefinition body = context.orbitBody().get();
        if (!body.supportsSurfaceArrival() || body.levelKey().filter(markerLevel.dimension()::equals).isEmpty()) {
            return EndgameCode.TARGET_WRONG_BODY;
        }
        if (!LaserShaft.footprintInsideChunk(markerPos)) {
            return EndgameCode.FOOTPRINT_AT_CHUNK_EDGE;
        }
        if (marker.nextLayer() < LaserShaft.floor(markerPos, markerLevel.getMinBuildHeight(),
                context.settings().maxDepth())) {
            return EndgameCode.COMPLETE;
        }
        return EndgameCode.OK;
    }

    /** Rule 3: the marker's chunk and its eight neighbours are FULL, so neighbour updates load nothing. */
    static boolean neighbourhoodFull(ServerLevel level, BlockPos marker) {
        int cx = marker.getX() >> 4;
        int cz = marker.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private record Layer(EndgameCode code, List<BlockPos> breakable, List<ItemStack> drops) {
        static Layer stop(EndgameCode code) {
            return new Layer(code, List.of(), List.of());
        }
    }

    /** Plans one whole layer; nothing changes unless every step passes. */
    private static Layer plan(Context context, ServerLevel level, BlockPos marker, int y) {
        List<BlockPos> cells = LaserShaft.layer(marker, y);
        BlockPos min = cells.get(0);
        BlockPos max = cells.get(LaserShaft.CELLS - 1);
        EndgameCode chain = EndgameProtection.check(new EndgameProtection.Batch(EndgameSystem.LASER_DRILL,
                EndgameEffect.BLOCK_BREAK, context.owner(), Optional.empty(), level.dimension(), min, max, true),
                new ForgeProtectionView(level, context.service().root().orElseThrow().zones()));
        if (chain != EndgameCode.OK) {
            return Layer.stop(chain);
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(marker.getX() >> 4, marker.getZ() >> 4);
        List<LaserShaft.Cell> kinds = new ArrayList<>(LaserShaft.CELLS);
        List<BlockState> states = new ArrayList<>(LaserShaft.CELLS);
        for (BlockPos cell : cells) {
            BlockState state = chunk.getBlockState(cell);
            states.add(state);
            kinds.add(classify(level, cell, state));
        }
        EndgameCode classification = LaserShaft.classify(kinds);
        if (classification != EndgameCode.OK) {
            return Layer.stop(classification);
        }
        FakePlayer breaker = FakePlayerFactory.get(level, new GameProfile(context.owner(), FAKE_PLAYER_NAME));
        List<BlockPos> breakable = new ArrayList<>();
        List<BlockState> breakableStates = new ArrayList<>();
        for (int i = 0; i < LaserShaft.CELLS; i++) {
            if (kinds.get(i) == LaserShaft.Cell.BREAKABLE) {
                breakable.add(cells.get(i));
                breakableStates.add(states.get(i));
            }
        }
        // Step 7: a standard break event per breakable cell, all before any removal.
        for (int i = 0; i < breakable.size(); i++) {
            if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, breakable.get(i),
                    breakableStates.get(i), breaker))) {
                return Layer.stop(EndgameCode.TARGET_PROTECTED);
            }
        }
        // No tool and no fortune; harvest-tool requirements are not applied (a balance decision).
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < breakable.size(); i++) {
            drops.addAll(Block.getDrops(breakableStates.get(i), level, breakable.get(i), null, breaker,
                    ItemStack.EMPTY));
        }
        drops.removeIf(ItemStack::isEmpty);
        return drops.isEmpty() || markerFits(level, marker, drops) ? new Layer(EndgameCode.OK, breakable, drops)
                : Layer.stop(EndgameCode.TARGET_BUFFER_FULL);
    }

    private static boolean markerFits(ServerLevel level, BlockPos marker, List<ItemStack> drops) {
        return level.getChunkSource().getChunkNow(marker.getX() >> 4, marker.getZ() >> 4)
                .getBlockEntity(marker) instanceof LaserTargetBlockEntity target && target.fits(drops);
    }

    private static LaserShaft.Cell classify(Level level, BlockPos cell, BlockState state) {
        if (state.isAir()) {
            return LaserShaft.Cell.AIR;
        }
        if (state.hasBlockEntity() || state.getDestroySpeed(level, cell) < 0 || state.is(ModBlockTags.LASER_DRILL_IMMUNE)) {
            return LaserShaft.Cell.IMMUNE;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return LaserShaft.Cell.FLUID;
        }
        return LaserShaft.Cell.BREAKABLE;
    }

    private static void audit(Context context, String action, EndgameCode code, String fields) {
        context.service().audit().line(context.now(), EndgameSystem.LASER_DRILL.id(), action, code.name(),
                context.controller(), context.owner(), null, fields);
    }
}
