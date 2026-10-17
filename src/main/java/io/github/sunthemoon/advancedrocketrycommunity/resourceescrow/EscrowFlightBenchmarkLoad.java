package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The docs/17 load, separate from the endgame-only refload and the saturated IO benchmark. */
final class EscrowFlightBenchmarkLoad {
    static final BlockPos ASSEMBLER = new BlockPos(320, 99, 160);
    final MinecraftServer server;
    final ServerLevel earth, moon;
    final List<ServerPlayer> players = new ArrayList<>();
    private final List<BlockPos> vents = new ArrayList<>();
    private final List<BlockPos> earthSlots = new ArrayList<>(), moonSlots = new ArrayList<>();
    private int built, initialPlayerTicks;
    private boolean initialized;

    EscrowFlightBenchmarkLoad(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        earth = server.overworld(); moon = server.getLevel(CelestialIds.MOON_LEVEL);
        EscrowTags.require(moon != null, "Benchmark requires the Moon Level");
        for (int i = 0; i < 8; i++) { earthSlots.add(source(i)); moonSlots.add(destination(i)); }
        earthSlots.add(new BlockPos(64, 100, 160)); earthSlots.add(new BlockPos(96, 100, 160));
        earthSlots.add(ASSEMBLER); earthSlots.add(ASSEMBLER.offset(7, 16, 15));
        for (int i = 0; i < 16; i++) { vents.add(new BlockPos(256 + (i % 4) * 4, 100, 128 + (i / 4) * 4)); }
        moonSlots.addAll(vents);
        ticket(earth, earthSlots); ticket(moon, moonSlots);
    }
    static UUID id(String name) { return UUID.nameUUIDFromBytes(("arce:governed-benchmark:" + name).getBytes(StandardCharsets.UTF_8)); }
    static BlockPos source(int i) { return new BlockPos(64 + i * 32, 100, 64); }
    static BlockPos destination(int i) { return new BlockPos(64 + i * 32, 100, 64); }
    private static void ticket(ServerLevel level, List<BlockPos> positions) {
        positions.stream().map(ChunkPos::new).distinct().forEach(chunk -> {
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
        });
    }
    boolean loaded() { return loaded(earth, earthSlots) && loaded(moon, moonSlots); }
    private static boolean loaded(ServerLevel level, List<BlockPos> positions) {
        return positions.stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos));
    }
    boolean setup(boolean recovery) {
        if (!loaded()) { return false; }
        if (!initialized) {
            EscrowTags.require(server.getPlayerCount() == 0, "Benchmark must start with no connections");
            for (int i = 0; i < 2; i++) {
                players.add(ReleaseTestPlayers.place(server, id("player-" + i), "escrowBench" + i, earth, new Vec3(64 + i * 32, 104, 72)));
            }
            initialPlayerTicks = playerTicks();
            if (!recovery) {
                var stations = StationRegistrySavedData.get(server);
                EscrowTags.require(stations.stations().isEmpty(), "Benchmark stations are not fresh");
                for (int i = 0; i < 10; i++) {
                    stations.reserve(id("station-" + i), id("station-owner-" + i), "Benchmark " + i,
                            CelestialIds.EARTH_ID, earth.getGameTime()); stations.commit(id("station-" + i));
                }
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "arce satellite release-test batch 100");
                for (var pos : vents) {
                    for (int x = -1; x <= 1; x++) { for (int y = 0; y <= 2; y++) { for (int z = -1; z <= 1; z++) {
                        moon.setBlockAndUpdate(pos.offset(x, y, z), x == 0 && y == 1 && z == 0
                                ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
                    } } }
                    moon.setBlockAndUpdate(pos, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
                }
                earth.setBlockAndUpdate(ASSEMBLER, ModBlocks.ROCKET_ASSEMBLER.get().defaultBlockState());
            }
            refill(); initialized = true;
        }
        if (!recovery && built < 2048) {
            for (int count = 0; count < 128 && built < 2048; count++, built++) {
                int x = built % 8, z = built / 8 % 16, y = built / 128;
                var block = y == 0 ? ModBlocks.ROCKET_MOTOR.get() : y == 1 ? ModBlocks.ROCKET_FUEL_TANK.get() : Blocks.IRON_BLOCK;
                if (x == 0 && z == 0 && y == 2) { block = ModBlocks.ROCKET_SEAT.get(); }
                if (x == 1 && z == 0 && y == 2) { block = ModBlocks.GUIDANCE_COMPUTER.get(); }
                earth.setBlockAndUpdate(ASSEMBLER.offset(x, y + 1, z), block.defaultBlockState());
            }
            return false;
        }
        EscrowTags.require(StationRegistrySavedData.get(server).stations().size() == 10, "Reference load lost stations");
        EscrowTags.require(SatelliteMissionSavedData.get(server).missions().size() == 100, "Reference load lacks 100 tasks");
        return activeVents() == 16 && playerTicks() - initialPlayerTicks >= 40;
    }
    void refill() {
        for (var pos : vents) {
            var vent = (OxygenVentBlockEntity) moon.getBlockEntity(pos);
            EscrowTags.require(vent != null, "Reference vent missing");
            refill(vent);
        }
    }
    static void refill(OxygenVentBlockEntity vent) {
        // Supply through the same automation interfaces as a hopper and an energy cable.
        // Canisters are consumed by the vent's normal ticker, without resetting its scan or phase.
        refill(vent.itemHandler(), vent.getCapability(ForgeCapabilities.ENERGY)
                .orElseThrow(() -> new IllegalStateException("Vent energy absent")), ModItems.OXYGEN_CANISTER.get());
    }
    static void refill(net.minecraftforge.items.IItemHandler items, net.minecraftforge.energy.IEnergyStorage energy,
                       net.minecraft.world.item.Item canister) {
        items.extractItem(OxygenVentBlockEntity.SLOT_EMPTY, 64, false);
        int missing = 4 - items.getStackInSlot(OxygenVentBlockEntity.SLOT_OXYGEN).getCount();
        if (missing > 0) {
            EscrowTags.require(items.insertItem(OxygenVentBlockEntity.SLOT_OXYGEN,
                    new ItemStack(canister, missing), false).isEmpty(), "Vent supply refused");
        }
        energy.receiveEnergy(40000, false);
    }
    int activeVents() {
        return (int) vents.stream().filter(pos -> moon.getBlockEntity(pos) instanceof OxygenVentBlockEntity vent
                && vent.status() == VentOperatingStatus.ACTIVE).count();
    }
    java.util.Map<String, Object> diagnostics() {
        return java.util.Map.of("loaded", loaded(), "active_vents", activeVents(), "player_ticks", tickDelta(),
                "vents", vents.stream().map(pos -> {
                    var vent = (OxygenVentBlockEntity) moon.getBlockEntity(pos);
                    return vent == null ? java.util.Map.of("status", "MISSING")
                            : java.util.Map.of("status", vent.status().name(), "oxygen", vent.oxygenUnits(), "energy", vent.energyStored());
                }).toList());
    }
    int playerTicks() { return players.stream().mapToInt(player -> player.tickCount).sum(); }
    int tickDelta() { return playerTicks() - initialPlayerTicks; }
    void assemble() {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "arce rocket assemble 320 99 160");
    }
    boolean assembled() {
        return earth.getEntitiesOfClass(RocketEntity.class, new AABB(ASSEMBLER).inflate(20)).stream()
                .anyMatch(rocket -> rocket.operational() && rocket.snapshot().orElseThrow().blocks().size() == 2048);
    }
}
