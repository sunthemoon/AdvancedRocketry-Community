package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native connected survival players, actual water and the registered life-support listener. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnderwaterBreathingGameTests {
    private static final String BATCH = "underwater_breathing";

    private UnderwaterBreathingGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void submergedSuitPaysOnceEveryTwentyTicksAndRestoresAir(GameTestHelper helper) {
        assertPaidCadence(helper, helper.getLevel());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void vacuumAndWaterUseOnlyOneOxygenDebitPerCadence(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        assertPaidCadence(helper, moon);
        helper.succeed();
    }

    private static void assertPaidCadence(GameTestHelper helper, ServerLevel level) {
        try (var test = new UnderwaterBreathingFixture(helper, level)) {
            ItemStack chest = test.suit(50);
            test.player.setAirSupply(60);
            for (int tick = 1; tick <= 40; tick++) {
                test.tick();
                helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 50 - tick / 20,
                        "Wrong suit debit at submerged tick " + tick);
                int expectedAir = tick < 20 ? 60 - tick : test.player.getMaxAirSupply() - 1 - tick % 20;
                helper.assertTrue(test.player.getAirSupply() == expectedAir,
                        "Air restored without payment or not restored on payment at tick " + tick
                                + ": " + test.player.getAirSupply() + " != " + expectedAir);
                helper.assertTrue(test.player.getHealth() == 20.0F, "Paid suit took damage at tick " + tick);
            }
        }
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void noSuitRetainsNativeDrowning(GameTestHelper helper) {
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            assertDrowning(helper, test);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void emptyTankRetainsNativeDrowning(GameTestHelper helper) {
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            ItemStack chest = test.suit(0);
            assertDrowning(helper, test);
            helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 0, "Empty suit acquired oxygen");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void unsupportedTankRetainsNativeDrowningAndItsOriginalData(GameTestHelper helper) {
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            ItemStack chest = test.suit(50);
            chest.getTag().getCompound("arce_space_suit_oxygen").putInt("schema_version", 2);
            chest.getTag().putString("marker", "retain");
            CompoundTag before = chest.getTag().copy();
            assertDrowning(helper, test);
            helper.assertTrue(before.equals(chest.getTag()), "Unsupported suit data was changed");
        }
        helper.succeed();
    }

    private static void assertDrowning(GameTestHelper helper, UnderwaterBreathingFixture test) {
        test.player.setAirSupply(0);
        for (int tick = 1; tick <= 20; tick++) {
            test.tick();
            helper.assertTrue(test.player.getAirSupply() == (tick == 20 ? 0 : -tick),
                    "Native air countdown changed at tick " + tick + ": " + test.player.getAirSupply());
        }
        helper.assertTrue(test.player.getHealth() == 18.0F && test.player.getLastDamageSource() != null
                && test.player.getLastDamageSource().is(DamageTypes.DROWN),
                "Native drowning did not deal exactly two health points: " + test.player.getHealth());
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void waterBreathingAloneAndWithASuitRemainNative(GameTestHelper helper) {
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            test.player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200));
            test.player.setAirSupply(17);
            for (int tick = 1; tick <= 20; tick++) {
                test.tick();
                helper.assertTrue(test.player.getAirSupply() == 17 && test.player.getHealth() == 20.0F,
                        "Water breathing without a suit changed vanilla air or damage");
            }
            ItemStack chest = test.suit(50);
            for (int tick = 1; tick <= 40; tick++) {
                test.tick();
                helper.assertTrue(test.player.getAirSupply() == 17 && SpaceSuitOxygen.read(chest).oxygenUnits() == 50,
                        "Native water breathing acquired suit air or spent oxygen");
            }
            helper.assertTrue(test.player.getEffect(MobEffects.WATER_BREATHING).getDuration() == 140,
                    "Water breathing's native duration changed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void equipmentSwitchOffDoesNotGrantAirOrDebitForWater(GameTestHelper helper) {
        SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            ItemStack chest = test.suit(50);
            assertDrowning(helper, test);
            helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 50, "Disabled underwater support spent oxygen");
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void conduitsRespirationAndTurtleHelmetsRemainNative(GameTestHelper helper) {
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            ItemStack chest = test.suit(50);
            test.player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 200));
            test.player.setAirSupply(17);
            for (int tick = 0; tick < 40; tick++) { test.tick(); }
            helper.assertTrue(test.player.getAirSupply() == 17 && SpaceSuitOxygen.read(chest).oxygenUnits() == 50,
                    "Conduit power spent suit oxygen or changed native air");
        }
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            ItemStack helmet = new ItemStack(Items.TURTLE_HELMET);
            helmet.enchant(Enchantments.RESPIRATION, 3);
            test.player.setItemSlot(EquipmentSlot.HEAD, helmet);
            // Native turtle helmet inventory tick above water creates its water-breathing effect.
            test.player.setPos(test.pool.getX() + 0.5D, test.pool.getY() + 4.0D, test.pool.getZ() + 0.5D);
            test.player.baseTick();
            test.player.doTick();
            helper.assertTrue(test.player.hasEffect(MobEffects.WATER_BREATHING), "Native turtle helmet effect is absent");
            test.submerge();
            test.player.setAirSupply(17);
            for (int tick = 0; tick < 40; tick++) { test.tick(); }
            helper.assertTrue(test.player.getAirSupply() == 17 && test.player.getHealth() == 20.0F,
                    "Turtle helmet's native effect changed air or health");
            test.player.removeEffect(MobEffects.WATER_BREATHING);
            test.player.setAirSupply(60);
            for (int tick = 0; tick < 40; tick++) {
                int before = test.player.getAirSupply();
                test.tick();
                int after = test.player.getAirSupply();
                helper.assertTrue(after == before || after == before - 1, "Respiration's native consumption changed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void aquaticNonPlayersReceiveNoSuitAirRestoration(GameTestHelper helper) {
        try (var test = new UnderwaterBreathingFixture(helper, helper.getLevel())) {
            var fish = EntityType.COD.create(test.level);
            helper.assertTrue(fish != null, "Native cod is unavailable");
            fish.setPos(test.player.position());
            fish.setNoAi(true);
            fish.setNoGravity(true);
            fish.baseTick();
            fish.setAirSupply(60);
            for (int tick = 0; tick < 40; tick++) {
                fish.tick();
                // WaterAnimal.handleAirSupply refills to 300 every native submerged tick.
                helper.assertTrue(fish.getAirSupply() == 300 && fish.getHealth() == fish.getMaxHealth(),
                        "Native aquatic air refill or health changed");
            }
            fish.discard();
            var pig = EntityType.PIG.create(test.level);
            helper.assertTrue(pig != null, "Native pig is unavailable");
            pig.setPos(test.player.position());
            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
            pig.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_SUIT_LEGGINGS.get()));
            pig.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()));
            ItemStack chest = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
            helper.assertTrue(SpaceSuitOxygen.set(chest, 50), "Could not seed non-player suit oxygen");
            pig.setItemSlot(EquipmentSlot.CHEST, chest);
            pig.baseTick();
            pig.setAirSupply(0);
            for (int tick = 1; tick <= 20; tick++) {
                pig.tick();
                helper.assertTrue(pig.getAirSupply() == (tick == 20 ? 0 : -tick),
                        "A non-player wearing a suit received air at tick " + tick);
                helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 50,
                        "A non-player's suit oxygen was debited");
            }
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth() - 2.0F && pig.getLastDamageSource() != null
                    && pig.getLastDamageSource().is(DamageTypes.DROWN), "Suited non-player did not drown natively");
            pig.discard();
        }
        helper.succeed();
    }
}
