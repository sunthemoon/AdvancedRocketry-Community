package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** W160F disposable native rows. All release admission still uses the registered operator command. */
final class RocketRejoinNativeRows {
    static final UUID FIRST = UUID.fromString("01580000-0000-4000-8000-000000000001");
    static final UUID SECOND = RocketRejoinNativeProbe.PLAYER;
    private static final BlockPos ORIGIN = new BlockPos(72, 100, 72);
    private final MinecraftServer server;
    private final String cut, mode;
    private ServerPlayer first, second;
    private boolean prepared, disassembled, boarded;

    static boolean selected(String cut) { return List.of("C12", "C25", "C26", "C27").contains(cut); }
    RocketRejoinNativeRows(MinecraftServer server, String cut, String mode) {
        this.server = server; this.cut = cut; this.mode = mode;
        RocketReleaseNativeCuts.validate(server);
        if (cut.equals("C26") && !mode.equals("seed")) {
            // ServerStarted occurs before this fixture forces the recorded entity column.
            require(java.util.stream.StreamSupport.stream(server.overworld().getAllEntities().spliterator(), false)
                    .noneMatch(e -> e instanceof RocketEntity),
                    "Player-first fixture already loaded its rocket");
            second = login(SECOND, "W160FSecond");
            if (mode.equals("recover")) { require(RocketRejoinProtection.guarded(second), "Player-first login was not guarded"); }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_W160F_PLAYER_FIRST mode={} loaded_rockets=0 guarded={}",
                    mode, RocketRejoinProtection.guarded(second));
        }
    }
    boolean prepare(RocketEntity rocket) {
        if (prepared) { return true; }
        if (cut.equals("C26")) {
            server.overworld().setDefaultSpawnPos(new BlockPos(8192, 100, 8192), 0);
            server.overworld().setChunkForced(4, 4, false);
        }
        if (cut.equals("C12")) {
            var image = RocketSourceRetirementFixtures.diskImage(server.overworld(), new ChunkPos(4, 4), rocket.getUUID());
            if (image == null) { return false; }
            require(image.getCompound("RocketEntityData").getCompound("flight_data").getCompound("passengers")
                    .getList("assignments", 10).size() == 2, "C12 rollback source lacks the older roster");
            try {
                Files.copy(server.getWorldPath(LevelResource.ROOT).resolve("entities/r.0.0.mca"),
                        server.getServerDirectory().toPath().resolve("w160f-old-entities.mca"));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            var flight = rocket.flightData().orElseThrow();
            rocket.updateFlightData(flight.withPassengers(flight.passengers().remove(FIRST)));
        }
        var old = login(SECOND, "W160FSecond"); old.setHealth(1);
        old.teleportTo(server.overworld(), rocket.getX(), rocket.getY(), rocket.getZ(), 0, 0);
        require(old.startRiding(rocket, true), "Old native seat did not mount");
        server.getPlayerList().saveAll(); server.getPlayerList().remove(old);
        prepared = true; return true;
    }
    boolean advance(RocketRejoinSavedData data, RocketEntity rocket) {
        if (data.acknowledgedRoot() == null || data.root().entries().stream().anyMatch(e -> e.seat() == RocketRejoinRoot.Seat.PENDING)
                || !data.acknowledgedCurrent()) { return false; }
        if (cut.equals("C25")) {
            if (rocket != null && !disassembled) {
                require(mode.equals("seed"), "Disassembled rocket reconstructed on restart");
                require(!RocketRuntime.releaseFenced(rocket), "Disassembly preceded S2");
                int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "arce rocket release-test disassemble " + rocket.getUUID() + " discard-fuel 1000");
                require(result == 1 && rocket.isRemoved(), "Native disassembly did not restore the footprint");
                disassembled = true;
            }
            require(server.overworld().getBlockState(ORIGIN).is(Blocks.IRON_BLOCK)
                    && server.overworld().getBlockState(ORIGIN.above()).is(Blocks.IRON_BLOCK), "Disassembly lost restored blocks");
        } else { require(rocket != null, "Native row lost its authority"); }
        if (second == null) { second = login(SECOND, "W160FSecond"); return false; }
        if (!finished(data, second)) { return false; }
        if (!cut.equals("C12")) {
            if (first == null) { first = login(FIRST, "W160FFirst"); return false; }
            if (!finished(data, first)) { return false; }
        }
        require(data.root().entries().isEmpty() && data.acknowledgedRoot().entries().isEmpty(), "Native row retained completed obligations");
        if (cut.equals("C27")) {
            if (!boarded) {
                if (mode.equals("seed")) {
                    var image = RocketSourceRetirementFixtures.diskImage(server.overworld(), new ChunkPos(4, 4), rocket.getUUID());
                    require(image != null && image.getCompound("RocketEntityData").getCompound("flight_data")
                            .getCompound("passengers").getList("assignments", 10).isEmpty(), "Old seats survived before new boarding");
                }
                board(first, rocket); board(second, rocket);
                require(rocket.flightData().orElseThrow().passengers().assignment(FIRST).orElseThrow().seatIndex() == 0
                        && rocket.flightData().orElseThrow().passengers().assignment(SECOND).orElseThrow().seatIndex() == 1,
                        "Later boarding changed either original seat index");
                require(first.getVehicle() == rocket && second.getVehicle() == rocket, "Later boarding did not mount both riders");
                boarded = true;
            }
        }
        require(server.saveEverything(false, true, true), "Native row final save failed");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W160F_ROW case={} mode={} disassembled={} boarded={} entries=0",
                cut, mode, cut.equals("C25"), boarded);
        if (mode.equals("seed")) { RocketReleaseNativeCuts.checkpoint(cut); }
        return true;
    }
    private void board(ServerPlayer player, RocketEntity rocket) {
        player.setPos(rocket.position());
        RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.BOARD,
                new TravelTarget.BodySurface(io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.EARTH_ID), UUID.randomUUID());
    }
    private boolean finished(RocketRejoinSavedData data, ServerPlayer player) {
        require(player.isAlive() && player.getHealth() > 0, "Native row passenger died");
        if (data.root().entries().stream().anyMatch(e -> e.player().equals(player.getUUID())) || RocketRejoinProtection.guarded(player)) { return false; }
        require(!player.getPersistentData().contains("arce_passenger_rejoin"), "P2 retained a live marker");
        if (!cut.equals("C27")) { require(player.getVehicle() == null, "Released rider remounted an old seat"); }
        return true;
    }
    private ServerPlayer login(UUID id, String name) { return ReleaseTestPlayers.login(server, id, name, server.overworld()); }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
}
