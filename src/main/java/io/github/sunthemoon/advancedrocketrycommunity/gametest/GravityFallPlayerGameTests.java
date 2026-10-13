package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.HEIGHT;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.MOON;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.assertOneScaling;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.clear;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.expect;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.heal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.landing;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.level;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.livingDamage;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.lost;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.registeredTick;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.scaled;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.serverFall;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.start;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.sturdy;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.travelUntilLanded;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.Fall;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GravityFallFixtures.FallProbe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.3 leaf B (W38) for players: connected survival players, after vanilla's 60-tick spawn immunity.
 * Landings use the server's own fall routine, {@code ServerPlayer.doCheckFallDamage}; vehicle and station cases call
 * {@code causeFallDamage} with the arguments vanilla passes.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityFallPlayerGameTests {
    private static final String BATCH = "gravity_fall_players";
    /** A newly joined player ignores damage for 60 ticks (ServerPlayer.spawnInvulnerableTime). */
    private static final int SPAWN_IMMUNITY = 65;

    private GravityFallPlayerGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void playerLandingOnTheMoonIsScaledOnceAgainstTheOverworld(GameTestHelper helper) {
        afterSpawnImmunity(helper, List.of("fallHome", "fallVisitor"), players -> {
            ServerLevel earth = helper.getLevel();
            ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
            ServerPlayer home = players.get(0);
            ServerPlayer visitor = players.get(1);
            BlockPos earthPad = null;
            BlockPos moonPad = null;
            try {
                earthPad = landing(helper, earth);
                moonPad = landing(helper, moon);
                arrive(home, earth, earthPad);
                arrive(visitor, moon, moonPad);
                expect(helper, CelestialGravityController.appliedMultiplier(home), 1.0D, "Overworld player has an ARCE factor");
                expect(helper, CelestialGravityController.appliedMultiplier(visitor), MOON, "Moon player lacks its factor");
                try (FallProbe probe = new FallProbe(home, visitor)) {
                    serverFall(home, earthPad);
                    serverFall(visitor, moonPad);
                    Fall earthFall = probe.only(helper, home, "Overworld player");
                    helper.assertTrue(earthFall.incoming() == HEIGHT && earthFall.outgoing() == HEIGHT,
                            "The Overworld player's fall changed: " + earthFall);
                    expect(helper, lost(home), livingDamage(HEIGHT), "Overworld player damage changed");
                    Fall moonFall = probe.only(helper, visitor, "Moon player");
                    helper.assertTrue(moonFall.incoming() == HEIGHT && moonFall.outgoing() == scaled(HEIGHT, MOON, 1),
                            "The Moon player's fall was not scaled once: " + moonFall);
                    assertOneScaling(helper, visitor, HEIGHT, MOON, "Moon player");

                    // Players keep their legacy gravity with the switch off, but their falls are not scaled.
                    SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, false);
                    arrive(visitor, moon, moonPad);
                    expect(helper, CelestialGravityController.appliedMultiplier(visitor), MOON, "Legacy player gravity changed");
                    probe.reset();
                    serverFall(visitor, moonPad);
                    helper.assertTrue(probe.only(helper, visitor, "Switched-off player").outgoing() == HEIGHT,
                            "The switch did not leave the player's fall unchanged");
                    expect(helper, lost(visitor), livingDamage(HEIGHT), "Switched-off player damage changed");
                }
            } finally {
                clear(earth, earthPad);
                clear(moon, moonPad);
            }
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void stationPlayerKeepsItsOwnFactorOnFootAndInANonLivingVehicle(GameTestHelper helper) {
        afterSpawnImmunity(helper, List.of("fallStation"), players -> {
            ServerPlayer player = players.get(0);
            ServerLevel space = level(helper, CelestialIds.SPACE_LEVEL);
            StationRegistrySavedData data = StationRegistrySavedData.get(space.getServer());
            StationPlatformGenerator platforms = new StationPlatformGenerator();
            StationState station = new StationCreationService(platforms, body -> true)
                    .create(space.getServer(), UUID.randomUUID(), "Gravity fall", CelestialIds.MOON_ID, false)
                    .station().orElseThrow();
            Minecart cart = null;
            try {
                data.flush(space.getServer());
                StationState observed = data.find(station.stationId()).orElseThrow();
                helper.assertTrue(data.checkedSetGravity(space.getServer(), observed, 350)
                        == StationRegistrySavedData.CheckedUpdate.COMMITTED, "Station fixture gravity did not commit");
                BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y + 2, station.landingPad().z());
                player.teleportTo(space, pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D, 0.0F, 0.0F);
                registeredTick(player);
                expect(helper, CelestialGravityController.appliedMultiplier(player), 0.35D, "Station factor not applied");
                heal(player);
                try (FallProbe probe = new FallProbe(player)) {
                    // As Block.fallOn calls it; the Space Level itself has factor 0, so 0.35 is the player's own layer.
                    player.causeFallDamage(HEIGHT, 1.0F, player.damageSources().fall());
                    helper.assertTrue(probe.only(helper, player, "Station player").outgoing() == scaled(HEIGHT, 0.35D, 1),
                            "The station player's fall did not use the station factor once");
                    assertOneScaling(helper, player, HEIGHT, 0.35D, "Station player");
                    cart = new Minecart(space, player.getX(), player.getY(), player.getZ());
                    helper.assertTrue(player.startRiding(cart, true), "Minecart fixture failed");
                    heal(player);
                    probe.reset();
                    cart.causeFallDamage(HEIGHT, 1.0F, cart.damageSources().fall());
                    Fall carried = probe.only(helper, player, "Station minecart passenger");
                    helper.assertTrue(carried.incoming() == HEIGHT && carried.outgoing() == scaled(HEIGHT, 0.35D, 1),
                            "A non-living vehicle did not leave the player's own factor: " + carried);
                    assertOneScaling(helper, player, HEIGHT, 0.35D, "Station minecart passenger");
                }
            } finally {
                try {
                    player.stopRiding();
                    if (cart != null) { cart.discard(); }
                    data.delete(station.stationId());
                    platforms.removeTemplate(space, station.cell());
                } finally {
                    data.flush(space.getServer());
                }
            }
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void fieldPlayerRidingALivingMountInheritsTheMountsLanding(GameTestHelper helper) {
        afterSpawnImmunity(helper, List.of("fallField"), players -> {
            ServerPlayer player = players.get(0);
            ServerLevel world = helper.getLevel();
            BlockPos device = helper.absolutePos(new BlockPos(1, 14, 1));
            helper.assertTrue(world.getBlockState(device).isAir(), "Field fixture position is occupied");
            BlockPos pad = null;
            List<Entity> entities = new ArrayList<>();
            try {
                world.setBlockAndUpdate(device, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
                GravityFieldBlockEntity field = (GravityFieldBlockEntity) world.getBlockEntity(device);
                helper.assertTrue(field != null && field.assignOwner(player.getUUID()), "Field fixture ownership failed");
                field.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
                player.teleportTo(world, device.getX() + 2.5D, device.getY(), device.getZ() + 0.5D, 0.0F, 0.0F);
                helper.assertTrue(new GravityFieldMenu(3, player.getInventory(), field)
                        .clickMenuButton(player, GravityFieldMenu.BUTTON_START), "Field fixture start failed");
                GravityFieldBlockEntity.serverTick(world, device, field.getBlockState(), field);
                helper.assertTrue(field.active(), "Field fixture did not activate: " + field.describe());
                registeredTick(player);
                expect(helper, CelestialGravityController.appliedMultiplier(player), 0.5D, "Field factor not applied");
                heal(player);

                pad = landing(helper, world);
                Pig pig = new Pig(EntityType.PIG, world);
                entities.add(pig);
                pig.setPos(start(pad));
                sturdy(pig);
                helper.assertTrue(player.startRiding(pig, true), "Pig fixture failed");
                try (FallProbe probe = new FallProbe(pig, player)) {
                    helper.assertTrue(travelUntilLanded(pig, start(pad)), "The pig's native fall did not land");
                    Fall mount = probe.only(helper, pig, "Overworld pig");
                    Fall rider = probe.only(helper, player, "Field player on the pig");
                    helper.assertTrue(mount.outgoing() == mount.incoming() && rider.incoming() == mount.outgoing()
                            && rider.outgoing() == rider.incoming(), "The rider did not inherit the mount's landing: "
                            + rider + " after " + mount);
                    int inherited = livingDamage(mount.incoming());
                    helper.assertTrue(inherited != livingDamage(scaled(mount.incoming(), 0.5D, 1)),
                            "Fixture cannot tell the rider's own factor from the mount's");
                    expect(helper, lost(player), inherited, "The rider's damage is not the mount's single landing");

                    player.stopRiding();
                    Minecart cart = new Minecart(world, player.getX(), player.getY(), player.getZ());
                    entities.add(cart);
                    helper.assertTrue(player.startRiding(cart, true), "Minecart fixture failed");
                    heal(player);
                    probe.reset();
                    cart.causeFallDamage(HEIGHT, 1.0F, cart.damageSources().fall());
                    helper.assertTrue(probe.only(helper, player, "Field player in a minecart").outgoing()
                            == scaled(HEIGHT, 0.5D, 1), "A non-living vehicle did not leave the field factor");
                    assertOneScaling(helper, player, HEIGHT, 0.5D, "Field player in a minecart");
                }
            } finally {
                player.stopRiding();
                entities.forEach(Entity::discard);
                clear(world, pad);
                world.setBlockAndUpdate(device, Blocks.AIR.defaultBlockState());
            }
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void paddedBootsStillCancelLowGravityLandings(GameTestHelper helper) {
        afterSpawnImmunity(helper, List.of("fallBoots"), players -> {
            ServerPlayer player = players.get(0);
            ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
            BlockPos pad = null;
            Minecart cart = null;
            try {
                pad = landing(helper, moon);
                arrive(player, moon, pad);
                expect(helper, CelestialGravityController.appliedMultiplier(player), MOON, "Moon player lacks its factor");
                try (FallProbe probe = new FallProbe(player)) {
                    serverFall(player, pad);
                    helper.assertTrue(!probe.only(helper, player, "Unprotected player").canceled(), "Control fall canceled");
                    assertOneScaling(helper, player, HEIGHT, MOON, "Unprotected player");

                    player.setItemSlot(EquipmentSlot.FEET, ArmorEquipment.install(new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()),
                            SuitEquipmentContent.modules().get("padded_landing_boots").get().getDefaultInstance()).armor());
                    for (boolean lowGravityOnly : new boolean[]{false, true}) {
                        SwitchOverrides.set(CommonConfig.LOW_GRAVITY_BOOTS, lowGravityOnly);
                        heal(player);
                        probe.reset();
                        serverFall(player, pad);
                        helper.assertTrue(probe.only(helper, player, "Padded player").canceled() && lost(player) == 0.0F,
                                "Padded boots did not cancel the landing, lowGravityBoots=" + lowGravityOnly);
                    }
                    cart = new Minecart(moon, player.getX(), player.getY(), player.getZ());
                    helper.assertTrue(player.startRiding(cart, true), "Minecart fixture failed");
                    heal(player);
                    probe.reset();
                    cart.causeFallDamage(HEIGHT, 1.0F, cart.damageSources().fall());
                    helper.assertTrue(probe.only(helper, player, "Padded passenger").canceled() && lost(player) == 0.0F,
                            "Padded boots did not cancel a passenger's fall");
                }
            } finally {
                SwitchOverrides.clear(CommonConfig.LOW_GRAVITY_BOOTS);
                player.stopRiding();
                if (cart != null) { cart.discard(); }
                clear(moon, pad);
            }
        });
    }

    /** Joins connected survival players now and runs {@code body} once spawn immunity is over, then removes them. */
    private static void afterSpawnImmunity(GameTestHelper helper, List<String> names, Consumer<List<ServerPlayer>> body) {
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> players = new ArrayList<>();
        for (String name : names) {
            ServerPlayer player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), name, level,
                    helper.absolutePos(new BlockPos(1, 2, 1)), new ArrayList<>());
            // The GameTest world defaults to creative, whose flight ability turns a fall into PlayerFlyableFallEvent.
            player.setGameMode(GameType.SURVIVAL);
            players.add(player);
        }
        helper.runAfterDelay(SPAWN_IMMUNITY, () -> {
            try {
                players.forEach(GravityFallFixtures::sturdy);
                SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
                body.accept(players);
                helper.succeed();
            } finally {
                SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
                players.forEach(player -> level.getServer().getPlayerList().remove(player));
            }
        });
    }

    /** Moves the player above the landing block and posts the registered tick, so leaf A applies that Level's factor. */
    private static void arrive(ServerPlayer player, ServerLevel level, BlockPos landing) {
        Vec3 top = start(landing);
        player.teleportTo(level, top.x, top.y, top.z, 0.0F, 0.0F);
        registeredTick(player);
        heal(player);
    }
}
