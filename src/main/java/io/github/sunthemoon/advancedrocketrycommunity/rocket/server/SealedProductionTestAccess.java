package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.RocketAdapterRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/** Gated fixture setup only; publication still uses the installed production coordinator and provider wrapper. */
public final class SealedProductionTestAccess {
    /**
     * W129aM (ADR-074 item 8): the journal is written off-thread and acknowledged on a later tick. Runs each step once
     * the journal is acknowledged current, in this tick when it already is, then requests the write the next step needs.
     * Call it from the test method itself; it registers at most one {@code onEachTick} and steps must not schedule.
     */
    public static void afterJournalAcknowledged(GameTestHelper helper, Runnable... steps) {
        var server = helper.getLevel().getServer(); EscrowTestEndpoints.requireEnabled(server);
        var data = RocketTransactionSavedData.get(server); int[] next = {0};
        Runnable advance = () -> {
            while (next[0] < steps.length) {
                if (!data.acknowledgedCurrent()) {
                    // WRITE_PENDING waits; a stale or failed publication is asked for again once the writer is idle.
                    if (!data.writerBusy() && data.requestCheckpoint(server) != RocketTransactionSavedData.CheckpointRequest.REQUESTED) {
                        throw new IllegalStateException("Fixture journal checkpoint request was refused");
                    }
                    return;
                }
                steps[next[0]++].run();
            }
        };
        advance.run();
        if (next[0] < steps.length) { helper.onEachTick(advance); }
    }
    /**
     * W129aM: one waiting tick of a sealed fixture held to the dedicated server's 50 ms ({@link GameTestTickPacer}). The
     * journal's acknowledgement now arrives from its IO thread in wall-clock time, while a native proof stays current
     * for a fixed number of ticks, so unthrottled GameTest ticks would measure the host instead of the protocol.
     */
    public static Runnable tickPacer(ServerLevel level) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        return new GameTestTickPacer(level.getServer())::pace;
    }
    public static BlockEntity stage(ServerLevel level, BlockEntity source, ResourceLocation adapterId,
            RocketBlockEntityAdapter provider) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        // Staging changes no journal entry; the former synchronous checkpoint is the caller's afterJournalAcknowledged.
        if (!RocketTransactionSavedData.get(level.getServer()).acknowledgedCurrent()) {
            throw new IllegalStateException("Stage C only once the journal is acknowledged (afterJournalAcknowledged)");
        }
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
        return carrier;
    }
    public static boolean unseal(BlockEntity carrier) {
        var level = (ServerLevel) carrier.getLevel(); EscrowTestEndpoints.requireEnabled(level.getServer());
        return RocketRuntime.unsealForReleaseTest(level, (SealedRocketBlockEntity) carrier);
    }
    public static BlockEntity stageHeld(ServerLevel level, BlockEntity source, ResourceLocation adapterId,
            RocketBlockEntityAdapter provider) {
        EscrowTestEndpoints.requireEnabled(level.getServer()); source.setLevel(level);
        var registry = new RocketAdapterRegistry(ForgeRegistries.BLOCK_ENTITY_TYPES::containsKey);
        registry.forOwner(adapterId.getNamespace()).register(adapterId,
                Set.of(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(source.getType())), 1, provider);
        var payload = registry.freeze().capture(source).optionalPayload().orElseThrow(); var at = source.getBlockPos();
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), SealedUnsealGameTests.position(at),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(source.getBlockState()), payload),
                        new RocketBlock(new RocketPosition(0, 0, 3), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState()))),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(2, 200, 1000, 1000, 1, 1, 1, 1), level.getGameTime());
        var id = UUID.randomUUID(); var entity = UUID.randomUUID(); var data = RocketTransactionSavedData.get(level.getServer());
        data.journalFor(snapshot, UUID.randomUUID()).write(new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY,
                RocketTransactionPhase.RESTORED, snapshot.snapshotId(), snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 1, entity));
        // The held recovery waits for this write's acknowledgement by itself (JOURNAL_UNPROVED, no diagnostic line).
        data.requestCheckpoint(level.getServer());
        if (source instanceof Container container) { container.clearContent(); } source.setRemoved();
        level.setBlock(at.south(3), Blocks.BEDROCK.defaultBlockState(), 2);
        return SealedUnsealGameTests.install(level, at, new SealedRocketMember(id, snapshot.snapshotId(), entity, snapshot.contentHash(),
                snapshot.sourceDimension(), SealedUnsealGameTests.position(at), 0, snapshot.blocks().get(0).state(), payload));
    }
    public static void releaseHeld(BlockEntity carrier) {
        var level = (ServerLevel) carrier.getLevel(); EscrowTestEndpoints.requireEnabled(level.getServer());
        var fence = carrier.getBlockPos().south(3);
        if (!level.getBlockState(fence).is(Blocks.BEDROCK)) { throw new IllegalStateException("Held fixture fence changed"); }
        level.setBlock(fence, Blocks.AIR.defaultBlockState(), 3);
    }
    public static boolean checkedAbsent(BlockEntity carrier) {
        var level = (ServerLevel) carrier.getLevel(); EscrowTestEndpoints.requireEnabled(level.getServer());
        var member = ((SealedRocketBlockEntity) carrier).member().orElseThrow();
        return RocketTransactionSavedData.get(level.getServer()).acknowledgedAbsent(member.transaction(), member.snapshot(), member.entity());
    }
    public static String diagnostic(BlockEntity carrier) {
        var level = (ServerLevel) carrier.getLevel(); EscrowTestEndpoints.requireEnabled(level.getServer());
        var member = ((SealedRocketBlockEntity) carrier).member().orElseThrow();
        return "state=" + RocketRuntime.sealedDiagnosticForReleaseTest(level.getServer(), member.transaction())
                + ",absent=" + checkedAbsent(carrier) + ",lazy=" + RocketRuntime.sealedLazyDiagnosticForReleaseTest(level.getServer());
    }
    private SealedProductionTestAccess() { }
}
