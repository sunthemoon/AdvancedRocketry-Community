package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserGunGameTests {
    private LaserGunGameTests() { }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void nativeUseTwentyTicksBreaksOnceAndCooldownDoesNotReplay(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            helper.assertTrue(f.tool().getMaxDamage() == 512 && f.tool().getMaxStackSize() == 1
                    && f.tool().getUseDuration() == 72000 && f.gun.getTier() == net.minecraft.world.item.Tiers.DIAMOND
                    && f.tool().isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
                    && !f.tool().isCorrectToolForDrops(Blocks.DIRT.defaultBlockState()), "Registered tool qualification/use bounds differ");
            f.start(); f.ticks(19); f.unchanged(Blocks.STONE, 0);
            f.ticks(1);
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().getDamageValue() == 1
                    && f.drops().size() == 1 && f.drops().get(0).getItem().is(Items.COBBLESTONE), "Native loot/wear differs");
            f.drops().forEach(net.minecraft.world.entity.Entity::discard);
            f.set(Blocks.STONE); f.ticks(5); f.unchanged(Blocks.STONE, 1);
            f.ticks(19); f.unchanged(Blocks.STONE, 1); f.ticks(1);
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().getDamageValue() == 2
                    && f.drops().size() == 1, "Cooldown replayed or new mining did not complete");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void zeroHardnessBreaksInOneTickAndConsumesLastDurability(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            f.tool().setDamageValue(511);
            f.set(Blocks.TNT); f.start(); f.ticks(1);
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().isEmpty()
                    && f.drops().size() == 1 && f.drops().get(0).getItem().is(Items.TNT), "Zero-hardness/last-use boundary differs");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void bedrockAndUnsupportedBlockEntitiesRefuse(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            f.set(Blocks.BEDROCK); f.start(); f.ticks(25); f.unchanged(Blocks.BEDROCK, 0);
            f.set(Blocks.CHEST); f.ticks(25); f.unchanged(Blocks.CHEST, 0);
            // Remove only this test-created empty block entity before restoring the BE-free owned cells.
            f.set(Blocks.AIR);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void cancelledNativeBreakLeavesExactToolAndTarget(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            Consumer<BlockEvent.BreakEvent> veto = event -> {
                if (event.getPlayer() == f.player && event.getPos().equals(f.target)) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, veto);
            CompoundTag before = f.tool().save(new CompoundTag());
            try {
                f.start(); f.ticks(20); f.unchanged(Blocks.STONE, 0);
                helper.assertTrue(f.tool().save(new CompoundTag()).equals(before), "Canceled event mutated tool NBT");
            } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
            f.ticks(25);
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().getDamageValue() == 1,
                    "Native admitted control failed after veto removal");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void adr054ZoneAndExtensionCancellationRefuse(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            var service = EndgameRuntime.operational().orElseThrow();
            String name = "w20_" + UUID.randomUUID().toString().substring(0, 8);
            var zone = ProtectedZone.of(name, f.level.dimension().location(), f.target.getX(), f.target.getZ(),
                    f.target.getX(), f.target.getZ(), List.of());
            helper.assertTrue(service.coalesced(root -> root.addZone(zone, service.settings().zones()))
                    == io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode.OK, "Zone fixture refused");
            try { f.start(); f.ticks(20); f.unchanged(Blocks.STONE, 0); }
            finally { service.coalesced(root -> root.removeZone(name)); }
            Consumer<EndgameEffectEvent> veto = event -> {
                if (event.systemId().equals(ModIdentity.id("basic_laser_gun")) && event.ownerId().equals(f.player.getUUID())) {
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EndgameEffectEvent.class, veto);
            try { f.ticks(25); f.unchanged(Blocks.STONE, 0); }
            finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void installedStationBuildPolicyRefusesAnUnownedSpaceTarget(GameTestHelper helper) {
        var space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space unavailable");
        var original = StationRegistrySavedData.get(space.getServer());
        CompoundTag bad = new CompoundTag(); bad.putInt("schema_version", 4);
        var blocked = StationRegistrySavedData.load(bad);
        try (var f = new LaserGunFixture(helper, space)) {
            space.getServer().overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, blocked);
            try { f.start(); f.ticks(20); f.unchanged(Blocks.STONE, 0); }
            finally { space.getServer().overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, original); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void targetStateStackAndUseEndResetContinuousWork(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            f.start(); f.ticks(19);
            f.set(Blocks.AIR);
            f.level.setBlock(f.target.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            f.ticks(1);
            helper.assertTrue(f.level.getBlockState(f.target.east()).is(Blocks.STONE) && f.tool().getDamageValue() == 0,
                    "Position change retained old work");
            f.level.setBlock(f.target.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            f.set(Blocks.DIRT); f.ticks(1); f.unchanged(Blocks.DIRT, 0);
            f.set(Blocks.STONE); f.ticks(19); f.unchanged(Blocks.STONE, 0);
            f.player.releaseUsingItem(); f.start(); f.ticks(19); f.unchanged(Blocks.STONE, 0);
            f.player.stopUsingItem(); f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.gun));
            f.start(); f.ticks(19); f.unchanged(Blocks.STONE, 0); f.ticks(1);
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().getDamageValue() == 1, "Reset control never completed");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void entityTakesOnePointAndOccludesTheBlockEvenWhenAttackIsCancelled(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            var cow = EntityType.COW.create(f.level);
            helper.assertTrue(cow != null, "Cow unavailable");
            cow.setNoAi(true); cow.setNoGravity(true);
            cow.setPos(f.target.getX() - 1, f.target.getY(), f.target.getZ() + 0.5);
            f.level.addFreshEntity(cow);
            try {
                float health = cow.getHealth(); f.start(); f.ticks(1);
                helper.assertTrue(cow.getHealth() == health - 1, "Entity branch did not deal exactly one point");
                f.unchanged(Blocks.STONE, 0);
                Consumer<AttackEntityEvent> veto = event -> { if (event.getEntity() == f.player) { event.setCanceled(true); } };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, AttackEntityEvent.class, veto);
                try { f.ticks(25); f.unchanged(Blocks.STONE, 0); helper.assertTrue(cow.getHealth() == health - 1, "Canceled attack damaged entity"); }
                finally { MinecraftForge.EVENT_BUS.unregister(veto); }
            } finally { cow.discard(); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void disabledAndOffhandUsesRefuseAndLogoutClearsTracking(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            f.start(); f.ticks(19);
            helper.assertTrue(LaserGunRuntime.service().tracked() > 0, "No tracking fixture");
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try { f.ticks(1); f.unchanged(Blocks.STONE, 0); }
            finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
            f.player.stopUsingItem(); f.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(f.gun));
            helper.assertTrue(!f.gun.use(f.level, f.player, InteractionHand.OFF_HAND).getResult().consumesAction(), "Offhand use admitted");
            f.start(); f.ticks(1);
            int tracked = LaserGunRuntime.service().tracked();
            f.level.getServer().getPlayerList().remove(f.player);
            helper.assertTrue(LaserGunRuntime.service().tracked() == tracked - 1, "Logout retained laser tracking");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "laser_gun", timeoutTicks = 100)
    public static void rayStopsAtFirstUnloadedChunkWithoutLoadingIt(GameTestHelper helper) {
        try (var f = new LaserGunFixture(helper)) {
            int x = f.target.getX() >> 4;
            int z = f.target.getZ() >> 4;
            int edge = x;
            for (int count = 0; count < 256 && f.level.getChunkSource().getChunkNow(edge + 1, z) != null; count++) { edge++; }
            helper.assertTrue(f.level.getChunkSource().getChunkNow(edge, z) != null
                    && f.level.getChunkSource().getChunkNow(edge + 1, z) == null, "No loaded/unloaded boundary in bounded fixture search");
            // Do not teleport or add tickets at the boundary. The trace alone observes the remote loaded prefix.
            f.player.setPos(edge * 16 + 15.5, f.level.getMaxBuildHeight() - 5, z * 16 + 8.5);
            f.player.setYRot(-90); f.player.setXRot(0);
            int loaded = f.level.getChunkSource().getLoadedChunksCount();
            CompoundTag held = f.tool().save(new CompoundTag());
            helper.assertTrue(LoadedLaserTrace.trace(f.player) == null, "Ray traversed the unloaded boundary");
            helper.assertTrue(f.level.getChunkSource().getChunkNow(edge + 1, z) == null
                    && f.level.getChunkSource().getLoadedChunksCount() == loaded && held.equals(f.tool().save(new CompoundTag())),
                    "Trace loaded a chunk or changed the tool");
        }
        helper.succeed();
    }
}
