package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldSaveGameTests {
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void abandonedWorldDropThenNativePlayerBreakDropsExactlyOneProjector(GameTestHelper helper) {
        try (var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST)) {
            var pos = f.identity.source();
            var box = new net.minecraft.world.phys.AABB(pos).inflate(2);
            var old = f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                    .map(net.minecraft.world.entity.item.ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            try {
                net.minecraft.world.level.block.Block.dropResources(f.source.getBlockState(), f.level, pos, f.source);
                helper.assertTrue(f.level.getBlockEntity(pos) == f.source
                        && f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size() == old.size(),
                        "Drop computation without removal changed the source or emitted stock");
                var player = new net.minecraftforge.common.util.FakePlayer(f.level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FieldBreaker"));
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
                helper.assertTrue(player.gameMode.destroyBlock(pos), "Native player break refused the owned projector");
                var drops = f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                        .filter(entity -> !old.contains(entity.getUUID())).toList();
                helper.assertTrue(f.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.AIR)
                        && drops.size() == 1 && drops.get(0).getItem().getCount() == 1
                        && drops.get(0).getItem().is(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.FORCE_FIELD_PROJECTOR.get()),
                        "Abandoned deferred drop plus native player loot duplicated or lost a projector");
            } finally {
                f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                        .filter(entity -> !old.contains(entity.getUUID())).forEach(net.minecraft.world.entity.Entity::discard);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void deferredWorldDropIsOneShotAndBoundToTheSourcePosition(GameTestHelper helper) {
        try (var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST)) {
            BlockPos pos = f.identity.source();
            f.source.deferWorldDrop(pos);
            helper.assertTrue(!f.source.takeWorldDrop(pos.east()) && !f.source.takeWorldDrop(pos),
                    "A mismatched removal position consumed or retained a deferred drop");
            f.source.deferWorldDrop(pos.east());
            helper.assertTrue(!f.source.takeWorldDrop(pos), "A mismatched arming position granted a deferred drop");
            var mutable = pos.mutable();
            f.source.deferWorldDrop(mutable);
            mutable.move(Direction.EAST);
            helper.assertTrue(f.source.takeWorldDrop(pos) && !f.source.takeWorldDrop(pos),
                    "A same-tick source drop was not exactly one-shot");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void deferredWorldDropExpiresAfterItsArmingTick(GameTestHelper helper) {
        var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST);
        BlockPos pos = f.identity.source();
        f.source.deferWorldDrop(pos);
        long armedAt = f.level.getGameTime();
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(f.level.getGameTime() > armedAt && !f.source.takeWorldDrop(pos),
                        "A previous-tick deferred drop remained usable");
                helper.succeed();
            } finally { f.close(); }
        });
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void oversizedNativeReferenceRefusesWholeChunkEvenAfterOutgoingBeDisappears(GameTestHelper helper) {
        var chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        try {
            for (boolean projector : new boolean[]{true, false}) {
                var state = projector ? ModBlocks.FORCE_FIELD_PROJECTOR.get().defaultBlockState() : ModBlocks.FORCE_FIELD.get().defaultBlockState();
                ForceFieldNativeBlockEntity entity = projector ? new ForceFieldProjectorBlockEntity(helper.absolutePos(BlockPos.ZERO), state)
                        : new ForceFieldBlockEntity(helper.absolutePos(BlockPos.ZERO), state);
                String key = projector ? ForceFieldCodecs.PROJECTOR_ROOT : ForceFieldCodecs.FIELD_ROOT;
                CompoundTag raw = new CompoundTag(); raw.putByteArray("future", new byte[8192]);
                CompoundTag outer = new CompoundTag(); outer.put(key, raw); entity.load(outer);
                var encoded = entity.saveWithFullMetadata();
                helper.assertTrue(entity.quarantined() && encoded.get(key) == raw, "Oversized root was copied or normalized");
                CompoundTag outgoing = new CompoundTag(); ListTag entities = new ListTag(); entities.add(encoded); outgoing.put("block_entities", entities);
                try {
                    ForceFieldProtection.saving(new ChunkDataEvent.Save(chunk, helper.getLevel(), outgoing));
                    helper.fail("Oversized field root admitted a native chunk save");
                } catch (IllegalStateException denied) {
                    helper.assertTrue(chunk.isUnsaved(), "Save refusal erased the dirty flag");
                }
                chunk.setUnsaved(false);
                try {
                    GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(chunk, helper.getLevel(), new CompoundTag()));
                    helper.fail("Outgoing BE disappearance erased the native denial");
                } catch (IllegalStateException denied) {
                    helper.assertTrue(chunk.isUnsaved(), "Empty outgoing retry erased dirty state");
                }
                GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
            }
        } finally { GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos()); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void refusedVanillaDestroyBlockDoesNotMintProjectorLoot(GameTestHelper helper) {
        try (var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST)) {
            java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> veto = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.identity.source())) { event.setCanceled(true); }
            };
            var box = new net.minecraft.world.phys.AABB(f.identity.source()).inflate(2);
            int before = f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                    false, net.minecraftforge.event.level.BlockEvent.BreakEvent.class, veto);
            try { f.level.destroyBlock(f.identity.source(), true); }
            finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto); }
            helper.assertTrue(f.level.getBlockEntity(f.identity.source()) == f.source
                    && f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size() == before,
                    "Vanilla's pre-removal loot minted a duplicate projector after refusal");
            var state = f.source.getBlockState();
            ForceFieldProtection.physical(f.level, f.identity.source(), () -> f.level.removeBlock(f.identity.source(), false));
            var loot = net.minecraft.world.level.block.Block.getDrops(state, f.level, f.identity.source(), f.source);
            helper.assertTrue(loot.size() == 1 && loot.get(0).getItem() ==
                    io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.FORCE_FIELD_PROJECTOR.get(),
                    "Successfully removed projector lost its ordinary loot");
        }
        helper.succeed();
    }
    private ForceFieldSaveGameTests() { }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void nativeWorldDestructionDropsExactlyOneOnlyAfterSuccessfulRemoval(GameTestHelper helper) {
        for (boolean refused : new boolean[]{true, false}) {
            try (var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST)) {
                var box = new net.minecraft.world.phys.AABB(f.identity.source()).inflate(2);
                var old = f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                        .map(net.minecraft.world.entity.item.ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent> veto = event -> {
                    if (refused && event.getLevel() == f.level && event.getPos().equals(f.identity.source())) { event.setCanceled(true); }
                };
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
                        false, net.minecraftforge.event.level.BlockEvent.BreakEvent.class, veto);
                try {
                    f.level.destroyBlock(f.identity.source(), true);
                    var drops = f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                            .filter(entity -> !old.contains(entity.getUUID())).toList();
                    helper.assertTrue(f.level.getBlockState(f.identity.source()).is(ModBlocks.FORCE_FIELD_PROJECTOR.get()) == refused,
                            "Native destruction did not conserve the refused/successful source block");
                    helper.assertTrue(refused ? drops.isEmpty() : drops.size() == 1 && drops.get(0).getItem().getCount() == 1
                            && drops.get(0).getItem().is(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.FORCE_FIELD_PROJECTOR.get()),
                            "Native destruction did not emit exactly one item on success and zero on refusal");
                    if (!refused) {
                        helper.assertTrue(!f.level.destroyBlock(f.identity.source(), true)
                                && f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).size() == old.size() + 1,
                                "Repeated native destruction duplicated stock");
                    }
                } finally {
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);
                    f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                            .filter(entity -> !old.contains(entity.getUUID())).forEach(net.minecraft.world.entity.Entity::discard);
                }
            }
        }
        helper.succeed();
    }
}
