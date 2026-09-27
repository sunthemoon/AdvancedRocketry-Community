package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryClimateGameTests {
    private PlanetaryClimateGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void breathableHostileRoomConsumesFiniteSupplyAndFailsClosed(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos localVent = new BlockPos(3, 1, 3);
        OxygenVentBlockEntity vent = room(helper, localVent);
        BlockPos pos = helper.absolutePos(localVent);
        BlockPos eye = pos.above(2);
        try (var test = new PlanetaryExposureGameTests.Harness(level)) {
            test.replace(hostileEarth());
            test.player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            int oxygen = vent.oxygenUnits();
            int energy = vent.energyStored();
            for (int i = 0; i < 20; i++) {
                test.atmosphere.observeVent(level, vent);
                test.atmosphere.tick(level.getServer());
                test.life.tickPlayer(test.player);
            }
            helper.assertTrue(test.atmosphere.controlledAt(level, eye) && test.player.attempts == 0,
                    "Supplied breathable hostile room did not protect");
            helper.assertTrue(vent.oxygenUnits() == oxygen - 1
                    && vent.energyStored() == energy - 20 * AtmosphereLimits.VENT_ENERGY_PER_TICK,
                    "Climate room did not use exactly the existing finite supply cadence");
            helper.assertTrue(test.atmosphere.metrics(level.dimension()).orElseThrow().lastTickInspections()
                    <= AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK, "Climate scan exceeded budget");
            BlockPos roof = pos.above(3);
            level.setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
            test.atmosphere.markDirty(level, roof);
            helper.assertTrue(!test.atmosphere.controlledAt(level, eye)
                    && test.atmosphere.breathabilityAt(level, eye) == BreathabilityState.BREATHABLE,
                    "Ambient breathable air was confused with a controlled room");
            for (int i = 0; i < 20; i++) { test.life.tickPlayer(test.player); }
            helper.assertTrue(test.player.attempts == 1 && test.player.lastDamage.endsWith("planetary_pressure")
                    && test.player.getHealth() == 18, "Breathable but uncontrolled exposure was not applied: attempts="
                            + test.player.attempts + " health=" + test.player.getHealth() + " source=" + test.player.lastDamage);
            level.setBlockAndUpdate(roof, Blocks.IRON_BLOCK.defaultBlockState());
            test.atmosphere.markDirty(level, roof);
            test.atmosphere.observeVent(level, vent);
            test.atmosphere.tick(level.getServer());
            helper.assertTrue(test.atmosphere.controlledAt(level, eye), "Repaired room did not recover");
            var power = vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(IllegalStateException::new);
            helper.assertTrue(vent.energyStored() == AtmosphereLimits.VENT_ENERGY_PER_TICK,
                    "Fixture did not reach the last finite supply interval");
            test.atmosphere.observeVent(level, vent);
            test.atmosphere.tick(level.getServer());
            helper.assertTrue(vent.energyStored() == 0 && vent.status() == VentOperatingStatus.ACTIVE
                    && !test.atmosphere.controlledAt(level, eye),
                    "Exhausted power left stale protection after its last debit");
            power.receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
            test.atmosphere.observeVent(level, vent);
            test.atmosphere.tick(level.getServer());
            helper.assertTrue(test.atmosphere.controlledAt(level, eye), "Refilled vent did not recover before unload");
            test.atmosphere.onChunkUnload(level, pos.getX() >> 4, pos.getZ() >> 4);
            helper.assertTrue(!test.atmosphere.controlledAt(level, eye), "Unloaded vent retained climate authority");
        } finally {
            helper.setBlock(localVent, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void legacyBreathableEarthStillSkipsVentScanningAndConsumption(GameTestHelper helper) {
        BlockPos localVent = new BlockPos(3, 1, 3);
        OxygenVentBlockEntity vent = room(helper, localVent);
        try (var test = new PlanetaryExposureGameTests.Harness(helper.getLevel())) {
            int energy = vent.energyStored();
            int oxygen = vent.oxygenUnits();
            for (int i = 0; i < 20; i++) {
                test.atmosphere.observeVent(helper.getLevel(), vent);
                test.atmosphere.tick(helper.getLevel().getServer());
            }
            helper.assertTrue(vent.energyStored() == energy && vent.oxygenUnits() == oxygen
                    && test.atmosphere.metrics(helper.getLevel().dimension()).orElseThrow().totalInspections() == 0,
                    "Legacy breathable shortcut consumed resources or scanned");
            helper.assertTrue(!test.atmosphere.controlledAt(helper.getLevel(), helper.absolutePos(localVent.above(2))),
                    "Ambient air manufactured controlled-volume authority");
        } finally { helper.setBlock(localVent, Blocks.AIR); }
        helper.succeed();
    }

    private static CelestialBodyDefinition hostileEarth() {
        var earth = CelestialDefaults.definitions().stream().filter(CelestialBodyDefinition::isRoot).findFirst().orElseThrow();
        return new CelestialBodyDefinition(earth.id(), earth.parentId(), earth.levelKey(), earth.gravityMultiplier(),
                new AtmosphereDefinition(9.2, true, 737, earth.atmosphere().profile()), earth.orbit(),
                earth.visualProfile(), earth.capabilities(), 1, 0, true);
    }

    private static OxygenVentBlockEntity room(GameTestHelper helper, BlockPos ventPos) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean wall = x != 0 || z != 0 || y == 0 || y == 3;
                    helper.setBlock(ventPos.offset(x, y, z), wall ? Blocks.IRON_BLOCK : Blocks.AIR);
                }
            }
        }
        helper.setBlock(ventPos, ModBlocks.OXYGEN_VENT.get());
        var vent = (OxygenVentBlockEntity) helper.getBlockEntity(ventPos);
        var items = vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false).isEmpty(), "Oxygen insert rejected");
        OxygenVentBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(ventPos), vent.getBlockState(), vent);
        vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(IllegalStateException::new)
                .receiveEnergy(22 * AtmosphereLimits.VENT_ENERGY_PER_TICK, false);
        return vent;
    }
}
