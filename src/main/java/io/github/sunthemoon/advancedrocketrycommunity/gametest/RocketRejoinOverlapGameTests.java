package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W80 prerequisite: an open NIO scan must not prevent either of two vanilla player saves. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinOverlapGameTests {
    private static final String PROBE = "arce_w159_overlap_probe";

    private RocketRejoinOverlapGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_rejoin_overlap")
    public static void openScanAllowsTwoConsecutiveVanillaPlayerReplacements(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "ARCEW159Overlap",
                helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 2)), new ArrayList<>());
        Path installed = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
        try {
            Vec3 first = player.position();
            Vec3 second = first.add(1.0D, 0.0D, 0.0D);
            Vec3 third = first.add(2.0D, 0.0D, 0.0D);
            prepare(player, first, 0);
            server.getPlayerList().saveAll();
            helper.assertTrue(matches(read(installed), player.getUUID(), first, 0), "Initial vanilla save missing");
            // Default NIO open options, exactly as required by W80 section 4. Keep this handle open
            // while vanilla first renames its file to .dat_old, then deletes/reuses that name.
            try (var scan = Files.newInputStream(installed)) {
                prepare(player, second, 1);
                server.getPlayerList().saveAll();
                boolean replacementOne = matches(read(installed), player.getUUID(), second, 1);
                prepare(player, third, 2);
                server.getPlayerList().saveAll();
                boolean replacementTwo = matches(read(installed), player.getUUID(), third, 2);
                boolean originalScan = matches(NbtIo.readCompressed(scan), player.getUUID(), first, 0);
                helper.assertTrue(replacementOne && replacementTwo && originalScan,
                        "W159 overlap prerequisite: first replacement=" + replacementOne
                                + ", second replacement=" + replacementTwo + ", original open scan=" + originalScan);
            }
        } finally {
            // The scan is closed before logout performs its own vanilla save. Only this test's player is removed.
            player.getPersistentData().remove(PROBE);
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void prepare(ServerPlayer player, Vec3 position, int generation) {
        player.setPos(position);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.getPersistentData().putInt(PROBE, generation);
    }

    private static CompoundTag read(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            return NbtIo.readCompressed(input);
        }
    }

    private static boolean matches(CompoundTag tag, UUID player, Vec3 position, int generation) {
        var pos = tag.getList("Pos", Tag.TAG_DOUBLE);
        var forge = tag.getCompound("ForgeData");
        return tag.hasUUID("UUID") && tag.getUUID("UUID").equals(player)
                && pos.size() == 3 && pos.getDouble(0) == position.x
                && pos.getDouble(1) == position.y && pos.getDouble(2) == position.z
                && forge.contains(PROBE, Tag.TAG_INT) && forge.getInt(PROBE) == generation;
    }
}
