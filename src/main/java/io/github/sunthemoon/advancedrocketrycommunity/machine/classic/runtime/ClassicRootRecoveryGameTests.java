package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourcesCodec;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicRootRecoveryGameTests {
    @GameTest(template = "empty", batch = "w01f_missing_roots", timeoutTicks = 30)
    public static void missingRootsAllowUnrelatedChestAndPlayerChangesToSave(GameTestHelper helper) {
        for (String missing : List.of(ClassicCheckpointCodec.ROOT, ClassicResourcesCodec.ROOT, ClassicHatchBlockEntity.ROOT)) {
            try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, true)) {
                var ownerId = fixture.controller.controllerState().machineInstanceId();
                if (missing.equals(ClassicHatchBlockEntity.ROOT)) {
                    for (var hatch : fixture.hatches()) {
                        var saved = hatch.saveWithFullMetadata(); saved.remove(missing); hatch.load(saved);
                        helper.assertTrue(!hatch.repairRequired() && hatch.ownerClaim().isEmpty()
                                && hatch.multiblockBinding().isEmpty() && hatch.energyStored() == 0, "Missing hatch was not empty and unbound");
                    }
                } else {
                    var saved = fixture.controller.saveWithFullMetadata(); saved.remove(missing); fixture.controller.load(saved);
                    helper.assertTrue(!fixture.controller.repairRequired() && !fixture.controller.formed()
                            && fixture.controller.controllerState().generation() == 0 && fixture.controller.checkpoint().claimedParts().isEmpty()
                            && fixture.controller.checkpoint().resources().banks().isEmpty() && fixture.controller.checkpoint().job().isEmpty()
                            && !ownerId.equals(fixture.controller.controllerState().machineInstanceId()), "Missing controller root was not a fresh checkpoint");
                }
                var chestPosition = fixture.origin.offset(0, 0, 2);
                helper.getLevel().setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                try {
                    var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPosition);
                    var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "W01F_Save"));
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
                    player.getInventory().add(chest.removeItem(0, 10));
                    var chestSaved = chest.saveWithFullMetadata();
                    var playerSaved = player.saveWithoutId(new CompoundTag());
                    var entities = new ListTag(); entities.add(chestSaved); entities.add(fixture.controller.saveWithFullMetadata());
                    fixture.hatches().forEach(hatch -> entities.add(hatch.saveWithFullMetadata()));
                    var chunkSaved = new CompoundTag(); chunkSaved.put("block_entities", entities);
                    var chunk = helper.getLevel().getChunkAt(fixture.origin);
                    var save = new ChunkDataEvent.Save(chunk, helper.getLevel(), chunkSaved);
                    GuardedChunkSaves.beforeSave(save); ClassicRuntime.save(save);
                    chest.load(chestSaved); player.load(playerSaved);
                    helper.assertTrue(chest.getItem(0).isEmpty()
                            && player.getInventory().countItem(Items.DIAMOND) == 10, "Unrelated chest/player transfer was not conserved");
                } finally { helper.getLevel().setBlock(chestPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w01f_unsafe_roots", timeoutTicks = 30)
    public static void boundedFutureRootsSaveVerbatimButPresentUnsafeRootsKeepDenial(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            var chunkPosition = new ChunkPos(fixture.origin);
            var hatchChunkPosition = new ChunkPos(fixture.inputPosition);
            var state = helper.getLevel().getCapability(GuardedChunkSaves.CAPABILITY).orElseThrow(() -> new IllegalStateException("Guard"));
            var original = fixture.controller.saveWithFullMetadata(); var hatchOriginal = fixture.inputHatch.saveWithFullMetadata();
            try {
                var future = hatchOriginal.copy(); future.getCompound(ClassicHatchBlockEntity.ROOT).putInt("schema_version", 99);
                fixture.inputHatch.load(future);
                helper.assertTrue(fixture.inputHatch.repairRequired()
                        && future.get(ClassicHatchBlockEntity.ROOT).equals(fixture.inputHatch.saveWithFullMetadata().get(ClassicHatchBlockEntity.ROOT))
                        && state.reason(chunkPosition.toLong()).isEmpty(), "Bounded future hatch root was refused or normalized");
                var futureOwner = original.copy(); futureOwner.getCompound(ClassicCheckpointCodec.ROOT).putInt("schema_version", 99);
                fixture.controller.load(futureOwner);
                helper.assertTrue(fixture.controller.repairRequired()
                        && futureOwner.get(ClassicCheckpointCodec.ROOT).equals(fixture.controller.saveWithFullMetadata().get(ClassicCheckpointCodec.ROOT))
                        && state.reason(chunkPosition.toLong()).isEmpty(), "Bounded future controller root was refused or normalized");
                var unsafe = original.copy(); unsafe.remove(ClassicCheckpointCodec.ROOT);
                unsafe.getCompound(ClassicResourcesCodec.ROOT).putString("large", "x".repeat(32_768));
                fixture.controller.load(unsafe);
                helper.assertTrue(fixture.controller.repairRequired() && state.reason(chunkPosition.toLong()).isPresent(), "Present unsafe root escaped refusal with a missing companion");
                helper.assertTrue(unsafe.get(ClassicResourcesCodec.ROOT).equals(fixture.controller.saveWithFullMetadata().get(ClassicResourcesCodec.ROOT)), "Unsafe root was replaced");
                GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunkPosition);
                var unsafeHatch = hatchOriginal.copy(); unsafeHatch.getCompound(ClassicHatchBlockEntity.ROOT).putString("large", "x".repeat(4_096));
                fixture.inputHatch.load(unsafeHatch);
                helper.assertTrue(fixture.inputHatch.repairRequired() && state.reason(hatchChunkPosition.toLong()).isPresent(), "Unsafe hatch root escaped refusal");
            } finally {
                fixture.controller.load(original); fixture.inputHatch.load(hatchOriginal);
                GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunkPosition);
                GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), hatchChunkPosition);
            }
        }
        helper.succeed();
    }
    private ClassicRootRecoveryGameTests() { }
}
