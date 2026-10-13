package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.AtmosphereVolume;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeId;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Server-owned torch conversion (ADR-066 section 3.2): placements and block events queue torches, volumes that lose
 * supply are swept, and each Level's queue spends a bounded budget after the atmosphere tick. Nothing scans a Level.
 */
final class TorchCombustionService {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Why a tagged torch placement was refused. */
    enum Refusal {
        UNSUPPORTED("unsupported"), BUSY("busy");

        final String key;

        Refusal(String key) {
            this.key = key;
        }
    }

    private final Map<ResourceKey<Level>, LevelState> levels = new HashMap<>();

    /** Admits a placed tagged torch, queues it when its cell may be noncombustible, or names the refusal. */
    Optional<Refusal> admitPlacement(ServerLevel level, BlockPos pos, BlockState placed) {
        if (!CombustionTorches.isCombustionTorch(placed)) {
            return Optional.empty();
        }
        if (CombustionTorches.pairFor(placed).isEmpty()) {
            return Optional.of(Refusal.UNSUPPORTED);
        }
        CombustionAir air = CombustionAir.at(level, pos);
        if (air == CombustionAir.COMBUSTIBLE || air == CombustionAir.UNMANAGED) {
            return Optional.empty();
        }
        TorchQueue queue = state(level).queue;
        if (queue.full()) {
            if (queue.refuseOverloaded()) {
                LOGGER.warn("Torch queue of {} is full ({} positions); refusing new torch placements until it drains",
                        level.dimension().location(), TorchQueue.MAX_POSITIONS);
            }
            return Optional.of(Refusal.BUSY);
        }
        queue.offer(pos.asLong());
        return Optional.empty();
    }

    /** A block event at or next to a loaded torch: queue it when its cell may be noncombustible. */
    void reconsider(ServerLevel level, BlockPos pos) {
        if (!CombustionAir.loaded(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!CombustionTorches.isCombustionTorch(state) || CombustionTorches.pairFor(state).isEmpty()) {
            return;
        }
        CombustionAir air = CombustionAir.at(level, pos);
        if (air == CombustionAir.NONCOMBUSTIBLE || air == CombustionAir.PENDING) {
            state(level).queue.offer(pos.asLong());
        }
    }

    /** After the atmosphere tick: start sweeps of volumes that lost supply, then spend each Level's budget. */
    void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Map<VolumeId, AtmosphereVolume> supplied = AtmosphereRuntime.suppliedVolumes(level);
            LevelState state = levels.get(level.dimension());
            if (state == null) {
                if (supplied.isEmpty()) {
                    continue;
                }
                state = state(level);
            }
            for (Map.Entry<VolumeId, AtmosphereVolume> previous : state.supplied.entrySet()) {
                if (!supplied.containsKey(previous.getKey())) {
                    state.queue.addSweep(previous.getValue().cells().stream()
                            .mapToLong(cell -> BlockPos.asLong(cell.x(), cell.y(), cell.z())).iterator());
                }
            }
            state.supplied = supplied;
            state.queue.tick(position -> check(level, BlockPos.of(position)),
                    position -> candidate(level, BlockPos.of(position)));
        }
    }

    Optional<TorchQueue.Metrics> metrics(ResourceKey<Level> level) {
        return Optional.ofNullable(levels.get(level)).map(state -> state.queue.metrics());
    }

    void unload(ServerLevel level) {
        LevelState removed = levels.remove(level.dimension());
        if (removed != null) {
            removed.queue.clear();
        }
    }

    void clear() {
        levels.values().forEach(state -> state.queue.clear());
        levels.clear();
    }

    /** Offers capacity distinct positions starting at one packed position; GameTest capacity fixtures only. */
    int fillForTest(ServerLevel level, long first) {
        TorchQueue queue = state(level).queue;
        BlockPos origin = BlockPos.of(first);
        int added = 0;
        for (int index = 0; !queue.full() && index < TorchQueue.MAX_POSITIONS * 2; index++) {
            if (queue.offer(origin.offset(index % 64, 0, index / 64).asLong()) == TorchQueue.Offer.ADDED) {
                added++;
            }
        }
        return added;
    }

    private TorchQueue.Outcome check(ServerLevel level, BlockPos pos) {
        if (!CombustionAir.loaded(level, pos)) {
            return TorchQueue.Outcome.DONE;
        }
        BlockState state = level.getBlockState(pos);
        if (!CombustionTorches.isCombustionTorch(state)) {
            return TorchQueue.Outcome.DONE;
        }
        Optional<TorchPair> pair = CombustionTorches.pairFor(state);
        if (pair.isEmpty()) {
            return TorchQueue.Outcome.DONE;
        }
        return switch (CombustionAir.at(level, pos)) {
            case COMBUSTIBLE, UNMANAGED -> TorchQueue.Outcome.DONE;
            case PENDING -> TorchQueue.Outcome.DEFER;
            case NONCOMBUSTIBLE -> {
                if (!CombustionAir.settled(level)) {
                    yield TorchQueue.Outcome.DEFER;
                }
                TorchWorldChanges.extinguish(level, pos, state, pair.get(), CommonConfig.dropExtinguishedTorches());
                yield TorchQueue.Outcome.DONE;
            }
        };
    }

    private boolean candidate(ServerLevel level, BlockPos pos) {
        if (!CombustionAir.loaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return CombustionTorches.isCombustionTorch(state) && CombustionTorches.pairFor(state).isPresent();
    }

    private LevelState state(ServerLevel level) {
        return levels.computeIfAbsent(level.dimension(), ignored -> new LevelState());
    }

    private static final class LevelState {
        private final TorchQueue queue = new TorchQueue();
        private Map<VolumeId, AtmosphereVolume> supplied = Map.of();
    }
}
