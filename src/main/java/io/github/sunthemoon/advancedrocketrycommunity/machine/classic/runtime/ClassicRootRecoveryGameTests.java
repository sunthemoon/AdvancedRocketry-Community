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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicRootRecoveryGameTests {
    private static final List<String> CONTROLLER_ROOTS = List.of(ClassicCheckpointCodec.ROOT, ClassicResourcesCodec.ROOT,
            "arce_recipe_signature", "arce_process_journal", "arce_process", "arce_machine");
    private static final String ALL_CONTROLLER_ROOTS = "all controller roots";

    @GameTest(template = "empty", batch = "w01f_missing_roots", timeoutTicks = 30)
    public static void missingRootsAllowUnrelatedChestAndPlayerChangesToSave(GameTestHelper helper) {
        for (String missing : List.of(ClassicCheckpointCodec.ROOT, ClassicResourcesCodec.ROOT, ALL_CONTROLLER_ROOTS, ClassicHatchBlockEntity.ROOT)) {
            try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, true)) {
                var ownerId = fixture.controller.controllerState().machineInstanceId();
                var original = fixture.controller.saveWithFullMetadata();
                var guard = helper.getLevel().getCapability(GuardedChunkSaves.CAPABILITY).orElseThrow(() -> new IllegalStateException("Guard"));
                var chunkPosition = new ChunkPos(fixture.origin);
                if (missing.equals(ClassicHatchBlockEntity.ROOT)) {
                    for (var hatch : fixture.hatches()) {
                        var saved = hatch.saveWithFullMetadata(); saved.remove(missing); hatch.load(saved);
                        helper.assertTrue(!hatch.repairRequired() && hatch.ownerClaim().isEmpty()
                                && hatch.multiblockBinding().isEmpty() && hatch.energyStored() == 0, "Missing hatch was not empty and unbound");
                    }
                } else if (missing.equals(ALL_CONTROLLER_ROOTS)) {
                    var saved = original.copy(); CONTROLLER_ROOTS.forEach(saved::remove); fixture.controller.load(saved);
                    helper.assertTrue(!fixture.controller.repairRequired() && !fixture.controller.formed()
                            && fixture.controller.controllerState().generation() == 0 && fixture.controller.checkpoint().claimedParts().isEmpty()
                            && fixture.controller.checkpoint().resources().banks().isEmpty() && fixture.controller.checkpoint().job().isEmpty()
                            && !ownerId.equals(fixture.controller.controllerState().machineInstanceId()), "Rootless controller was not a fresh checkpoint");
                } else {
                    // One missing required root with its companion present is a partial snapshot, not a new controller.
                    var saved = original.copy(); saved.remove(missing); fixture.controller.load(saved.copy());
                    String present = missing.equals(ClassicCheckpointCodec.ROOT) ? ClassicResourcesCodec.ROOT : ClassicCheckpointCodec.ROOT;
                    var after = fixture.controller.saveWithFullMetadata();
                    helper.assertTrue(fixture.controller.repairRequired() && !after.contains(missing)
                            && saved.get(present).equals(after.get(present)) && guard.reason(chunkPosition.toLong()).isEmpty(),
                            "Partial controller root was reset, normalized or refused");
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
                } finally {
                    helper.getLevel().setBlock(chestPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (fixture.controller.repairRequired()) { fixture.controller.load(original); }
                    GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunkPosition);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w01g_partial_machine_root", timeoutTicks = 30)
    public static void missingMachineRootKeepsSevenIngotsQuarantinedWithoutRefusal(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            fixture.seed(7, 0);
            var original = fixture.controller.saveWithFullMetadata();
            var partial = original.copy(); partial.remove(ClassicCheckpointCodec.ROOT);
            quarantine(helper, fixture, original, partial, ClassicResourcesCodec.ROOT, ClassicCheckpointCodec.ROOT);
            helper.assertTrue(fixture.input().getStackInSlot(0).getCount() == 7
                    && ClassicResourcesCodec.encode(fixture.controller.checkpoint().resources()).equals(original.get(ClassicResourcesCodec.ROOT)),
                    "Restored controller lost the quarantined ingots");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w01g_partial_resources_root", timeoutTicks = 30)
    public static void missingResourcesRootKeepsAnActiveJobQuarantinedWithoutRefusal(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            fixture.seed(1, 6_000); fixture.tick(20);
            int progress = fixture.controller.progressTicks(); long consumed = fixture.controller.consumedEnergy();
            helper.assertTrue(progress > 0 && consumed > 0, "Fixture job did not start");
            var original = fixture.controller.saveWithFullMetadata();
            var partial = original.copy(); partial.remove(ClassicResourcesCodec.ROOT);
            quarantine(helper, fixture, original, partial, ClassicCheckpointCodec.ROOT, ClassicResourcesCodec.ROOT);
            // The quarantine ticks spent nothing; the restoring tick advances the retained job by exactly one.
            helper.assertTrue(fixture.controller.progressTicks() == progress + 1 && fixture.controller.consumedEnergy() == consumed + 20,
                    "Restored controller lost or advanced job progress");
            fixture.tick(300);
            helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.controller.checkpoint().batchOrdinal() == 1
                    && fixture.power.energyStored() == 0 && fixture.input().getStackInSlot(0).isEmpty()
                    && fixture.output().getStackInSlot(0).getCount() == 2, "Restored job did not complete exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w01g_partial_future_root", timeoutTicks = 30)
    public static void futureMachineRootWithoutResourcesStaysQuarantinedVerbatim(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            fixture.seed(4, 0);
            var original = fixture.controller.saveWithFullMetadata();
            var partial = original.copy(); partial.remove(ClassicResourcesCodec.ROOT);
            partial.getCompound(ClassicCheckpointCodec.ROOT).putInt("schema_version", 99);
            quarantine(helper, fixture, original, partial, ClassicCheckpointCodec.ROOT, ClassicResourcesCodec.ROOT);
            helper.assertTrue(fixture.input().getStackInSlot(0).getCount() == 4, "Restored controller lost its bank");
        }
        helper.succeed();
    }

    /**
     * W01G review L3: a quarantined controller exposes no Item or Fluid hatch, but its power plug still receives FE,
     * which stays in the plug unspent. This pins the behaviour that the ADR-064 and R-021 texts describe.
     */
    @GameTest(template = "empty", batch = "w55_quarantined_plug", timeoutTicks = 30)
    public static void quarantinedControllerPlugStillReceivesFeAndSpendsNone(GameTestHelper helper) {
        try (var fixture = new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false)) {
            fixture.seed(2, 0);
            var original = fixture.controller.saveWithFullMetadata();
            var partial = original.copy(); partial.remove(ClassicCheckpointCodec.ROOT);
            try {
                fixture.controller.load(partial); fixture.manager.observe(fixture.controller); fixture.tick(5);
                helper.assertTrue(fixture.controller.repairRequired() && fixture.controller.reason() == ClassicWait.REPAIR,
                        "Partial controller root was not quarantined");
                int accepted = fixture.power.getCapability(ForgeCapabilities.ENERGY)
                        .map(storage -> storage.receiveEnergy(4_000, false)).orElse(0);
                fixture.tick(20);
                helper.assertTrue(accepted == 4_000 && fixture.power.energyStored() == 4_000
                        && !fixture.inputHatch.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                        "Quarantined plug accepted " + accepted + " FE and holds " + fixture.power.energyStored());
            } finally {
                fixture.controller.load(original); fixture.manager.observe(fixture.controller); fixture.manager.tick();
            }
        }
        helper.succeed();
    }

    /** Loads {@code partial}, proves it is quarantined verbatim without a save denial, then restores {@code original}. */
    private static void quarantine(GameTestHelper helper, ClassicLatheGameTests.Fixture fixture, CompoundTag original,
                                   CompoundTag partial, String present, String missing) {
        var chunkPosition = new ChunkPos(fixture.origin);
        var guard = helper.getLevel().getCapability(GuardedChunkSaves.CAPABILITY).orElseThrow(() -> new IllegalStateException("Guard"));
        var ownerId = fixture.controller.controllerState().machineInstanceId();
        int energy = fixture.power.energyStored();
        try {
            fixture.controller.load(partial.copy());
            fixture.manager.observe(fixture.controller); fixture.tick(5);
            var after = fixture.controller.saveWithFullMetadata();
            helper.assertTrue(fixture.controller.repairRequired() && fixture.controller.reason() == ClassicWait.REPAIR
                    && !fixture.controller.formed(), "Partial controller root was not quarantined");
            helper.assertTrue(partial.get(present).equals(after.get(present)) && !after.contains(missing),
                    "Partial controller root was not saved verbatim");
            helper.assertTrue(!fixture.inputHatch.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    && ClassicRemovalPolicy.blocks(helper.getLevel(), fixture.inputPosition)
                    && fixture.power.energyStored() == energy, "Quarantined controller exposed, released or spent resources");
            var entities = new ListTag(); entities.add(after);
            fixture.hatches().forEach(hatch -> entities.add(hatch.saveWithFullMetadata()));
            var chunkSaved = new CompoundTag(); chunkSaved.put("block_entities", entities);
            var save = new ChunkDataEvent.Save(helper.getLevel().getChunkAt(fixture.origin), helper.getLevel(), chunkSaved);
            GuardedChunkSaves.beforeSave(save); ClassicRuntime.save(save);
            helper.assertTrue(guard.reason(chunkPosition.toLong()).isEmpty(), "Partial controller root refused its chunk save");
        } finally {
            fixture.controller.load(original); fixture.manager.observe(fixture.controller); fixture.manager.tick();
            GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunkPosition);
        }
        helper.assertTrue(fixture.controller.formed() && ownerId.equals(fixture.controller.controllerState().machineInstanceId()),
                "Restored controller did not re-form with its own identity");
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
