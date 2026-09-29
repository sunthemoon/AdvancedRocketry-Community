package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationProtectionGameTests {
    private StationProtectionGameTests() {
    }

    @GameTest(template = "empty", batch = "station_protection", timeoutTicks = 100)
    public static void unavailableRegistryDeniesSpaceBreakAndPlacementWithoutChangingOtherLevels(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space is unavailable");
        var original = StationRegistrySavedData.get(server);
        var manager = new StationManager(new CelestialCatalogManager());
        CompoundTag badRoot = new CompoundTag();
        badRoot.putInt("schema_version", 4);
        var blocked = StationRegistrySavedData.load(badRoot);
        helper.assertTrue(!blocked.operational(), "Invalid fixture unexpectedly loaded");
        // Synchronous scoped replacement: restore before another tick or any flush.
        server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, blocked);
        try {
            BlockPos position = new BlockPos(0, 128, 0);
            space.getChunkAt(position);
            for (boolean operator : new boolean[]{false, true}) {
                assertEvents(helper, manager, space, position, operator, true);
                assertEvents(helper, manager, helper.getLevel(), helper.absolutePos(BlockPos.ZERO), operator, false);
            }
            helper.assertTrue(blocked.save(new CompoundTag()).equals(badRoot), "Protection changed quarantined data");
            var available = new StationRegistrySavedData();
            server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, available);
            assertEvents(helper, manager, space, position, false, false);
        } finally {
            server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, original);
            manager.clear();
        }
        helper.succeed();
    }

    private static void assertEvents(GameTestHelper helper, StationManager manager, ServerLevel level,
                                     BlockPos position, boolean operator, boolean denied) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "StationAuthority")) {
            @Override
            public boolean hasPermissions(int permissionLevel) {
                return operator;
            }
        };
        var state = Blocks.AIR.defaultBlockState();
        var broken = new BlockEvent.BreakEvent(level, position, state, player);
        manager.onBlockBroken(broken);
        helper.assertTrue(broken.isCanceled() == denied, "Wrong break permission for operator=" + operator);
        var snapshot = BlockSnapshot.create(level.dimension(), level, position);
        var placed = new BlockEvent.EntityPlaceEvent(snapshot, state, player);
        manager.onBlockPlaced(placed);
        helper.assertTrue(placed.isCanceled() == denied, "Wrong placement permission for operator=" + operator);
        var multiple = new BlockEvent.EntityMultiPlaceEvent(List.of(snapshot,
                BlockSnapshot.create(level.dimension(), level, position.above())), state, player);
        manager.onBlockPlaced(multiple);
        helper.assertTrue(multiple.isCanceled() == denied, "Wrong multiple-placement permission for operator=" + operator);
    }
}
