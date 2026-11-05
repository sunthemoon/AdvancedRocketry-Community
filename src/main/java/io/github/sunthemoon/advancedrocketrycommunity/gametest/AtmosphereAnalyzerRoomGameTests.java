package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerFeedback;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Installed-runtime and native item routing checks, not real-client visual evidence. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereAnalyzerRoomGameTests {
    private AtmosphereAnalyzerRoomGameTests() { }

    @GameTest(template = "atmosphere_test", batch = "atmosphere_analyzer", timeoutTicks = 80)
    public static void suppliedRoomDoorClickMatchesDirectEyeReading(GameTestHelper helper) {
        Room room = new Room(helper);
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) { return; }
            try {
                // Readiness is independent of the analyzer: these are the HUD eye and command feet queries.
                if (AtmosphereRuntime.breathabilityAt(room.level, room.eye()).orElseThrow() != BreathabilityState.BREATHABLE
                        || AtmosphereRuntime.breathabilityAt(room.level, room.player.blockPosition()).orElseThrow()
                        != BreathabilityState.BREATHABLE) {
                    helper.assertTrue(helper.getTick() < 79, "Room did not acquire authoritative supply");
                    return;
                }
                var status = new ArrayList<String>();
                room.level.getServer().getCommands().performPrefixedCommand(room.player.createCommandSourceStack()
                        .withSource(ConnectedTestPlayers.capture(status)), "arce atmosphere status");
                helper.assertTrue(status.stream().anyMatch(line -> line.startsWith("Atmosphere=BREATHABLE")),
                        "Player status command did not confirm breathing: " + status);
                helper.assertTrue(AtmosphereRuntime.suppliedVolumes(room.level).values().stream()
                        .anyMatch(volume -> volume.cells().size() == 27), "Fixture is not a supplied 27-cell room");
                AnalyzerReading direct = AtmosphereAnalyzerRuntime.read(room.player);
                helper.assertTrue(direct.state() == AnalyzerReading.State.BREATHABLE && direct.supplied(),
                        "Direct runtime read disagreed with authoritative room: " + direct);
                room.assertAmbient(direct);
                var metrics = AtmosphereRuntime.metrics(room.level);
                int energy = room.vent.energyStored(), oxygen = room.vent.oxygenUnits();
                for (InteractionHand hand : InteractionHand.values()) {
                    room.click(room.door, hand, direct);
                    room.click(room.door.above(), hand, direct);
                }
                helper.assertTrue(metrics.equals(AtmosphereRuntime.metrics(room.level))
                                && energy == room.vent.energyStored() && oxygen == room.vent.oxygenUnits(),
                        "Analyzer door measurement invalidated, scanned or spent room resources");
                helper.assertTrue(!room.level.getBlockState(room.door).getValue(DoorBlock.OPEN)
                        && !room.level.getBlockState(room.door.above()).getValue(DoorBlock.OPEN),
                        "Analyzer activated the airlock");
                // Leaving the volume must not reuse the preceding positive reading.
                room.player.teleportTo(room.level, room.origin.getX() + 0.5, room.origin.getY() + 1,
                        room.origin.getZ() + 3.5, 0, 0);
                AnalyzerReading outside = AtmosphereAnalyzerRuntime.read(room.player);
                helper.assertTrue(outside.state() == AnalyzerReading.State.NON_BREATHABLE && !outside.supplied(),
                        "Outside inherited room supply");
                room.assertAmbient(outside);
                room.click(room.door, InteractionHand.MAIN_HAND, outside);
                room.standInside();
                // A real state change must still revoke supply synchronously.
                ((DoorBlock) room.level.getBlockState(room.door).getBlock()).setOpen(room.player, room.level,
                        room.level.getBlockState(room.door), room.door, true);
                AnalyzerReading open = AtmosphereAnalyzerRuntime.read(room.player);
                helper.assertTrue(open.state() != AnalyzerReading.State.BREATHABLE && !open.supplied(),
                        "Opening the airlock retained positive authority");
                room.click(room.door, InteractionHand.MAIN_HAND, open);
                finished[0] = true;
                room.close();
                helper.succeed();
            } catch (RuntimeException failed) {
                finished[0] = true;
                room.close();
                throw failed;
            }
        });
    }

    private static final class Room implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final BlockPos door;
        private final Set<ChunkPos> forced = new LinkedHashSet<>();
        private final List<String> replies = new ArrayList<>(), tokens = new ArrayList<>();
        private ServerPlayer player;
        private OxygenVentBlockEntity vent;

        private Room(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(level != null, "Moon is unavailable");
            BlockPos base = helper.absolutePos(new BlockPos(5, 1, 5));
            origin = new BlockPos(base.getX(), 96, base.getZ());
            door = origin.offset(0, 1, 2);
            try {
                for (int x = (origin.getX() - 2) >> 4; x <= (origin.getX() + 2) >> 4; x++) {
                    for (int z = (origin.getZ() - 2) >> 4; z <= (origin.getZ() + 3) >> 4; z++) {
                        ChunkPos chunk = new ChunkPos(x, z);
                        if (!level.getForcedChunks().contains(chunk.toLong())) {
                            level.setChunkForced(x, z, true);
                            forced.add(chunk);
                        }
                        level.getChunk(x, z); // Bounded fixture setup, never query-side loading.
                    }
                }
                for (int x = -2; x <= 2; x++) { for (int y = 0; y <= 4; y++) { for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0;
                    level.setBlock(origin.offset(x, y, z), (y == 4 ? Blocks.GLASS : wall ? Blocks.IRON_BLOCK : Blocks.AIR)
                            .defaultBlockState(), Block.UPDATE_ALL);
                } } }
                var doorState = ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
                level.setBlock(door, doorState, Block.UPDATE_ALL);
                level.setBlock(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
                level.setBlock(origin, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
                vent = (OxygenVentBlockEntity) level.getBlockEntity(origin);
                var items = vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
                helper.assertTrue(items.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false).isEmpty(),
                        "Vent refused oxygen fixture");
                OxygenVentBlockEntity.serverTick(level, origin, vent.getBlockState(), vent);
                vent.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                        .receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
                player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), "analyzerRoom", level,
                        origin.above(), replies, false, tokens);
                standInside();
            } catch (RuntimeException failed) { close(); throw failed; }
        }

        private void standInside() {
            player.teleportTo(level, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
        }

        private BlockPos eye() { return BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()); }

        private void assertAmbient(AnalyzerReading reading) {
            helper.assertTrue(reading.ambient().orElseThrow().pressure() == 0.0D
                    && reading.ambient().orElseThrow().temperatureKelvin() == 220.0D, "Room changed Moon ambient metadata");
        }

        private void click(BlockPos target, InteractionHand hand, AnalyzerReading expected) {
            var item = ModItems.ATMOSPHERE_ANALYZER.get();
            ItemStack stack = new ItemStack(item);
            stack.getOrCreateTag().putString("note", "measurement data");
            var before = stack.copy();
            player.setItemInHand(hand, stack);
            player.getCooldowns().removeCooldown(item);
            replies.clear(); tokens.clear();
            var hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, false);
            var result = player.gameMode.useItemOn(player, level, stack, hand, hit);
            // Vanilla falls back to air-use after a block/item PASS (the original analyzer path).
            if (!result.consumesAction()) { player.gameMode.useItem(player, level, stack, hand); }
            String token = ConnectedTestPlayers.token(AtmosphereAnalyzerFeedback.message(expected));
            helper.assertTrue(tokens.equals(List.of(token)), "Native analyzer click disagreed with direct eye reading: actual="
                    + tokens + " expected=" + token);
            helper.assertTrue(ItemStack.matches(before, stack) && player.getCooldowns().isOnCooldown(item),
                    "Native measurement mutated held data or skipped cooldown");
        }

        @Override public void close() {
            try {
                if (player != null) { level.getServer().getPlayerList().remove(player); player = null; }
            } finally {
                try {
                    for (int x = -2; x <= 2; x++) { for (int y = 0; y <= 4; y++) { for (int z = -2; z <= 2; z++) {
                        level.removeBlock(origin.offset(x, y, z), false);
                    } } }
                } finally { for (ChunkPos chunk : forced) { level.setChunkForced(chunk.x, chunk.z, false); } }
            }
        }
    }
}
