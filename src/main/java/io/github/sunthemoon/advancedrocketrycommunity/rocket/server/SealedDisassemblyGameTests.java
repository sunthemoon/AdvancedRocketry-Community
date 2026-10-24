package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedTerrainImage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedDisassemblyGameTests {
    @GameTest(template = "rocket_test", batch = "w152_disassembly", timeoutTicks = 400)
    public static void retainedURequiresNativeSealedTerrainAndEntityAbsenceBeforeCompletion(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var chest = new ChestBlockEntity(origin.east(), Blocks.CHEST.defaultBlockState());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var payload = new VanillaContainerRocketAdapter().capture(chest);
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), List.of(
                        new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState())),
                        new RocketBlock(new RocketPosition(1, 0, 0), RocketBlockStateAdapter.capture(Blocks.CHEST.defaultBlockState()), payload)),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(2, 200, 1_000, 1_000, 1, 1, 1, 1), level.getGameTime());
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2); level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), 2);
        var rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "Rocket type unavailable");
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        RocketTransferEntities.positionAtOrigin(rocket, snapshot.sourceOrigin());
        helper.assertTrue(level.addFreshEntity(rocket), "Source D was not published");
        var transaction = new UUID[1]; var done = new boolean[1]; var callbackFailure = new String[1];
        Consumer<SealedDisassembly.Result> completion = result -> {
            try {
                helper.assertTrue(result == SealedDisassembly.Result.SEALED, "Native completion did not elect sealed terrain: " + result
                        + ":" + RocketRuntime.sealedDiagnosticForReleaseTest(server, transaction[0]));
                var journal = RocketTransactionSavedData.get(server);
                helper.assertTrue(journal.acknowledgedAbsent(transaction[0], snapshot.snapshotId(), rocket.getUUID()),
                        "Completion ran before exact checked U absence");
                helper.assertTrue(level.getEntity(rocket.getUUID()) == null, "D remained live beside completed C");
                var members = List.of(((SealedRocketBlockEntity) level.getBlockEntity(origin)).member().orElseThrow(),
                        ((SealedRocketBlockEntity) level.getBlockEntity(origin.east())).member().orElseThrow());
                var terrain = server.getWorldPath(LevelResource.ROOT).resolve("region");
                var chunks = members.stream().collect(java.util.stream.Collectors.groupingBy(member ->
                        new ChunkPos(member.position().x() >> 4, member.position().z() >> 4)));
                for (var chunk : chunks.entrySet()) {
                    helper.assertTrue(SealedTerrainImage.matches(AnvilRecordReader.read(terrain, chunk.getKey()).root(),
                            chunk.getValue(), level.getBlockState(origin), true), "Native terrain was not the complete sealed footprint");
                }
                var entities = AnvilRecordReader.read(server.getWorldPath(LevelResource.ROOT).resolve("entities"), new ChunkPos(origin)).root();
                helper.assertTrue(RocketRetirementProof.absentSlot(entities, rocket.assemblyTransactionId().orElseThrow(), rocket.getUUID()),
                        "Native entity slot still held D at checked U retirement");
                done[0] = true; helper.succeed();
            } catch (java.io.IOException | RuntimeException assertion) {
                callbackFailure[0] = assertion.getClass().getSimpleName() + ":" + assertion.getMessage();
            }
        };
        helper.onEachTick(() -> {
            if (callbackFailure[0] != null) { helper.fail(callbackFailure[0]); }
            if (done[0]) { return; }
            if (transaction[0] == null && level.areEntitiesLoaded(new ChunkPos(origin).toLong())) {
                try {
                    var begin = RocketRuntime.class.getMethod("beginSealedForReleaseTest", RocketEntity.class, Consumer.class);
                    transaction[0] = (UUID) begin.invoke(null, rocket, completion);
                    helper.assertTrue(transaction[0] != null, "Ordinary sealed request was refused");
                    helper.assertTrue(RocketRuntime.sealedFrozen(rocket), "Admission did not freeze D before writing U");
                    rocket.kill(); helper.assertTrue(rocket.isAlive(), "Pending D accepted kill before election");
                    boolean changed = false;
                    try { rocket.updateFlightData(rocket.flightData().orElseThrow()); changed = true; }
                    catch (IllegalStateException expected) { }
                    helper.assertTrue(!changed, "Pending D accepted a flight/manifest mutation");
                } catch (ReflectiveOperationException failure) { helper.fail("Sealed executor unavailable: " + failure.getClass().getSimpleName()); }
            }
            if (transaction[0] == null) { return; }
            if (rocket.isAlive()) { helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Pending D exposed resource mutation"); }
            for (var position : List.of(origin, origin.east())) {
                if (!(level.getBlockEntity(position) instanceof SealedRocketBlockEntity carrier)) { continue; }
                helper.assertTrue(carrier.member().filter(member -> member.transaction().equals(transaction[0])).isPresent(), "Incomplete C was published");
                helper.assertTrue(!carrier.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Retained C exposed inventory");
            }
        });
    }
    private SealedDisassemblyGameTests() { }
}
