package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.Options;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.NaturalBedFixtures.Fixture;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.NaturalBedFixtures.Room;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W37B: actual connected players, loaded native holders and the installed atmosphere/sleep authority. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NaturalBedGameTests {
    private static final String TEMPLATE = "atmosphere_test";
    private static final String INTERRUPTED = "W37B injected earlier time-check listener interruption";
    private static final List<ResourceKey<Level>> HOSTS = List.of(CelestialIds.MOON_LEVEL, CelestialIds.SPACE_LEVEL,
            PlanetaryContent.level(PlanetaryContent.MARS), PlanetaryContent.level(PlanetaryContent.VENUS), ExoplanetContent.TAU_CETI_F_LEVEL,
            ExoplanetContent.TAU_CETI_G_LEVEL);

    private NaturalBedGameTests() { }

    @GameTest(template = TEMPLATE, batch = "natural_beds_matrix", timeoutTicks = 60)
    public static void sixNativeHostsSleepKeepAbsenceAndTuplesAndRefuseLate(GameTestHelper helper) {
        int[] state = {0, 0}; // Host and phase; occupancy changes must be rescanned before the next bed call.
        Tuple[] expected = {null};
        Zombie[] monsters = {null};
        withRoomSteps(helper, "W37BMatrix", fixture -> {
            ServerPlayer player = fixture.player;
            Room room = fixture.rooms.get(state[0]);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            ServerLevel level = room.level;
            runtimeType(helper, level);
            enter(player, room);
            if (state[1] == 0) {
                // Even a non-default absent tuple is kept bit-for-bit, not normalized to vanilla defaults.
                player.respawnDimension = CelestialIds.MOON_LEVEL;
                player.respawnPosition = null;
                player.respawnAngle = -0.0F;
                player.respawnForced = true;
                Tuple absent = Tuple.read(player);
                helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent() && player.isSleeping(),
                        "A supplied, permitted bed did not sleep in " + level.dimension().location());
                absent.assertSame(helper, player);
                player.stopSleepInBed(true, true);
                state[1] = 1;
                return false;
            }
            if (state[1] == 1) {
                BlockPos home = helper.absolutePos(new BlockPos(2, 1, 2));
                player.setRespawnPosition(Level.OVERWORLD, home, -72.25F, true, false);
                Tuple saved = Tuple.read(player);
                helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent(), "Second sleep failed");
                saved.assertSame(helper, player);
                player.stopSleepInBed(true, true);
                expected[0] = saved;
                state[1] = 2;
                return false;
            }
            Tuple saved = expected[0];
            if (state[1] == 2) {
                ServerPlayer sleeper = player;
                Consumer<SleepingTimeCheckEvent> day = event -> {
                    if (event.getEntity() == sleeper) { event.setResult(Event.Result.DENY); }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SleepingTimeCheckEvent.class, day);
                try {
                    helper.assertTrue(player.startSleepInBed(room.head()).left().orElse(null)
                                    == Player.BedSleepingProblem.NOT_POSSIBLE_NOW,
                            "The native post-setter daytime path did not refuse in " + level.dimension().location());
                    saved.assertSame(helper, player);
                } finally { MinecraftForge.EVENT_BUS.unregister(day); }

                Zombie monster = EntityType.ZOMBIE.create(level);
                helper.assertTrue(monster != null, "Zombie creation failed");
                monster.setNoAi(true);
                monster.moveTo(room.head().getX() + 0.5D, room.head().getY(), room.head().getZ() + 1.5D, 0, 0);
                level.addFreshEntity(monster);
                monsters[0] = monster;
                fixture.cleanup.add(monster::discard);
                state[1] = 3;
                return false;
            }
            Zombie monster = monsters[0];
            try {
                helper.assertTrue(player.startSleepInBed(room.head()).left().orElse(null)
                                == Player.BedSleepingProblem.NOT_SAFE,
                        "The survival monster path did not refuse in " + level.dimension().location());
                saved.assertSame(helper, player);
            } finally { monster.discard(); }

            // Native command, not a directly fabricated forced event.
            fixture.server.getCommands().performPrefixedCommand(fixture.server.createCommandSourceStack(),
                    "execute in " + level.dimension().location() + " run spawnpoint " + player.getGameProfile().getName()
                            + " " + room.head().getX() + " " + room.head().getY() + " " + room.head().getZ() + " 37.5");
            helper.assertTrue(player.isRespawnForced() && player.getRespawnDimension().equals(level.dimension())
                            && room.head().equals(player.getRespawnPosition()) && player.getRespawnAngle() == 37.5F,
                    "The admin spawnpoint was intercepted");
            player.setRespawnPosition(level.dimension(), room.head(), -0.0F, false, false);
            helper.assertTrue(!player.isRespawnForced() && room.head().equals(player.getRespawnPosition())
                            && Float.floatToRawIntBits(player.getRespawnAngle()) == 0x80000000,
                    "An unrelated same-tuple mod setter after return was intercepted");
            player.setRespawnPosition(Level.NETHER, null, 13.0F, true, false);
            helper.assertTrue(player.getRespawnPosition() == null && player.getRespawnDimension() == Level.OVERWORLD
                            && player.getRespawnAngle() == 0 && !player.isRespawnForced(),
                    "An unrelated null setter did not retain vanilla's clearing semantics");

            // A valid pending R-036 backup survives sleep; native W37 replacement restore still works.
            BlockPos oldPoint = new BlockPos(25_000_000, 80, 25_000_000);
            player.setRespawnPosition(CelestialIds.MOON_LEVEL, oldPoint, -72.25F, true, false);
            CompoundTag persisted = new CompoundTag();
            RespawnPointBackup.store(persisted, new RespawnPointBackup.Backup(
                    new RespawnPointBackup.Point(CelestialIds.MOON_LEVEL, oldPoint, -72.25F, true),
                    RespawnOutcome.DISABLED));
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            CompoundTag before = persisted.copy();
            Tuple prior = Tuple.read(player);
            helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent(), "Backup sleep failed");
            prior.assertSame(helper, player);
            helper.assertTrue(before.equals(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)),
                    "Sleep modified the persisted R-036 backup");
            player.stopSleepInBed(true, true);
            fixture.player = fixture.server.getPlayerList().respawn(player, false);
            prior.assertSame(helper, fixture.player);
            helper.assertTrue(fixture.player.level().dimension().equals(Level.OVERWORLD), "W37 fallback did not run");
            state[0]++;
            state[1] = 0;
            return state[0] == fixture.rooms.size();
        });
    }

    @GameTest(template = TEMPLATE, batch = "natural_beds_nested", timeoutTicks = 60)
    public static void earlyReturnsNestedSleepAndForeignSameTupleSettersKeepWorking(GameTestHelper helper) {
        int[] phase = {0};
        Tuple[] original = {null};
        withRoomSteps(helper, "W37BNested", fixture -> {
            ServerPlayer player = fixture.player;
            Room room = fixture.rooms.get(0);
            enter(player, room);
            BlockPos home = helper.absolutePos(new BlockPos(2, 1, 2));
            if (phase[0] == 0) {
                player.setRespawnPosition(Level.OVERWORLD, home, 11.25F, true, false);
                player.teleportTo(room.level, room.vent.getX() + 20.5D, room.vent.getY() + 1, room.vent.getZ() + 0.5D, 0, 0);
                helper.assertTrue(player.startSleepInBed(room.head()).left().orElse(null)
                        == Player.BedSleepingProblem.TOO_FAR_AWAY, "The early range refusal was not reached");
                player.setRespawnPosition(room.level.dimension(), room.head(), 22.5F, false, false);
                Tuple unrelated = Tuple.read(player);
                // A synthetic time check must not consume an early-return snapshot as a completed native bed write.
                MinecraftForge.EVENT_BUS.post(new SleepingTimeCheckEvent(player, Optional.of(room.head())));
                unrelated.assertSame(helper, player);
                enter(player, room);
                boolean[] reposted = {false};
                Consumer<PlayerSleepInBedEvent> synthetic = event -> {
                    if (event.getEntity() == player && !reposted[0]) {
                        reposted[0] = true;
                        MinecraftForge.EVENT_BUS.post(new PlayerSleepInBedEvent(player, Optional.of(room.head().west())));
                    }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayerSleepInBedEvent.class, synthetic);
                try {
                    helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent(), "Reposted-admission sleep failed");
                    unrelated.assertSame(helper, player);
                    player.stopSleepInBed(true, true);
                } finally { MinecraftForge.EVENT_BUS.unregister(synthetic); }
                original[0] = unrelated;
                phase[0] = 1;
                return false;
            }
            if (phase[0] == 1) {
                Tuple unrelated = original[0];
                boolean[] nested = {false};
                Consumer<PlayerSetSpawnEvent> nest = event -> {
                    if (event.getEntity() == player && !nested[0]) {
                        nested[0] = true;
                        MinecraftForge.EVENT_BUS.post(new SleepingTimeCheckEvent(player, Optional.of(room.head())));
                        helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent(), "Nested sleep failed");
                        unrelated.assertSame(helper, player);
                        player.stopSleepInBed(true, true);
                        // A same-arguments foreign setter while the outer native setter is on the stack must work.
                        player.setRespawnPosition(room.level.dimension(), room.head(), 81.25F, false, false);
                        helper.assertTrue(player.getRespawnAngle() == 81.25F, "The nested foreign setter was intercepted");
                        fixture.server.getCommands().performPrefixedCommand(fixture.server.createCommandSourceStack(),
                                "spawnpoint " + player.getGameProfile().getName() + " " + home.getX() + " " + home.getY()
                                        + " " + home.getZ() + " 37.5");
                    }
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerSetSpawnEvent.class, nest);
                try {
                    helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent(), "Outer sleep failed");
                    helper.assertTrue(nested[0] && player.getRespawnAngle() == 37.5F && player.isRespawnForced()
                                    && home.equals(player.getRespawnPosition()) && player.getRespawnDimension() == Level.OVERWORLD,
                            "An enclosing snapshot overwrote the nested admin setter");
                    player.stopSleepInBed(true, true);
                } finally { MinecraftForge.EVENT_BUS.unregister(nest); }
                phase[0] = 2;
                return false;
            }
            boolean[] canceled = {false};
            Consumer<PlayerSetSpawnEvent> cancelAndSet = event -> {
                if (event.getEntity() == player && !canceled[0]) {
                    canceled[0] = true;
                    event.setCanceled(true);
                    player.setRespawnPosition(room.level.dimension(), room.head(), player.getYRot(), false, false);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerSetSpawnEvent.class, cancelAndSet);
            try {
                helper.assertTrue(player.startSleepInBed(room.head()).right().isPresent(), "Canceled-setter sleep failed");
                helper.assertTrue(canceled[0] && !player.isRespawnForced() && room.head().equals(player.getRespawnPosition())
                                && player.getRespawnDimension().equals(room.level.dimension()),
                        "The canceled native setter's candidate caused a foreign same-tuple setter to be undone");
                player.stopSleepInBed(true, true);
            } finally { MinecraftForge.EVENT_BUS.unregister(cancelAndSet); }
            return true;
        });
    }

    @GameTest(template = TEMPLATE, batch = "natural_beds_vacuum", timeoutTicks = 40)
    public static void vacuumAdmissionOnAllSixNativeHostsUsesTheW37Rule(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (var discovery = new NaturalBedFixtures.DiscoveryScope(server)) {
            ServerPlayer player = join(helper, "W37BVacuum");
            List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
            bodies.addAll(PlanetaryContent.definitions());
            bodies.addAll(StarSystemContent.definitions());
            bodies.addAll(ExoplanetContent.definitions());
            bodies.replaceAll(body -> new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(),
                    body.gravityMultiplier(), new AtmosphereDefinition(0, false, 288, ModIdentity.id("vacuum")),
                    body.orbit(), body.visualProfile(), body.capabilities(), body.solarIntensity(), body.radiation(),
                    false, body.discoveryRequired()));
            CelestialCatalogManager catalogs = new CelestialCatalogManager();
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies)), "Invalid vacuum fixture catalog");
            AtmosphereManager air = new AtmosphereManager(new CelestialEnvironmentService(catalogs));
            SleepRespawnService admission = new SleepRespawnService(air, catalogs, () -> new Options(false, false));
            // Controlled catalog supplies the counterfactual vacuum on normally breathable Tau Ceti f. The real
            // startSleepInBed, real Levels and same production W37 admission code run; no runtime authority is replaced.
            Consumer<PlayerSleepInBedEvent> listener = event -> {
                if (event.getEntity() == player) { admission.onSleepInBed(event); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerSleepInBedEvent.class, listener);
            List<Runnable> cleanup = new ArrayList<>();
            try {
                for (ResourceKey<Level> key : HOSTS) {
                    ServerLevel level = NonPlayerAtmosphereGameTests.level(helper, key);
                    BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
                    BlockPos head = new BlockPos(column.getX(), 200, column.getZ());
                    cleanup.add(SleepRespawnGameTests.placeBedOnPlatform(level, head));
                    player.teleportTo(level, head.getX() + 0.5D, head.getY(), head.getZ() + 0.5D, 0, 0);
                    Tuple before = Tuple.read(player);
                    helper.assertTrue(player.startSleepInBed(head).left().orElse(null)
                                    == Player.BedSleepingProblem.NOT_POSSIBLE_HERE && !player.isSleeping(),
                            "An unbreathable native bed was admitted in " + key.location());
                    before.assertSame(helper, player);
                }
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                server.getPlayerList().remove(player);
                cleanup.forEach(Runnable::run);
                air.clear();
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "natural_beds_command", timeoutTicks = 60)
    public static void earlierTimeCheckFailureIsRestoredBeforeImmediateSaveAll(GameTestHelper helper) {
        withRooms(helper, "W37BCommand", fixture -> {
            Room room = fixture.rooms.get(0);
            enter(fixture.player, room);
            fixture.player.setRespawnPosition(Level.OVERWORLD, helper.absolutePos(new BlockPos(2, 1, 2)),
                    -72.25F, true, false);
            Tuple saved = Tuple.read(fixture.player);
            interrupted(helper, fixture.player, room.head());
            helper.assertTrue(room.head().equals(fixture.player.getRespawnPosition()), "The native write was not exposed");
            NaturalBedFixtures.saveWithReversedCommandCancellation(fixture.server);
            saved.assertSame(helper, fixture.player);
            assertFile(helper, fixture.player, saved);
            interrupted(helper, fixture.player, room.head());
            fixture.player.setRespawnPosition(Level.OVERWORLD, saved.position.offset(1, 0, 0), 81.25F, true, false);
            Tuple newer = Tuple.read(fixture.player);
            fixture.server.getCommands().performPrefixedCommand(fixture.server.createCommandSourceStack(), "save-all");
            newer.assertSame(helper, fixture.player);
            assertFile(helper, fixture.player, newer);
            interrupted(helper, fixture.player, room.head());
            fixture.player.setRespawnPosition(room.level.dimension(), room.head(), fixture.player.getYRot(), false, false);
            Tuple same = Tuple.read(fixture.player);
            fixture.server.getCommands().performPrefixedCommand(fixture.server.createCommandSourceStack(), "save-all");
            same.assertSame(helper, fixture.player);
            assertFile(helper, fixture.player, same);
            fixture.player.setRespawnPosition(Level.OVERWORLD, saved.position, -72.25F, true, false);
            interrupted(helper, fixture.player, room.head());
            helper.assertTrue(fixture.player.startSleepInBed(room.head()).right().isPresent(), "The next bed call failed");
            saved.assertSame(helper, fixture.player);
            fixture.player.stopSleepInBed(true, true);
        });
    }

    @GameTest(template = TEMPLATE, batch = "natural_beds_start", timeoutTicks = 60)
    public static void earlierTimeCheckFailureIsRestoredBeforeNextTickSave(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "W37BStart", HOSTS);
        boolean[] interrupted = {false};
        boolean[] done = {false};
        int[] interruptedTick = {-1};
        Tuple[] saved = {null};
        GameTestTickPacer pacer = new GameTestTickPacer(fixture.server);
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            try {
                pacer.pace();
                if (!fixture.ready()) {
                    helper.assertTrue(helper.getTick() < 50, "The fallback rooms did not become breathable: " + fixture.status());
                    return;
                }
                if (!interrupted[0]) {
                    Room room = fixture.rooms.get(0);
                    enter(fixture.player, room);
                    fixture.player.setRespawnPosition(Level.OVERWORLD, helper.absolutePos(new BlockPos(2, 1, 2)),
                            -72.25F, true, false);
                    saved[0] = Tuple.read(fixture.player);
                    interrupted(helper, fixture.player, room.head());
                    helper.assertTrue(room.head().equals(fixture.player.getRespawnPosition()), "No pending bed write");
                    interruptedTick[0] = fixture.server.getTickCount();
                    interrupted[0] = true;
                    return;
                }
                if (fixture.server.getTickCount() <= interruptedTick[0]) { return; }
                saved[0].assertSame(helper, fixture.player);
                fixture.server.getPlayerList().saveAll(); // Same native serialization as the autosave.
                assertFile(helper, fixture.player, saved[0]);
                done[0] = true;
                fixture.close();
                helper.succeed();
            } catch (RuntimeException failure) {
                done[0] = true;
                fixture.close();
                throw failure;
            }
        });
    }

    private static void interrupted(GameTestHelper helper, ServerPlayer player, BlockPos head) {
        // Forge does not reserve HIGHEST's first slot. This scoped test-only dispatch fixture prepends a throwing
        // foreign listener to the actual baked listener array, then restores it. It does not bypass the setter.
        SleepingTimeCheckEvent probe = new SleepingTimeCheckEvent(player, Optional.of(head));
        try {
            var field = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("busID");
            field.setAccessible(true);
            IEventListener[] listeners = probe.getListenerList().getListeners(field.getInt(MinecraftForge.EVENT_BUS));
            IEventListener first = listeners[0];
            listeners[0] = event -> {
                if (((SleepingTimeCheckEvent) event).getEntity() == player) { throw new IllegalStateException(INTERRUPTED); }
                first.invoke(event);
            };
            try {
                try {
                    player.startSleepInBed(head);
                    helper.fail("The earlier foreign listener did not interrupt dispatch");
                } catch (IllegalStateException expected) {
                    helper.assertTrue(INTERRUPTED.equals(expected.getMessage()), "Unexpected exception " + expected);
                }
            } finally { listeners[0] = first; }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Forge dispatch fixture no longer matches the pinned EventBus", failure);
        }
    }

    private static void runtimeType(GameTestHelper helper, ServerLevel level) {
        DimensionType type = level.dimensionType();
        helper.assertTrue(type.natural() && level.dimensionTypeRegistration().unwrapKey().orElseThrow().location()
                .equals(level.dimension().location()), "Wrong runtime natural holder " + level.dimension().location());
        var json = DimensionType.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, type).result().orElseThrow();
        helper.assertTrue(type.equals(DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow()),
                "Dimension type readback changed fields");
        GlobalPos compass = CompassItem.getSpawnPosition(level);
        helper.assertTrue(compass != null && compass.dimension().equals(level.dimension())
                        && compass.pos().equals(level.getSharedSpawnPos()), "Compass did not target this Level's shared spawn");
        if (level.dimension().equals(CelestialIds.MOON_LEVEL) || level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            helper.assertTrue(type.fixedTime().orElse(-1) == 18000 && type.timeOfDay(0) == type.timeOfDay(12000),
                    "Fixed midnight clock input changed");
        } else {
            helper.assertTrue(!type.hasFixedTime() && type.timeOfDay(0) != type.timeOfDay(12000),
                    "Cyclic clock input stopped changing");
        }
    }

    private static void assertFile(GameTestHelper helper, ServerPlayer player, Tuple tuple) {
        try {
            CompoundTag file = NbtIo.readCompressed(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getStringUUID() + ".dat").toFile());
            helper.assertTrue(file.getString("SpawnDimension").equals(tuple.dimension.location().toString())
                            && file.getInt("SpawnX") == tuple.position.getX() && file.getInt("SpawnY") == tuple.position.getY()
                            && file.getInt("SpawnZ") == tuple.position.getZ() && file.getBoolean("SpawnForced") == tuple.forced
                            && Float.floatToRawIntBits(file.getFloat("SpawnAngle")) == tuple.angleBits,
                    "The native save persisted the temporary bed tuple");
        } catch (IOException failure) { throw new IllegalStateException("Native player file read failed", failure); }
    }

    private static void enter(ServerPlayer player, Room room) {
        player.server.overworld().setDayTime(18000);
        player.teleportTo(room.level, room.vent.getX() + 0.5D, room.vent.getY() + 1, room.vent.getZ() + 0.5D, 0, 0);
    }

    private static ServerPlayer join(GameTestHelper helper, String name) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), false, new ArrayList<>(), false);
    }

    private static void withRooms(GameTestHelper helper, String name, Consumer<Fixture> checks) {
        withRoomSteps(helper, name, fixture -> { checks.accept(fixture); return true; });
    }

    private static void withRoomSteps(GameTestHelper helper, String name, Predicate<Fixture> checks) {
        Fixture fixture = new Fixture(helper, name, HOSTS);
        boolean[] done = {false};
        GameTestTickPacer pacer = new GameTestTickPacer(fixture.server);
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            try {
                pacer.pace();
                if (!fixture.ready()) {
                    helper.assertTrue(helper.getTick() < 50, "The six bed fixtures did not become breathable: " + fixture.status());
                    return;
                }
                if (!checks.test(fixture)) { return; }
                done[0] = true;
                fixture.close();
                helper.succeed();
            } catch (RuntimeException failure) {
                done[0] = true;
                fixture.close();
                throw failure;
            }
        });
    }

    private record Tuple(ResourceKey<Level> dimension, BlockPos position, int angleBits, boolean forced) {
        static Tuple read(ServerPlayer player) {
            return new Tuple(player.getRespawnDimension(), player.getRespawnPosition(),
                    Float.floatToRawIntBits(player.getRespawnAngle()), player.isRespawnForced());
        }

        void assertSame(GameTestHelper helper, ServerPlayer player) {
            helper.assertTrue(equals(read(player)), "Spawn tuple changed: expected " + this + " actual " + read(player));
        }
    }
}
