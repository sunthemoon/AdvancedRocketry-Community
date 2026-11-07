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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
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
            f.command("list " + f.station.stationId(), "refused", 0);
            f.select("select_unknown", 0);
            f.server().getPlayerList().op(f.player.getGameProfile());
            f.command("list " + f.station.stationId(), "refused", 0);
            f.select("select_unknown", 0);
            helper.assertTrue(LandingPadService.registered(f.server(), f.station.stationId(), f.entry.reference()) == null
                    && LandingPadService.resolve(f.server(), f.station.stationId(), f.entry.reference()) == null,
                    "Deleted station still admitted its old pad");
            // Identity is checked before the source snapshot is read, with no chunk loading.
            helper.assertTrue(!new NamedLandingPadStaging().select(f.space(), null, UUID.randomUUID(), f.station,
                    f.entry.reference(), List.of(), 0).success(), "Deleted station admitted a named landing");
            UUID replacementId = UUID.randomUUID();
            f.registry.reserve(replacementId, f.station.ownerId(), "Recycled cell", CelestialIds.EARTH_ID, 0);
            var replacement = f.registry.commit(replacementId);
            try {
                helper.assertTrue(replacement.cell().equals(f.station.cell()), "Fixture did not reuse the deleted cell");
                helper.assertTrue(f.index.entries(replacementId).isEmpty()
                        && LandingPadService.resolve(f.server(), replacementId, f.entry.reference()) == null,
                        "Recycled cell inherited an old pad or binding");
                for (int i = 0; i < LandingPadIndexCodec.MAX_PADS; i++) {
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
            helper.succeed();
        }
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

    private static final class Fixture implements AutoCloseable {
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
            server().getPlayerList().deop(player.getGameProfile());
            server().getPlayerList().remove(player);
            registry.delete(station.stationId());
            space().setBlock(position, previousBlock, 3);
            previousIndex.setDirty();
            server().overworld().getDataStorage().set(LandingPadIndex.DATA_NAME, previousIndex);
        }
    }
}
