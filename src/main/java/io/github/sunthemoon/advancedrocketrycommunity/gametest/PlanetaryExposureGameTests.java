package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalExposure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalProtection;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.PlayerEnvironmentalService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryExposureGameTests {
    private PlanetaryExposureGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void registeredDamageUsesOneIntervalAndPlanetaryGravity(GameTestHelper helper) {
        for (var id : List.of(PlanetaryContent.MARS, PlanetaryContent.VENUS)) {
            ServerLevel level = helper.getLevel().getServer().getLevel(PlanetaryContent.level(id));
            helper.assertTrue(level != null, "Missing planet " + id);
            try (Harness test = new Harness(level)) {
                for (int i = 0; i < 19; i++) { test.environment.tick(test.player, false); }
                helper.assertTrue(test.player.attempts == 0 && test.player.getHealth() == 20, "Exposure damaged before its interval");
                test.environment.tick(test.player, false);
                helper.assertTrue(test.player.attempts == 1 && test.player.getHealth() == 18,
                        "Registered environmental damage did not apply exactly once: attempts="
                                + test.player.attempts + " health=" + test.player.getHealth() + " source=" + test.player.lastDamage);
                String expected = id.equals(PlanetaryContent.MARS) ? "cold" : "pressure";
                helper.assertTrue(test.player.lastDamage.equals(ModIdentity.MOD_ID + ".planetary_" + expected),
                        "Wrong prioritized damage source " + test.player.lastDamage);
                helper.assertTrue(test.warnings.size() == 1, "Unchanged exposure spammed warnings");
                new CelestialGravityController(test.profiles).onLivingTick(new LivingEvent.LivingTickEvent(test.player));
                var gravity = test.player.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
                double multiplier = id.equals(PlanetaryContent.MARS) ? .38 : .90;
                helper.assertTrue(gravity != null && Math.abs(gravity.getValue() - gravity.getBaseValue() * multiplier) < 1e-9,
                        "New surface did not use the existing gravity controller");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void passiveTagsDoNotGrantOxygenOrAcceptWrongSlots(GameTestHelper helper) {
        ServerLevel venus = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.VENUS));
        try (Harness test = new Harness(venus)) {
            suit(test.player);
            var protection = EnvironmentalProtection.read(test.player);
            helper.assertTrue(protection.thermal() && protection.pressure() && protection.solar(), "Generated suit tags not loaded");
            ItemStack chest = test.player.getItemBySlot(EquipmentSlot.CHEST);
            helper.assertTrue(SpaceSuitOxygen.set(chest, 5), "Could not seed suit oxygen");
            for (int i = 0; i < 20; i++) { test.life.tickPlayer(test.player); }
            helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 4 && test.player.getHealth() == 20,
                    "Environment protection duplicated oxygen debit or failed to protect");
            helper.assertTrue(SpaceSuitOxygen.set(chest, 0), "Could not exhaust oxygen");
            for (int i = 0; i < 20; i++) { test.life.tickPlayer(test.player); }
            helper.assertTrue(test.player.getHealth() == 18 && test.player.attempts == 1
                    && test.player.lastDamage.equals(ModIdentity.MOD_ID + ".vacuum"), "Passive tags supplied oxygen");
            test.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
            helper.assertTrue(EnvironmentalProtection.read(test.player).equals(EnvironmentalExposure.Protection.NONE), "Wrong slot protected");
            test.player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_BOOTS.get(), 2));
            helper.assertTrue(EnvironmentalProtection.read(test.player).equals(EnvironmentalExposure.Protection.NONE), "Count-two armor protected");
            test.player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            int before = test.player.attempts;
            for (int i = 0; i < 20; i++) { test.life.tickPlayer(test.player); }
            helper.assertTrue(test.player.attempts == before + 1 && test.player.lastDamage.endsWith(".vacuum"),
                    "Coincident environmental exposure stacked another damage attempt");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void exemptionsProtectionAndLifecycleResetExposure(GameTestHelper helper) {
        ServerLevel mars = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.MARS));
        try (Harness test = new Harness(mars)) {
            test.environment.tick(test.player, false);
            suit(test.player);
            helper.assertTrue(test.environment.tick(test.player, false).phase() == 0, "Complete protection retained phase");
            helper.assertTrue(test.warnings.size() == 2 && test.warnings.get(1).getString().isEmpty(), "Protection did not clear warning");
            test.player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
                test.player.setGameMode(mode);
                for (int i = 0; i < 20; i++) { helper.assertTrue(test.environment.tick(test.player, false).hazards() == 0, "Exempt mode exposed"); }
            }
            test.player.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < 19; i++) { test.environment.tick(test.player, false); }
            test.environment.remove(test.player.getUUID());
            helper.assertTrue(test.environment.tick(test.player, false).phase() == 1 && test.player.attempts == 0, "Logout retained phase");
            test.environment.clear();
            helper.assertTrue(test.environment.tick(test.player, false).phase() == 1, "Server cleanup retained phase");
            for (int i = 1; i < 19; i++) { test.environment.tick(test.player, false); }
            var replacement = new ProbePlayer(mars, test.player.getUUID());
            replacement.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(test.environment.tick(replacement, false).phase() == 1,
                    "Replacement player entity inherited exposure phase");
            test.player.setHealth(0);
            helper.assertTrue(test.environment.tick(test.player, false).hazards() == 0, "Dead player exposed");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void profileReloadAndLegacyLevelsDoNotRetainSurfaceExposure(GameTestHelper helper) {
        ServerLevel mars = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.MARS));
        try (Harness test = new Harness(mars)) {
            for (int i = 0; i < 19; i++) { test.environment.tick(test.player, false); }
            var body = PlanetaryContent.definitions().get(0);
            test.replace(new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), body.gravityMultiplier(),
                    body.atmosphere(), body.orbit(), body.visualProfile(), body.capabilities(), body.solarIntensity(), body.radiation(), false));
            helper.assertTrue(test.environment.tick(test.player, false).hazards() == 0 && test.player.attempts == 0, "Reload opt-out left old effects");
            test.replace(body);
            helper.assertTrue(test.environment.tick(test.player, false).phase() == 1, "Profile change retained elapsed exposure");
        }
        for (var key : List.of(net.minecraft.world.level.Level.OVERWORLD, CelestialIds.MOON_LEVEL, CelestialIds.SPACE_LEVEL)) {
            try (Harness test = new Harness(helper.getLevel().getServer().getLevel(key))) {
                for (int i = 0; i < 20; i++) { test.environment.tick(test.player, false); }
                helper.assertTrue(test.player.attempts == 0 && test.warnings.isEmpty(), "Legacy Level acquired a new hazard");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void ordinaryImmunityAndDamageCancellationRemainEffective(GameTestHelper helper) {
        ServerLevel mars = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.MARS));
        try (Harness test = new Harness(mars)) {
            test.player.immune = true;
            for (int i = 0; i < 20; i++) { test.environment.tick(test.player, false); }
            helper.assertTrue(test.player.attempts == 1 && test.player.getHealth() == 20, "Environmental source bypassed ordinary immunity");
            test.player.immune = false;
            test.player.cancel = true;
            for (int i = 0; i < 20; i++) { test.environment.tick(test.player, false); }
            helper.assertTrue(test.player.attempts == 2 && test.player.getHealth() == 20, "Rejected damage was applied anyway");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void newPlayerSpawnGraceIsNotBypassed(GameTestHelper helper) {
        ServerLevel mars = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.MARS));
        try (Harness test = new Harness(mars, false)) {
            for (int i = 0; i < 20; i++) { test.environment.tick(test.player, false); }
            helper.assertTrue(test.player.attempts == 1 && test.player.getHealth() == 20, "New exposure bypassed spawn protection");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", batch = "planetary_sunlight", timeoutTicks = 40)
    public static void sunlightUsesTimeLoadedColumnAndRoofWithoutLoadingFarChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long time = level.getDayTime();
        BlockPos column = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos eye = new BlockPos(column.getX(), level.getMaxBuildHeight() - 3, column.getZ());
        BlockPos roof = eye.above();
        var original = level.getBlockState(roof);
        Runnable cleanup = () -> {
            level.setBlockAndUpdate(roof, original);
            level.setDayTime(time); level.updateSkyBrightness();
        };
        try {
            level.setDayTime(6000); level.updateSkyBrightness();
            helper.assertTrue(PlayerEnvironmentalService.directSunlight(level, eye),
                    "Open day column not exposed: day=" + level.isDay() + " sky=" + level.canSeeSky(eye));
            level.setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        // Lighting is asynchronous, not guaranteed to publish after a fixed two ticks.
        // Observe the loaded column within the existing 40-tick test deadline.
        awaitRoofLighting(helper, level, eye, roof, original, cleanup);
    }

    private static void awaitRoofLighting(GameTestHelper helper, ServerLevel level, BlockPos eye, BlockPos roof,
            net.minecraft.world.level.block.state.BlockState original, Runnable cleanup) {
        helper.runAfterDelay(1, () -> {
            boolean pending = false;
            try {
                if (level.canSeeSky(eye) && helper.getTick() < 30) {
                    awaitRoofLighting(helper, level, eye, roof, original, cleanup);
                    pending = true;
                    return;
                }
                helper.assertTrue(level.getBlockState(roof).is(net.minecraft.world.level.block.Blocks.STONE)
                        && !level.canSeeSky(eye) && !PlayerEnvironmentalService.directSunlight(level, eye),
                        "Opaque roof did not shelter sunlight within the existing deadline");
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_SUNLIGHT_FIXTURE roof_observed_tick={} sky_light={}", helper.getTick(),
                        level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eye));
                level.setBlockAndUpdate(roof, original);
                level.setDayTime(18000); level.updateSkyBrightness();
                helper.assertTrue(!PlayerEnvironmentalService.directSunlight(level, eye), "Night counted as direct sunlight");
                BlockPos far = new BlockPos(25000000, eye.getY(), 25000000);
                level.setDayTime(6000); level.updateSkyBrightness();
                int chunks = level.getChunkSource().getLoadedChunksCount();
                helper.assertTrue(!level.hasChunkAt(far) && !PlayerEnvironmentalService.directSunlight(level, far)
                        && !level.hasChunkAt(far) && chunks == level.getChunkSource().getLoadedChunksCount(), "Sunlight query loaded a far column");
                helper.succeed();
            } finally { if (!pending) { cleanup.run(); } }
        });
    }

    static void suit(FakePlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_SUIT_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()));
    }

    static final class Harness implements AutoCloseable {
        final CelestialCatalogManager catalogs = new CelestialCatalogManager();
        final CelestialEnvironmentService profiles = new CelestialEnvironmentService(catalogs);
        final AtmosphereManager atmosphere = new AtmosphereManager(profiles);
        final List<Component> warnings = new ArrayList<>();
        final PlayerEnvironmentalService environment = new PlayerEnvironmentalService(atmosphere, (player, warning) -> warnings.add(warning));
        final PlayerLifeSupportService life = new PlayerLifeSupportService(atmosphere, (player, snapshot) -> { });
        final ProbePlayer player;
        private final List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());

        Harness(ServerLevel level) {
            this(level, true);
        }

        Harness(ServerLevel level, boolean pastSpawnGrace) {
            bodies.addAll(PlanetaryContent.definitions());
            if (!catalogs.applyCandidate(CelestialCatalog.create(bodies))) { throw new IllegalStateException("Bad fixture catalog"); }
            player = new ProbePlayer(level);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(0.5, 250, 0.5);
            player.setHealth(20);
            // FakePlayer.tick is a no-op, so its vanilla join grace never elapses.
            // Age only that fixture field; ordinary immunity and hurt logic remain active.
            if (pastSpawnGrace) {
                ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
            }
        }

        void replace(CelestialBodyDefinition body) {
            bodies.removeIf(existing -> existing.id().equals(body.id()));
            bodies.add(body);
            if (!catalogs.applyCandidate(CelestialCatalog.create(bodies))) { throw new IllegalStateException("Bad replacement catalog"); }
        }

        @Override public void close() { life.clear(); environment.clear(); atmosphere.clear(); catalogs.clear(); }
    }

    static final class ProbePlayer extends FakePlayer {
        int attempts;
        String lastDamage = "";
        boolean immune;
        boolean cancel;

        ProbePlayer(ServerLevel level) { this(level, UUID.randomUUID()); }
        ProbePlayer(ServerLevel level, UUID id) { super(level, new GameProfile(id, "PlanetExposure")); }
        @Override public boolean isInvulnerableTo(DamageSource source) { return immune; }
        @Override public boolean hurt(DamageSource source, float amount) {
            attempts++; lastDamage = source.getMsgId();
            return !cancel && super.hurt(source, amount);
        }
    }
}
