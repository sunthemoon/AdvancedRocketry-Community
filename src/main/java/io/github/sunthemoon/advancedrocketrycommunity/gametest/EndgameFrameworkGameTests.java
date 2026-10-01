package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.EndgameProtection;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-054 sections 5, 6, 10 and 12 on a running server: the chain, zones, the API event and the root file. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndgameFrameworkGameTests {
    private static volatile UUID vetoedOwner;

    static {
        MinecraftForge.EVENT_BUS.addListener(EndgameFrameworkGameTests::veto);
    }

    private EndgameFrameworkGameTests() {
    }

    private static void veto(EndgameEffectEvent event) {
        if (event.ownerId().equals(vetoedOwner)) {
            event.setCanceled(true);
        }
    }

    @GameTest(template = "empty", batch = "endgame_framework", timeoutTicks = 40)
    public static void theChainRefusesZonesUnloadedTargetsTheBuildHeightAndApiVetoes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EndgameService service = EndgameRuntime.operational().orElse(null);
        helper.assertTrue(service != null, "The endgame root is not operational");
        BlockPos min = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = min.offset(2, 0, 2);
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        helper.assertTrue(check(level, service, owner, min, max) == EndgameCode.OK, "An open area was refused");

        String name = "gt_" + owner.toString().substring(0, 8);
        ProtectedZone zone = ProtectedZone.of(name, level.dimension().location(), min.getX(), min.getZ(), max.getX(),
                max.getZ(), List.of(trusted));
        helper.assertTrue(service.barrier(root -> root.addZone(zone, 256)) == EndgameCode.OK, "Zone not added");
        try {
            helper.assertTrue(check(level, service, owner, min, max) == EndgameCode.TARGET_PROTECTED,
                    "A zone did not protect its area");
            helper.assertTrue(check(level, service, trusted, min, max) == EndgameCode.OK,
                    "An allow-listed owner was refused");
        } finally {
            service.barrier(root -> root.removeZone(name));
        }

        vetoedOwner = owner;
        try {
            helper.assertTrue(check(level, service, owner, min, max) == EndgameCode.TARGET_PROTECTED,
                    "A cancelled API event did not stop the batch");
        } finally {
            vetoedOwner = null;
        }

        BlockPos far = min.offset(4_800_000, 0, 0);
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "The far chunk is unexpectedly loaded");
        helper.assertTrue(check(level, service, owner, far, far.offset(2, 0, 2)) == EndgameCode.TARGET_UNLOADED,
                "An unloaded target was not refused");
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "The chain loaded a chunk (ADR-054 section 12)");

        BlockPos below = new BlockPos(min.getX(), level.getMinBuildHeight() - 1, min.getZ());
        helper.assertTrue(check(level, service, owner, below, max) == EndgameCode.TARGET_OUT_OF_BOUNDS,
                "A batch below the build height was accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "endgame_framework", timeoutTicks = 40)
    public static void aZoneIsABarrierWrittenToTheEndgameFileBeforeSuccess(GameTestHelper helper) {
        EndgameService service = EndgameRuntime.operational().orElse(null);
        helper.assertTrue(service != null, "The endgame root is not operational");
        ServerLevel level = helper.getLevel();
        String name = "gt_file_" + UUID.randomUUID().toString().substring(0, 8);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        ProtectedZone zone = ProtectedZone.of(name, level.dimension().location(), corner.getX(), corner.getZ(),
                corner.getX() + 3, corner.getZ() + 3, List.of());
        helper.assertTrue(service.barrier(root -> root.addZone(zone, 256)) == EndgameCode.OK, "Zone not added");
        try {
            helper.assertTrue(!service.writePending(), "The barrier left the root dirty");
            EndgameRoot onDisk = readFile(level);
            helper.assertTrue(onDisk.zone(name).equals(Optional.of(zone)), "The zone is not in the file");
        } finally {
            service.barrier(root -> root.removeZone(name));
        }
        helper.assertTrue(readFile(level).zone(name).isEmpty(), "The removal is not in the file");
        helper.succeed();
    }

    private static EndgameRoot readFile(ServerLevel level) {
        Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.ENDGAME.fileName());
        CompoundTag payload = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.ENDGAME).orElseThrow();
        return EndgameRootCodec.decode(payload);
    }

    private static EndgameCode check(ServerLevel level, EndgameService service, UUID owner, BlockPos min, BlockPos max) {
        EndgameRoot root = service.root().orElseThrow();
        return EndgameProtection.check(new EndgameProtection.Batch(EndgameSystem.LASER_DRILL, EndgameEffect.BLOCK_BREAK,
                owner, Optional.empty(), level.dimension(), min, max, true), new ForgeProtectionView(level, root.zones()));
    }
}
