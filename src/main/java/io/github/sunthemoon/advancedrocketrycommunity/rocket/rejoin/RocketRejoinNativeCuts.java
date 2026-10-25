package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Disposable C7-C11/C13 boundaries and a real backup-replacement failure, never a player writer injection. */
public final class RocketRejoinNativeCuts {
    private RocketRejoinNativeCuts() { }
    private static boolean seed(MinecraftServer server, String cut) {
        if (!RocketReleaseNativeCuts.rejoin() || !RocketReleaseNativeCuts.selection().endsWith(":" + cut + ":seed")) { return false; }
        RocketReleaseNativeCuts.validate(server);
        return true;
    }
    static void placed(ServerPlayer player, RocketRejoinRoot.Entry entry, RocketRejoinSavedData data) {
        var server = player.getServer(); if (!RocketReleaseNativeCuts.rejoin()) { return; }
        RocketReleaseNativeCuts.validate(server);
        if (seed(server, "C9")) {
            Path backup = backup(player);
            try {
                if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Unexpected backup object"); }
                    Files.delete(backup);
                }
                Files.createDirectory(backup); Files.writeString(backup.resolve("w159-owned-blocker"), "W159 disposable replacement failure");
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        RocketReleaseNativeCuts.checkpoint("C8");
        if (seed(server, "C13")) {
            if (data.acknowledgedRoot().entries().stream().noneMatch(e -> e.release().equals(entry.release()) && e.seat() == RocketRejoinRoot.Seat.PENDING)) {
                throw new IllegalStateException("C13 did not retain unfinished seat work");
            }
            RocketReleaseNativeCuts.checkpoint("C13");
        }
    }
    static void saved(ServerPlayer player) {
        if (seed(player.getServer(), "C9") && Files.isDirectory(backup(player), LinkOption.NOFOLLOW_LINKS)) { RocketReleaseNativeCuts.checkpoint("C9"); }
    }
    public static void cleanFixtureBackup(MinecraftServer server, java.util.UUID player) {
        if (!RocketReleaseNativeCuts.rejoin()) { return; } RocketReleaseNativeCuts.validate(server);
        var backup = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player + ".dat_old");
        var marker = backup.resolve("w159-owned-blocker");
        try {
            if (Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(marker); Files.delete(backup);
            }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private static Path backup(ServerPlayer player) {
        return player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat_old");
    }
}
