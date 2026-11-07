package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndexCodec;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Station deletion, advisory console state, and bounded saved-index capacity. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeletedStationPadGameTests {
    private DeletedStationPadGameTests() { }

    @GameTest(template = "empty", batch = "pad_station_delete", timeoutTicks = 100)
    public static void deletedPadsCannotBeListedSelectedLandedOnOrInheritedByRecycledCell(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.command("list " + f.station.stationId(), "list", 1);
            f.select("selected", 1);
            f.delete();
            f.command("list " + f.station.stationId(), "list_refused", 0);
            f.select("select_unknown", 0);
            f.server().getPlayerList().op(f.player.getGameProfile());
            f.command("list " + f.station.stationId(), "list_refused", 0);
            f.select("select_unknown", 0);
            helper.assertTrue(LandingPadService.registered(f.server(), f.station.stationId(), f.entry.reference()) == null
                    && LandingPadService.resolve(f.server(), f.station.stationId(), f.entry.reference()) == null,
                    "Deleted station still admitted its old pad");
            var source = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(),
                    new RocketPosition(0, 80, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                            new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                    List.of(), new RocketStats(1, 1, 0, 0, 0, 0, 0, 0), 0);
            helper.assertTrue(!new NamedLandingPadStaging().select(f.space(), source, UUID.randomUUID(), f.station,
                    f.entry.reference(), List.of(), 0).success(), "Deleted station admitted a named landing");
            UUID replacementId = UUID.randomUUID();
            f.registry.reserve(replacementId, f.station.ownerId(), "Recycled cell", CelestialIds.EARTH_ID, 0);
            var replacement = f.registry.commit(replacementId);
            try {
                helper.assertTrue(replacement.cell().equals(f.station.cell()), "Fixture did not reuse the deleted cell");
                helper.assertTrue(f.index.entries(replacementId).isEmpty()
                        && LandingPadService.resolve(f.server(), replacementId, f.entry.reference()) == null,
                        "Recycled cell inherited an old pad or binding");
                // The old physical block is not inherited. Its new owner can explicitly clear the obsolete binding.
                refused(helper, () -> LandingPadService.register(f.space(), f.position, replacement.ownerId(), false, "New"));
                helper.assertTrue(LandingPadService.remove(f.space(), f.position, replacement.ownerId(), false),
                        "New station owner could not clear the obsolete physical binding");
                var rebound = LandingPadService.register(f.space(), f.position, replacement.ownerId(), false, "Rebound");
                helper.assertTrue(!rebound.reference().equals(f.entry.reference())
                        && LandingPadService.resolve(f.server(), replacementId, rebound.reference()) != null,
                        "Rebound pad did not get a new usable identity");
                for (int i = 1; i < LandingPadIndexCodec.MAX_PADS; i++) {
                    f.index.register(replacementId, replacement.ownerId(), UUID.randomUUID(), "New " + i,
                            new StationPosition(i, 80, 0));
                }
                helper.assertTrue(f.index.entries(replacementId).size() == LandingPadIndexCodec.MAX_PADS,
                        "Old station consumed the replacement's per-station limit");
            } finally {
                for (var entry : f.index.entries(replacementId)) {
                    f.index.remove(replacementId, entry.reference(), entry.physicalId());
                }
                f.registry.delete(replacementId);
            }
            helper.assertTrue(f.index.entries(f.station.stationId()).isEmpty(), "Deleted station retained its pad bucket");
            helper.assertTrue(LandingPadIndexCodec.decode(f.index.save(new CompoundTag())).entries().isEmpty(),
                    "Deleted pad was still serialized");
            // The deletion's flush must already have saved the removal, before any end-of-test autosave.
            try {
                var file = f.server().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data").resolve(LandingPadIndex.DATA_NAME + ".dat").toFile();
                helper.assertTrue(NbtIo.readCompressed(file).getCompound("data").getList("pads", 10).isEmpty(),
                        "Station deletion did not flush its pad removal");
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "pad_station_delete_guard", timeoutTicks = 100)
    public static void refusedDeletionKeepsEveryPadClaimAndChoice(GameTestHelper helper) {
        var f = new Fixture(helper);
        try {
            f.select("selected", 1);
            UUID transfer = UUID.randomUUID();
            helper.assertTrue(f.index.claim(f.station.stationId(), f.entry.reference(), f.entry.physicalId(), transfer),
                    "Fixture claim could not be installed");
            var before = f.index.save(new CompoundTag()); var choice = f.index.choice(f.player.getUUID());
            refused(helper, () -> f.manager.delete(f.server(), f.station.ownerId(), false, f.station.stationId(), "wrong"));
            refused(helper, () -> f.manager.delete(f.server(), UUID.randomUUID(), false, f.station.stationId(), "confirm"));
            f.guardRocket = ModEntities.ROCKET.get().create(f.space());
            helper.assertTrue(f.guardRocket != null, "Deletion guard rocket could not be created");
            var at = f.station.landingPad();
            f.guardChunk = new ChunkPos(new BlockPos(at.x(), at.y(), at.z()));
            f.space().getChunkSource().addRegionTicket(TicketType.START, f.guardChunk, 2, Unit.INSTANCE);
            f.space().getChunk(f.guardChunk.x, f.guardChunk.z);
            f.guardRocket.setPos(at.x() + 0.5, at.y(), at.z() + 0.5);
            helper.assertTrue(f.space().addFreshEntity(f.guardRocket), "Deletion guard rocket could not be published");
            // Full runs can recycle a non-entity-ticking cell. Publish is not proof of query visibility.
            // Register this retry at test start, not from a scheduled callback; keep the original 100-tick bound.
            helper.succeedWhen(() -> {
                helper.assertTrue(f.space().getEntitiesOfClass(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class,
                        f.guardRocket.getBoundingBox().inflate(1)).contains(f.guardRocket),
                        "Deletion guard rocket is not yet visible to the level query");
                refused(helper, f::delete);
                helper.assertTrue(before.equals(f.index.save(new CompoundTag()))
                        && choice.equals(f.index.choice(f.player.getUUID())) && f.registry.find(f.station.stationId()).isPresent(),
                        "Rejected deletion mutated station pads, claims or choice");
            });
        } catch (RuntimeException | Error failure) { f.close(); throw failure; }
    }

    private static void refused(GameTestHelper helper, Runnable operation) {
        boolean refused = false;
        try { operation.run(); }
        catch (IllegalArgumentException | IllegalStateException | SecurityException expected) { refused = true; }
        helper.assertTrue(refused, "Operation was not refused");
    }

    @GameTest(template = "empty", batch = "pad_station_delete_console", timeoutTicks = 100)
    public static void deletionClearsTheConsoleChoiceAndItsDisplayedName(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            f.select("selected", 1);
            var rocket = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(rocket != null, "Console rocket could not be created");
            try {
                var menu = new RocketFlightMenu(1, f.player.getInventory(), rocket);
                helper.assertTrue(menu.padName(menu.activePlan()).equals("Deleted Dock"), "Initial console name missing");
                f.delete();
                var sent = menu.planPacket(RocketNavigation.empty());
                helper.assertTrue(sent.plan().equals(RocketFlightPlanSnapshot.empty()) && sent.padName().isEmpty(),
                        "Console retained the deleted station's pad choice/name: " + sent.padName());
            } finally { rocket.discard(); }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "pad_station_delete_capacity", timeoutTicks = 100)
    public static void deletionFreesAStationSlotInAnOtherwiseFullSavedIndex(GameTestHelper helper) {
        try (var f = new Fixture(helper)) {
            var entries = new ArrayList<LandingPadEntry>(); entries.add(f.entry);
            for (int i = 1; i < LandingPadIndexCodec.MAX_STATIONS; i++) {
                entries.add(new LandingPadEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), f.station.ownerId(),
                        "Old orphan", new StationPosition(0, 80, 0), i, null));
            }
            var full = LandingPadIndex.load(LandingPadIndexCodec.encode(LandingPadIndexCodec.MAX_STATIONS, entries, false));
            helper.assertTrue(full.operational(), "Full schema-1 fixture was invalid");
            f.server().overworld().getDataStorage().set(LandingPadIndex.DATA_NAME, full);
            try {
                f.delete();
                var reloaded = LandingPadIndex.load(full.save(new CompoundTag()));
                var added = reloaded.register(UUID.randomUUID(), f.station.ownerId(), UUID.randomUUID(), "New station",
                        new StationPosition(0, 80, 0));
                helper.assertTrue(added.epoch() == LandingPadIndexCodec.MAX_STATIONS,
                        "Deletion reset the durable epoch");
                helper.assertTrue(LandingPadIndexCodec.decode(reloaded.save(new CompoundTag())).entries().size()
                        == LandingPadIndexCodec.MAX_STATIONS, "Deletion changed another station's entry");
            } finally { f.server().overworld().getDataStorage().set(LandingPadIndex.DATA_NAME, f.index); }
            helper.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable, net.minecraft.gametest.framework.GameTestListener {
        final GameTestHelper helper;
        final StationRegistrySavedData registry;
        final LandingPadIndex previousIndex;
        final LandingPadIndex index = new LandingPadIndex();
        final StationState station;
        final StationManager manager;
        final ServerPlayer player;
        final LandingPadEntry entry;
        final BlockPos position;
        final net.minecraft.world.level.block.state.BlockState previousBlock;
        final List<String> replies = new ArrayList<>();
        io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity guardRocket;
        ChunkPos guardChunk;
        boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            registry = StationRegistrySavedData.get(server());
            previousIndex = LandingPadIndex.get(server());
            server().overworld().getDataStorage().set(LandingPadIndex.DATA_NAME, index);
            UUID owner = UUID.randomUUID(); UUID id = UUID.randomUUID();
            registry.reserve(id, owner, "Deleted pad fixture", CelestialIds.EARTH_ID, 0);
            station = registry.commit(id);
            var catalogs = new CelestialCatalogManager();
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())),
                    "Default celestial catalog could not be prepared");
            manager = new StationManager(catalogs);
            player = ConnectedTestPlayers.join(server(), owner, "deletedPad", helper.getLevel(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), true, replies);
            // Outside the removable 17x17 template: deleting a station does not erase player-built pads.
            position = new BlockPos(station.landingPad().x() + 24, 80, station.landingPad().z());
            space().getChunk(position);
            previousBlock = space().getBlockState(position);
            helper.assertTrue(space().getBlockEntity(position) == null, "Fixture overlaps a block entity");
            space().setBlock(position, ModBlocks.LANDING_PAD.get().defaultBlockState(), 3);
            entry = LandingPadService.register(space(), position, owner, false, "Deleted Dock");
            try {
                var field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
                ((net.minecraft.gametest.framework.GameTestInfo) field.get(helper)).addListener(this);
            } catch (ReflectiveOperationException failure) { close(); throw new IllegalStateException(failure); }
        }

        net.minecraft.server.MinecraftServer server() { return helper.getLevel().getServer(); }
        net.minecraft.server.level.ServerLevel space() { return server().getLevel(CelestialIds.SPACE_LEVEL); }
        void delete() { manager.delete(server(), station.ownerId(), false, station.stationId(), "confirm"); }
        void select(String message, int expected) {
            command("select " + station.stationId() + " " + entry.padId() + " " + entry.epoch(), message, expected);
        }
        void command(String suffix, String message, int expected) {
            replies.clear();
            int result = server().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "arcepad " + suffix);
            helper.assertTrue(result == expected && !replies.isEmpty()
                    && replies.get(0).startsWith("message.advancedrocketrycommunity.pad." + message),
                    "Unexpected pad command result " + result + ": " + replies);
        }
        @Override public void close() {
            if (closed) { return; }
            closed = true;
            if (guardRocket != null) { guardRocket.discard(); }
            if (guardChunk != null) { space().getChunkSource().removeRegionTicket(TicketType.START, guardChunk, 2, Unit.INSTANCE); }
            server().getPlayerList().deop(player.getGameProfile());
            server().getPlayerList().remove(player);
            registry.delete(station.stationId());
            space().setBlock(position, previousBlock, 3);
            previousIndex.setDirty();
            server().overworld().getDataStorage().set(LandingPadIndex.DATA_NAME, previousIndex);
        }
        @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
        @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info) { close(); }
        @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info) { close(); }
    }
}
