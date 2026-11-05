package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.CombustionTorches;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real save/unload/reload without players: FULL chunks need not have ticked their oxygen vent yet. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TorchRoomReloadGameTests {
    private static final TicketType<ChunkPos> LOADED_ONLY = TicketType.create("arce_gametest_torch_reload",
            Comparator.comparingLong(ChunkPos::toLong));

    private TorchRoomReloadGameTests() { }

    @GameTest(template = "rocket_test", batch = "torch_room_reload", timeoutTicks = 600)
    public static void suppliedTorchSurvivesNativeReloadBeforeVentTicks(GameTestHelper helper) {
        reload(helper, Ending.SUPPLIED);
    }

    @GameTest(template = "rocket_test", batch = "torch_room_reload", timeoutTicks = 600)
    public static void reloadedTorchGoesOutWhenRoomIsOpened(GameTestHelper helper) {
        reload(helper, Ending.OPEN);
    }

    @GameTest(template = "rocket_test", batch = "torch_room_reload", timeoutTicks = 600)
    public static void reloadedTorchGoesOutWhenSupplyStops(GameTestHelper helper) {
        reload(helper, Ending.NO_SUPPLY);
    }

    private static void reload(GameTestHelper helper, Ending ending) {
        var moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Configured Moon Level is absent");
        // Keep these native unloads independent: adjacent tests' FULL-only tickets must not retain another room.
        ChunkPos area = new ChunkPos(10_000 + ending.ordinal() * 16, 10_000);
        UnlitTorchFixture f = new UnlitTorchFixture(helper, moon,
                new BlockPos(area.getMinBlockX() + 2, 180, area.getMinBlockZ() + 7),
                new AABB(area.getMinBlockX(), 176, area.getMinBlockZ(), area.getMaxBlockX() + 1, 186,
                        area.getMaxBlockZ() + 1), true);
        BlockPos vent = f.origin, torch = vent.offset(1, 1, 0);
        ChunkPos centre = new ChunkPos(vent);
        List<ChunkPos> tickets = new ArrayList<>();
        f.onClose(() -> tickets.forEach(chunk ->
                f.level.getChunkSource().removeRegionTicket(LOADED_ONLY, chunk, 0, chunk)));
        OxygenVentBlockEntity[] entity = {null};
        int[] phase = {0}, ticks = {0};
        GameTestTickPacer pacer = new GameTestTickPacer(f.level.getServer());
        f.run(() -> {
            for (int x = -2; x <= 2; x++) { for (int y = 0; y <= 2; y++) { for (int z = -1; z <= 1; z++) {
                boolean inside = Math.abs(x) <= 1 && y == 1 && z == 0;
                f.set(vent.offset(x, y, z), inside ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
            } } }
            f.set(vent, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            entity[0] = AtmosphereGameTests.placePreparedVent(f.level, vent);
        }, () -> {
            switch (phase[0]) {
                case 0 -> {
                    tickVent(f, entity[0]);
                    if (!supplied(f, torch)) { return false; }
                    f.set(torch, Blocks.TORCH.defaultBlockState());
                    phase[0] = 1;
                }
                case 1 -> {
                    tickVent(f, entity[0]);
                    helper.assertTrue(f.level.getBlockState(torch).is(Blocks.TORCH), "Supplied torch went out before unload");
                    if (!supplied(f, torch) || ++ticks[0] < 10) { return false; }
                    f.releaseForce();
                    phase[0] = 2;
                }
                case 2 -> {
                    GameTestChunkUnloadPump.drainReady(f.level);
                    if (f.level.getChunkSource().getChunkNow(centre.x, centre.z) != null || !entity[0].isRemoved()) {
                        pacer.pace();
                        return false;
                    }
                    for (int dx = -1; dx <= 1; dx++) { for (int dz = -1; dz <= 1; dz++) {
                        ChunkPos chunk = new ChunkPos(centre.x + dx, centre.z + dz);
                        f.level.getChunkSource().addRegionTicket(LOADED_ONLY, chunk, 0, chunk);
                        tickets.add(chunk);
                        f.level.getChunk(chunk.x, chunk.z);
                    } }
                    OxygenVentBlockEntity loaded = (OxygenVentBlockEntity) f.level.getBlockEntity(vent);
                    helper.assertTrue(loaded != null && loaded != entity[0], "Reload did not deserialize a new vent");
                    entity[0] = loaded;
                    helper.assertTrue(f.level.players().isEmpty() && f.level.getBlockState(torch).is(Blocks.TORCH),
                            "Saved torch was not lit, or a player is present");
                    // Native neighbor notification queues the restored torch without a player placement.
                    f.level.updateNeighborsAt(torch.below(), Blocks.IRON_BLOCK);
                    ticks[0] = 0;
                    phase[0] = 3;
                }
                case 3 -> {
                    helper.assertTrue(f.level.getBlockState(torch).is(Blocks.TORCH),
                            "Reload extinguished the torch before room supply was rebuilt: " + AtmosphereRuntime.metrics(f.level));
                    if (++ticks[0] < 10) { return false; }
                    helper.assertTrue(!supplied(f, torch) && CombustionTorches.metrics(f.level).orElseThrow().deferrals() > 0,
                            "Fixture did not exercise the unknown atmosphere interval");
                    phase[0] = 4;
                }
                case 4 -> {
                    tickVent(f, entity[0]);
                    if (!supplied(f, torch)) { return false; }
                    helper.assertTrue(f.level.getBlockState(torch).is(Blocks.TORCH), "Rebuilt room did not retain its lit torch");
                    if (ending == Ending.SUPPLIED) { return true; }
                    if (ending == Ending.OPEN) {
                        f.set(vent.offset(-2, 1, 0), Blocks.AIR.defaultBlockState());
                    } else {
                        f.set(vent, Blocks.IRON_BLOCK.defaultBlockState());
                        AtmosphereRuntime.remove(f.level, vent);
                    }
                    // Direct fixture edits notify the same authority that player edits invalidate.
                    AtmosphereRuntime.invalidateDoorBoundary(f.level, vent.offset(-2, 1, 0), vent);
                    phase[0] = 5;
                }
                case 5 -> {
                    if (ending == Ending.OPEN) { tickVent(f, entity[0]); }
                    return f.level.getBlockState(torch).is(ModBlocks.UNLIT_TORCH.get());
                }
                default -> throw new IllegalStateException("Unknown reload phase");
            }
            return false;
        }, () -> helper.assertTrue(ending == Ending.SUPPLIED ? supplied(f, torch) : !supplied(f, torch),
                "Final atmosphere differs from " + ending));
    }

    private static void tickVent(UnlitTorchFixture f, OxygenVentBlockEntity vent) {
        OxygenVentBlockEntity.serverTick(f.level, vent.getBlockPos(), vent.getBlockState(), vent);
    }

    private static boolean supplied(UnlitTorchFixture f, BlockPos torch) {
        return AtmosphereAnalyzerRuntime.readAt(f.level, torch).state() == AnalyzerReading.State.BREATHABLE;
    }

    private enum Ending { SUPPLIED, OPEN, NO_SUPPLY }
}
