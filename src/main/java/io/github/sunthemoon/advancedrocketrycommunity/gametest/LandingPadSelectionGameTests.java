package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadIndex;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Command-level remote menu intent and distinct refusal messages. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandingPadSelectionGameTests {
    private LandingPadSelectionGameTests() { }

    @GameTest(template = "empty", batch = "pad_selection", timeoutTicks = 100)
    public static void unauthorizedSelectionExplainsVisitPermission(GameTestHelper helper) {
        fixture(helper, f -> {
            f.select(f.station, f.pad, "select_permission", 0);
        }, false);
    }

    @GameTest(template = "empty", batch = "pad_selection", timeoutTicks = 100)
    public static void unknownStationPadAndStaleEpochExplainTheUnknownChoice(GameTestHelper helper) {
        fixture(helper, f -> {
            f.select(UUID.randomUUID(), f.pad, "select_unknown", 0);
            f.select(f.station, new LandingPadReference(UUID.randomUUID(), 0), "select_unknown", 0);
            f.select(f.station, new LandingPadReference(f.pad.padId(), f.pad.epoch() + 1), "select_unknown", 0);
        }, true);
    }

    @GameTest(template = "empty", batch = "pad_selection", timeoutTicks = 100)
    public static void exhaustedTransientChoiceSlotsHaveTheirOwnRefusal(GameTestHelper helper) {
        fixture(helper, f -> {
            try {
                for (int i = 0; i < 128; i++) {
                    UUID actor = UUID.randomUUID();
                    f.fillers.add(actor);
                    helper.assertTrue(f.index.choose(actor, f.station, f.pad), "Could not fill choice slots");
                }
                f.select(f.station, f.pad, "select_full", 0);
            } finally { f.fillers.forEach(f.index::forgetChoice); }
        }, true);
    }

    @GameTest(template = "empty", batch = "pad_selection", timeoutTicks = 100)
    public static void remoteUnloadedChoiceIsRecordedWithoutLoadingOrClaiming(GameTestHelper helper) {
        fixture(helper, f -> {
            var space = f.player.getServer().getLevel(CelestialIds.SPACE_LEVEL);
            var station = StationRegistrySavedData.get(f.player.getServer()).find(f.station).orElseThrow();
            var entry = f.index.find(f.station, f.pad).orElseThrow();
            BlockPos at = entry.position(station);
            helper.assertTrue(space.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null,
                    "Remote fixture pad was already loaded");
            var saved = f.index.save(new net.minecraft.nbt.CompoundTag());
            f.select(f.station, f.pad, "selected_unloaded", 1);
            helper.assertTrue(f.pad.equals(f.index.choice(f.player.getUUID()).landingPad()), "Remote choice was lost");
            var rocket = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(rocket != null, "Console rocket could not be created");
            var menu = new RocketFlightMenu(1, f.player.getInventory(), rocket);
            helper.assertTrue(menu.activePlan().equals(f.index.choice(f.player.getUUID()))
                    && menu.padName(menu.activePlan()).equals("Venus Dock"), "Console did not supply the indexed pad name");
            rocket.discard();
            helper.assertTrue(saved.equals(f.index.save(new net.minecraft.nbt.CompoundTag())), "Menu intent changed durable pad data");
            helper.assertTrue(LandingPadService.resolve(f.player.getServer(), f.station, f.pad) == null,
                    "Unloaded choice bypassed physical identity admission");
            helper.assertTrue(space.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null,
                    "Remote command loaded a destination chunk");
        }, true);
    }

    @GameTest(template = "empty", batch = "pad_selection", timeoutTicks = 100)
    public static void invalidPlayerAndUnavailableIndexHaveDistinctRefusals(GameTestHelper helper) {
        fixture(helper, f -> {
            f.player.setHealth(0);
            f.select(f.station, f.pad, "select_invalid", 0);
            f.player.setHealth(f.player.getMaxHealth());
            var invalid = new net.minecraft.nbt.CompoundTag(); invalid.putInt("schema", 999);
            var storage = f.player.getServer().overworld().getDataStorage();
            storage.set(LandingPadIndex.DATA_NAME, LandingPadIndex.load(invalid));
            try { f.select(f.station, f.pad, "select_unavailable", 0); }
            finally { storage.set(LandingPadIndex.DATA_NAME, f.index); }
        }, true);
    }

    @GameTest(template = "empty", batch = "pad_selection", timeoutTicks = 100)
    public static void loadedMissingPhysicalPadIsNotAnUnloadedChoice(GameTestHelper helper) {
        fixture(helper, f -> {
            var space = f.player.getServer().getLevel(CelestialIds.SPACE_LEVEL);
            var station = StationRegistrySavedData.get(f.player.getServer()).find(f.station).orElseThrow();
            var entry = f.index.find(f.station, f.pad).orElseThrow();
            BlockPos at = entry.position(station);
            space.getChunk(at); // Test-owned fixture load, not command behaviour.
            f.select(f.station, f.pad, "select_stale", 0);
            helper.assertTrue(f.index.choice(f.player.getUUID()).landingPad() == null, "Missing physical pad was selected");
        }, true);
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer player;
        final UUID station;
        final LandingPadReference pad;
        final LandingPadIndex index;
        final List<String> replies;
        final List<UUID> fillers = new ArrayList<>();
        Fixture(GameTestHelper helper, ServerPlayer player, UUID station, LandingPadReference pad,
                LandingPadIndex index, List<String> replies) {
            this.helper = helper; this.player = player; this.station = station;
            this.pad = pad; this.index = index; this.replies = replies;
        }
        void select(UUID stationId, LandingPadReference reference, String message, int expected) {
            replies.clear();
            int result = player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),
                    "arcepad select " + stationId + " " + reference.padId() + " " + reference.epoch());
            helper.assertTrue(result == expected, "Selection returned " + result + ", expected " + expected);
            helper.assertTrue(replies.size() == 1 && replies.get(0).startsWith(
                    "message.advancedrocketrycommunity.pad." + message), "Wrong selection reply: " + replies);
        }
    }

    private static void fixture(GameTestHelper helper, Consumer<Fixture> test, boolean member) {
        var server = helper.getLevel().getServer();
        var replies = new ArrayList<String>();
        var player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "padChoice", helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), false, replies);
        var registry = StationRegistrySavedData.get(server);
        var index = LandingPadIndex.get(server);
        UUID stationId = UUID.randomUUID();
        registry.reserve(stationId, UUID.randomUUID(), "Remote pad", CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
        var station = registry.commit(stationId);
        if (member) { registry.addMember(stationId, player.getUUID()); }
        var entry = index.register(stationId, station.ownerId(), UUID.randomUUID(), "Venus Dock",
                new StationPosition(32, station.landingPad().y() - 1, 0));
        try {
            test.accept(new Fixture(helper, player, stationId, entry.reference(), index, replies));
            helper.succeed();
        } finally {
            index.forgetChoice(player.getUUID());
            index.remove(stationId, entry.reference(), entry.physicalId());
            registry.delete(stationId);
            server.getPlayerList().remove(player);
        }
    }
}
