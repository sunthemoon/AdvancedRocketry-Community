package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** ADR-066 leaf A: native tick/travel/entity-NBT cases and separately identified controller fixtures. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LivingGravityGameTests {
    private static final String BATCH = "living_gravity";
    private static final UUID FOREIGN_ID = UUID.fromString("d4b9983d-9dc3-4e88-9ee6-d2dd95fa693b");

    private LivingGravityGameTests() {
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeMobTickUsesOnlyItsActualLevelProfile(GameTestHelper helper) {
        List<ResourceKey<Level>> levels = List.of(Level.OVERWORLD, CelestialIds.MOON_LEVEL,
                PlanetaryContent.level(PlanetaryContent.MARS), PlanetaryContent.level(PlanetaryContent.VENUS),
                CelestialIds.SPACE_LEVEL);
        double[] factors = {1.0D, 0.165D, 0.38D, 0.90D, 0.0D};
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            for (int i = 0; i < levels.size(); i++) {
                Cow cow = cow(level(helper, levels.get(i)), airPosition(helper));
                try {
                    AttributeInstance gravity = gravity(cow);
                    double base = gravity.getBaseValue();
                    // Native LivingEntity.tick dispatches Forge's hook to the production-registered listener.
                    cow.tick();
                    expect(helper, gravity.getBaseValue(), base, "Native tick changed base gravity");
                    expect(helper, gravity.getValue(), base * factors[i], "Wrong native mob gravity in " + levels.get(i));
                    helper.assertTrue((gravity.getModifier(CelestialGravityController.MODIFIER_ID) == null)
                            == (factors[i] == 1.0D), "Wrong owned modifier presence");
                } finally {
                    cow.discard();
                }
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeTravelUsesLevelGravityAndHonorsNoGravity(GameTestHelper helper) {
        Cow earth = cow(level(helper, Level.OVERWORLD), airPosition(helper));
        Cow moon = cow(level(helper, CelestialIds.MOON_LEVEL), airPosition(helper));
        Cow space = cow(level(helper, CelestialIds.SPACE_LEVEL), airPosition(helper));
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            earth.tick();
            moon.tick();
            space.tick();
            double earthDrop = travelDrop(helper, earth);
            double moonDrop = travelDrop(helper, moon);
            double spaceDrop = travelDrop(helper, space);
            helper.assertTrue(earthDrop > 0.0D && moonDrop > 0.0D, "Native travel did not fall in air");
            expect(helper, moonDrop / earthDrop, 0.165D, "Native travel ignored the Moon gravity ratio");
            expect(helper, spaceDrop, 0.0D, "Native zero-gravity travel fell");
            moon.setNoGravity(true);
            expect(helper, travelDrop(helper, moon), 0.0D, "Native no-gravity flag was overridden");
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            earth.discard();
            moon.discard();
            space.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void productionSwitchRemovesOnlyMobModifierAndLeavesLegacyPlayerGravity(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        Cow cow = cow(moon, airPosition(helper));
        FakePlayer player = player(moon, airPosition(helper));
        AttributeInstance gravity = gravity(cow);
        gravity.setBaseValue(0.1D);
        AttributeModifier foreign = foreignModifier();
        gravity.addPermanentModifier(foreign);
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            cow.tick();
            expect(helper, gravity.getValue(), 0.1D * 1.25D * 0.165D, "Enabled mob gravity differs");
            SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, false);
            cow.tick();
            helper.assertTrue(gravity.getModifier(CelestialGravityController.MODIFIER_ID) == null,
                    "Disabled tick left the owned mob modifier");
            helper.assertTrue(gravity.getModifier(FOREIGN_ID) == foreign, "Disabled tick changed a foreign modifier");
            expect(helper, gravity.getBaseValue(), 0.1D, "Disabled tick changed the base");
            expect(helper, gravity.getValue(), 0.125D, "Disabled tick did not restore foreign-only gravity");
            // FakePlayer.tick is not a native mob tick; this checks the registered player event listener explicitly.
            registeredPlayerTick(player);
            expect(helper, gravity(player).getValue(), gravity(player).getBaseValue() * 0.165D,
                    "The new switch disabled legacy player gravity");
            SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
            cow.tick();
            expect(helper, gravity.getValue(), 0.1D * 1.25D * 0.165D, "Re-enabled tick did not recompute gravity");
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            cow.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeEntityNbtDropsOwnedPermanentResidueAndRecomputesOnReload(GameTestHelper helper) {
        Cow original = cow(level(helper, CelestialIds.MOON_LEVEL), airPosition(helper));
        List<Cow> entities = new ArrayList<>();
        entities.add(original);
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            AttributeInstance gravity = gravity(original);
            gravity.setBaseValue(0.1D);
            gravity.addPermanentModifier(foreignModifier());
            gravity.addPermanentModifier(new AttributeModifier(CelestialGravityController.MODIFIER_ID,
                    "Owned permanent residue fixture", 0.165D - 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            original.tick();
            AttributeModifier transientModifier = gravity.getModifier(CelestialGravityController.MODIFIER_ID);
            helper.assertTrue(transientModifier != null, "Tick did not retain an owned transient modifier");
            helper.assertTrue(!gravity.removePermanentModifier(CelestialGravityController.MODIFIER_ID)
                            && gravity.getModifier(CelestialGravityController.MODIFIER_ID) == transientModifier,
                    "Matching owned permanent residue was not converted to transient");
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            assertSavedGravity(helper, saved);

            List<ResourceKey<Level>> destinations = List.of(CelestialIds.MOON_LEVEL,
                    PlanetaryContent.level(PlanetaryContent.MARS), Level.OVERWORLD);
            double[] factors = {0.165D, 0.38D, 1.0D};
            for (int i = 0; i < destinations.size(); i++) {
                Cow restored = cow(level(helper, destinations.get(i)), airPosition(helper));
                entities.add(restored);
                restored.load(saved.copy());
                AttributeInstance loaded = gravity(restored);
                helper.assertTrue(loaded.getModifier(CelestialGravityController.MODIFIER_ID) == null,
                        "Owned transient survived actual entity NBT reload");
                AttributeModifier foreign = loaded.getModifier(FOREIGN_ID);
                helper.assertTrue(foreign != null && foreign.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL,
                        "Foreign modifier was lost or changed on reload");
                expect(helper, foreign.getAmount(), 0.25D, "Foreign modifier amount changed on reload");
                expect(helper, loaded.getBaseValue(), 0.1D, "Entity reload changed the base");
                restored.tick();
                expect(helper, loaded.getValue(), 0.125D * factors[i], "Reloaded native tick used a stale Level factor");
                assertSavedGravity(helper, restored.saveWithoutId(new CompoundTag()));
            }
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            entities.forEach(Cow::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void controllerFixtureNeverQueriesPlayerLayersForMobsAndReevaluatesCurrentState(GameTestHelper helper) {
        Cow cow = cow(level(helper, CelestialIds.MOON_LEVEL), airPosition(helper));
        CelestialCatalogManager catalogs = catalogs(0.165D);
        AtomicBoolean enabled = new AtomicBoolean(true);
        CelestialGravityController controller = new CelestialGravityController(new CelestialEnvironmentService(catalogs),
                (world, pos) -> { throw new AssertionError("Mob queried a station-position layer"); },
                player -> { throw new AssertionError("Mob queried a player field layer"); }, enabled::get);
        try {
            controllerTick(controller, cow);
            expect(helper, gravity(cow).getValue(), gravity(cow).getBaseValue() * 0.165D, "Fixture ignored Level gravity");
            AttributeModifier foreign = foreignModifier();
            gravity(cow).addPermanentModifier(foreign);
            enabled.set(false);
            controllerTick(controller, cow);
            helper.assertTrue(gravity(cow).getModifier(CelestialGravityController.MODIFIER_ID) == null
                            && gravity(cow).getModifier(FOREIGN_ID) == foreign, "Fixture switch changed foreign state");
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies(0.25D))), "Fixture reload failed");
            enabled.set(true);
            controllerTick(controller, cow);
            expect(helper, gravity(cow).getValue(), gravity(cow).getBaseValue() * 1.25D * 0.25D,
                    "Re-enable used a cached factor");
            catalogs.clear();
            controllerTick(controller, cow);
            helper.assertTrue(gravity(cow).getModifier(CelestialGravityController.MODIFIER_ID) == null,
                    "An unavailable profile retained owned gravity");
            expect(helper, gravity(cow).getValue(), gravity(cow).getBaseValue() * 1.25D, "Default-1 gravity differs");
            new CelestialGravityController(new CelestialEnvironmentService(catalogs(0.165D)))
                    .onLivingTick(new LivingEvent.LivingTickEvent(cow));
            expect(helper, gravity(cow).getValue(), gravity(cow).getBaseValue() * 1.25D * 0.165D,
                    "Legacy constructor did not default new mob gravity to enabled");
        } finally {
            cow.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void controllerFixtureRetainsPlayerPrecedenceInAllGameModes(GameTestHelper helper) {
        FakePlayer player = player(level(helper, CelestialIds.MOON_LEVEL), airPosition(helper));
        AtomicInteger fieldCalls = new AtomicInteger();
        AtomicInteger positionCalls = new AtomicInteger();
        double[] field = {0.5D};
        double[] position = {0.35D};
        CelestialGravityController controller = new CelestialGravityController(new CelestialEnvironmentService(catalogs(0.165D)),
                (world, pos) -> { positionCalls.incrementAndGet(); return optional(position[0]); },
                ignored -> { fieldCalls.incrementAndGet(); return optional(field[0]); },
                () -> { throw new AssertionError("A player queried the non-player switch"); });
        try {
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR)) {
                player.setGameMode(mode);
                field[0] = 0.5D;
                position[0] = 0.35D;
                int previousFields = fieldCalls.get();
                int previousPositions = positionCalls.get();
                controllerTick(controller, player);
                expect(helper, gravity(player).getValue(), gravity(player).getBaseValue() * 0.5D, "Player field precedence changed");
                helper.assertTrue(fieldCalls.get() == previousFields + 1 && positionCalls.get() == previousPositions,
                        "Player field did not short-circuit the position layer");
                field[0] = Double.NaN;
                controllerTick(controller, player);
                expect(helper, gravity(player).getValue(), gravity(player).getBaseValue() * 0.35D, "Player station precedence changed");
                position[0] = Double.NaN;
                controllerTick(controller, player);
                expect(helper, gravity(player).getValue(), gravity(player).getBaseValue() * 0.165D, "Player Level fallback changed");
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void controllerFixtureSkipsMissingAttributesWithoutQueryingOrAddingThem(GameTestHelper helper) {
        Cow missing = new Cow(EntityType.COW, level(helper, CelestialIds.MOON_LEVEL)) {
            @Override
            public AttributeInstance getAttribute(Attribute attribute) {
                return attribute == ForgeMod.ENTITY_GRAVITY.get() ? null : super.getAttribute(attribute);
            }
        };
        CelestialGravityController controller = new CelestialGravityController(new CelestialEnvironmentService(catalogs(0.165D)),
                (world, pos) -> { throw new AssertionError("Missing attribute queried a position layer"); },
                player -> { throw new AssertionError("Missing attribute queried a player layer"); },
                () -> { throw new AssertionError("Missing attribute queried the switch"); });
        try {
            // Bypass the masked entity getter: this fixture still has a real native backing attribute.
            var attributes = missing.getAttributes();
            AttributeInstance backing = attributes.getInstance(ForgeMod.ENTITY_GRAVITY.get());
            helper.assertTrue(backing != null, "Native backing gravity is missing from the fixture");
            helper.assertTrue(backing.getModifier(CelestialGravityController.MODIFIER_ID) == null,
                    "Missing-getter fixture already has an owned modifier");
            backing.setBaseValue(0.1D);
            backing.addPermanentModifier(foreignModifier());
            backing.addTransientModifier(new AttributeModifier(UUID.randomUUID(), "Foreign transient fixture",
                    0.01D, AttributeModifier.Operation.ADDITION));
            for (boolean ownedPresent : new boolean[]{false, true}) {
                if (ownedPresent) {
                    backing.addTransientModifier(new AttributeModifier(CelestialGravityController.MODIFIER_ID,
                            "Owned transient fixture", -0.25D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
                var modifiers = backing.getModifiers();
                var savedMap = attributes.save();
                controllerTick(controller, missing);
                helper.assertTrue(missing.getAttribute(ForgeMod.ENTITY_GRAVITY.get()) == null,
                        "Fixture no longer masks the entity gravity getter");
                helper.assertTrue(missing.getAttributes() == attributes
                                && attributes.getInstance(ForgeMod.ENTITY_GRAVITY.get()) == backing,
                        "Missing-getter tick replaced native attribute state");
                helper.assertTrue(backing.getModifiers().size() == modifiers.size()
                                && modifiers.stream().allMatch(modifier -> backing.getModifier(modifier.getId()) == modifier),
                        "Missing-getter tick added, removed or replaced a native modifier");
                helper.assertTrue(Double.compare(backing.getBaseValue(), 0.1D) == 0
                                && attributes.save().equals(savedMap),
                        "Missing-getter tick changed the native base or serialized attribute map");
            }
        } finally {
            missing.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeMobInsideAnActivePlayerFieldKeepsLevelGravity(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 14, 1));
        helper.assertTrue(world.getBlockState(pos).isAir(), "Field fixture position is occupied");
        ServerPlayer owner = null;
        Cow cow = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            world.setBlockAndUpdate(pos, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
            GravityFieldBlockEntity device = (GravityFieldBlockEntity) world.getBlockEntity(pos);
            helper.assertTrue(device != null, "Field fixture block entity is missing");
            owner = ConnectedTestPlayers.join(world.getServer(), UUID.randomUUID(), "livingField", world,
                    pos.east(2), new ArrayList<>());
            helper.assertTrue(device.assignOwner(owner.getUUID()), "Field fixture ownership failed");
            device.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
            helper.assertTrue(new GravityFieldMenu(3, owner.getInventory(), device)
                    .clickMenuButton(owner, GravityFieldMenu.BUTTON_START), "Field fixture start failed");
            GravityFieldBlockEntity.serverTick(world, pos, device.getBlockState(), device);
            helper.assertTrue(device.active(), "Field fixture did not activate: " + device.describe());
            cow = cow(world, pos.east(2));
            int chunks = world.getChunkSource().getLoadedChunksCount();
            Map<String, Integer> tickets = TicketCounts.near(world, new ChunkPos(pos), 2);
            registeredPlayerTick(owner);
            expect(helper, gravity(owner).getValue(), gravity(owner).getBaseValue() * 0.5D, "Active field did not affect its owner");
            cow.tick();
            expect(helper, gravity(cow).getValue(), gravity(cow).getBaseValue(), "Mob inherited a player-only field");
            helper.assertTrue(world.getChunkSource().getLoadedChunksCount() == chunks
                            && TicketCounts.near(world, new ChunkPos(pos), 2).equals(tickets),
                    "Tick routes changed loaded chunks or non-fixture tickets");
        } finally {
            SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            try {
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } finally {
                try {
                    if (owner != null) { world.getServer().getPlayerList().remove(owner); }
                } finally {
                    if (cow != null) { cow.discard(); }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void nativeMobInsideAStationKeepsSpaceLevelGravity(GameTestHelper helper) {
        ServerLevel space = level(helper, CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData data = StationRegistrySavedData.get(space.getServer());
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationState station = new StationCreationService(platforms, body -> true)
                .create(space.getServer(), UUID.randomUUID(), "Living gravity", CelestialIds.MOON_ID, false)
                .station().orElseThrow();
        Cow cow = null;
        FakePlayer player = null;
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, true);
        try {
            data.flush(space.getServer());
            StationState observed = data.find(station.stationId()).orElseThrow();
            helper.assertTrue(data.checkedSetGravity(space.getServer(), observed, 350)
                            == StationRegistrySavedData.CheckedUpdate.COMMITTED, "Station fixture gravity did not commit");
            BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y + 2, station.landingPad().z());
            cow = cow(space, pad);
            player = player(space, pad);
            registeredPlayerTick(player);
            expect(helper, gravity(player).getValue(), gravity(player).getBaseValue() * 0.35D,
                    "Registered player route did not resolve the station fixture");
            cow.tick();
            expect(helper, gravity(cow).getValue(), 0.0D, "Mob inherited the station's player-only gravity");
        } finally {
            try {
                if (cow != null) { cow.discard(); }
                if (player != null) { player.discard(); }
                data.delete(station.stationId());
                platforms.removeTemplate(space, station.cell());
                data.flush(space.getServer());
            } finally {
                SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
            }
        }
        helper.succeed();
    }

    private static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key);
        return level;
    }

    private static BlockPos airPosition(GameTestHelper helper) {
        BlockPos test = helper.absolutePos(new BlockPos(2, 2, 2));
        return new BlockPos(test.getX(), 200, test.getZ());
    }

    /** Native entities are not enrolled in world ticking; tick/travel calls below are synchronous native routes. */
    private static Cow cow(ServerLevel level, BlockPos position) {
        level.getChunkAt(position); // Explicit fixture setup, not production gravity loading.
        Cow cow = new Cow(EntityType.COW, level);
        cow.setNoAi(true);
        cow.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return cow;
    }

    private static FakePlayer player(ServerLevel level, BlockPos position) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "livingGravity"));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }

    private static AttributeInstance gravity(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity == null) { throw new AssertionError("Native fixture has no Forge gravity attribute"); }
        return gravity;
    }

    private static double travelDrop(GameTestHelper helper, Cow cow) {
        cow.setNoAi(false);
        cow.setPos(cow.getX(), 200.0D, cow.getZ());
        cow.setOnGround(false);
        cow.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(!cow.isInWater() && !cow.isInLava(), "Native travel fixture is not in air");
        double before = cow.getY();
        cow.travel(Vec3.ZERO);
        cow.travel(Vec3.ZERO);
        return before - cow.getY();
    }

    private static void registeredPlayerTick(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }

    private static void controllerTick(CelestialGravityController controller, LivingEntity entity) {
        controller.onLivingTick(new LivingEvent.LivingTickEvent(entity));
    }

    private static java.util.OptionalDouble optional(double value) {
        return Double.isNaN(value) ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(value);
    }

    private static AttributeModifier foreignModifier() {
        return new AttributeModifier(FOREIGN_ID, "Foreign permanent fixture", 0.25D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static CelestialCatalogManager catalogs(double moonGravity) {
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        if (!catalogs.applyCandidate(CelestialCatalog.create(bodies(moonGravity)))) {
            throw new AssertionError("Fixture catalog validation failed");
        }
        return catalogs;
    }

    private static List<CelestialBodyDefinition> bodies(double moonGravity) {
        return CelestialDefaults.definitions().stream().map(body -> !body.id().equals(CelestialIds.MOON_ID) ? body
                : new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), moonGravity,
                        body.atmosphere(), body.orbit(), body.visualProfile(), body.capabilities(), body.solarIntensity(),
                        body.radiation(), body.environmentEffects(), body.discoveryRequired())).toList();
    }

    private static void assertSavedGravity(GameTestHelper helper, CompoundTag entity) {
        String name = ForgeRegistries.ATTRIBUTES.getKey(ForgeMod.ENTITY_GRAVITY.get()).toString();
        int gravityEntries = 0;
        int foreignEntries = 0;
        for (Tag raw : entity.getList("Attributes", Tag.TAG_COMPOUND)) {
            CompoundTag attribute = (CompoundTag) raw;
            if (!attribute.getString("Name").equals(name)) { continue; }
            gravityEntries++;
            expect(helper, attribute.getDouble("Base"), 0.1D, "Entity save changed the registered base attribute");
            for (Tag modifierRaw : attribute.getList("Modifiers", Tag.TAG_COMPOUND)) {
                CompoundTag modifier = (CompoundTag) modifierRaw;
                helper.assertTrue(modifier.hasUUID("UUID"), "Saved modifier lacks a UUID");
                UUID id = modifier.getUUID("UUID");
                helper.assertTrue(!id.equals(CelestialGravityController.MODIFIER_ID), "Owned gravity persisted in entity NBT");
                if (id.equals(FOREIGN_ID)) { foreignEntries++; }
            }
        }
        helper.assertTrue(gravityEntries == 1 && foreignEntries == 1, "Entity save lost or duplicated foreign permanent gravity");
    }

    private static void expect(GameTestHelper helper, double actual, double expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-9D, message + ": " + actual + " != " + expected);
    }
}
