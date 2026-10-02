package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.SingularityContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillStorage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The ADR-054 section 7 endgame reference load for the C13 packaged-server hooks
 * ({@link ReferenceLoadReleaseTestCommands}): its devices and how they are built. Around an Overworld column and on
 * stations it creates: 16 logical and 4 physical laser drills, 32 railguns (16 pairs), 16 black-hole generators at
 * Cygnus X-1, 64 gravity fields with 20 test players inside, and 8 elevator pairs; and synthetic root mass up to the
 * reference root (1,024 endpoints, 1,024 tombstones, 256 transfers with the live ones). Owners are stable name-based
 * UUIDs, at most four drills or generators and eight fields each, as the per-owner limits allow.
 */
final class ReferenceLoadFixture {
    static final int FIELD_VISITORS = 20;
    private static final int RAILGUN_PAIRS = 16;
    private static final int LOGICAL_DRILL_OWNERS = 4;
    private static final int PHYSICAL_DRILLS = 4;
    private static final int GENERATOR_STATIONS = 4;
    private static final int FIELD_OWNERS = 8;
    private static final int ELEVATOR_PAIRS = 8;
    private static final int REFERENCE_ENDPOINTS = 1024;
    private static final int REFERENCE_TOMBSTONES = 1024;
    private static final int REFERENCE_TRANSFERS = 192;
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryBuild("minecraft", "overworld");

    private final EndgameService service;
    final Load load = new Load();

    ReferenceLoadFixture(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /** Owner {@code index} of the load: a stable name-based UUID, so a restart finds the same owners. */
    static UUID owner(int index) {
        return UUID.nameUUIDFromBytes(("arce-refload-owner-" + index).getBytes(StandardCharsets.UTF_8));
    }

    static UUID visitor(int index) {
        return UUID.nameUUIDFromBytes(("arce-refload-visitor-" + index).getBytes(StandardCharsets.UTF_8));
    }

    /** The devices of the load, for the driver and the status line. */
    static final class Load {
        final List<Device> drills = new ArrayList<>();
        final List<Device> fields = new ArrayList<>();
        final List<Device> generators = new ArrayList<>();
        final List<Device> railgunSources = new ArrayList<>();
        final List<Device> railgunDestinations = new ArrayList<>();
        final List<Elevator> elevators = new ArrayList<>();
        final List<Device> markers = new ArrayList<>();
        final Map<UUID, Long> nextClick = new HashMap<>();
        boolean built;
        boolean driving;
        long launches;
        long rides;
        int nextRide;
    }

    /** One device: its Level, position and owner, and the menu steps still to click. */
    static final class Device {
        final ServerLevel level;
        final BlockPos pos;
        final UUID owner;
        final List<Integer> steps = new ArrayList<>();
        /** One menu per device: a drill's target selection lives in the menu that made it. */
        AbstractContainerMenu menu;

        Device(ServerLevel level, BlockPos pos, UUID owner) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
        }
    }

    record Elevator(UUID stationId, BlockPos anchor, BlockPos terminal, UUID owner, UUID rider) {
    }

    // ---- Build ------------------------------------------------------------------------------------------------

    /** Builds the whole load around the Overworld column x0, z0; the summary line of what was built. */
    String build(MinecraftServer server, int x0, int z0) {
        ServerLevel overworld = server.overworld();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        int ownerIndex = 0;
        CommonConfig.ENDGAME_LASER_PHYSICAL.set(true);
        // Logical drills: four owners, four drills each, on one station per owner orbiting Earth.
        for (int o = 0; o < LOGICAL_DRILL_OWNERS; o++) {
            UUID owner = owner(ownerIndex++);
            BlockPos pad = station(server, space, owner, CelestialIds.EARTH_ID);
            for (int k = 0; k < 4; k++) {
                load.drills.add(drill(space, pad.offset(6 * k, 0, 0), owner, false));
            }
        }
        // Physical drills: one per owner and station, each with one marker on the Overworld column below.
        for (int p = 0; p < PHYSICAL_DRILLS; p++) {
            UUID owner = owner(ownerIndex++);
            BlockPos pad = station(server, space, owner, CelestialIds.EARTH_ID);
            load.drills.add(drill(space, pad, owner, true));
            BlockPos marker = new BlockPos(x0 + 48 * p, 160, z0 - 96);
            forceChunk(overworld, marker);
            overworld.setBlockAndUpdate(marker, ModBlocks.LASER_TARGET.get().defaultBlockState());
            ((LaserTargetBlockEntity) overworld.getBlockEntity(marker)).assignOwner(owner);
            for (int depth = 1; depth <= 64; depth++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos cell = marker.offset(dx, -depth, dz);
                        if (overworld.getBlockState(cell).isAir() || !overworld.getFluidState(cell).isEmpty()) {
                            overworld.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
                        }
                    }
                }
            }
            load.markers.add(new Device(overworld, marker, owner));
        }
        // Black-hole generators: four owners, four generators each, on a station orbiting Cygnus X-1.
        for (int g = 0; g < GENERATOR_STATIONS; g++) {
            UUID owner = owner(ownerIndex++);
            BlockPos pad = station(server, space, owner, SingularityContent.CYGNUS_X1);
            for (int k = 0; k < 4; k++) {
                load.generators.add(generator(space, pad.offset(6 * k, 0, 0), owner));
            }
        }
        // Gravity fields: eight owners, eight fields each. A radius-8 field's box covers 2 x 2 chunks, so each field
        // gets its own 2 x 2 chunk cell of a 32-block grid (ADR-058 density counts every chunk a box covers).
        int gridX = (x0 >> 4 << 4) + 8;
        int gridZ = ((z0 + 512) >> 4 << 4) + 8;
        for (int f = 0; f < FIELD_OWNERS; f++) {
            UUID owner = owner(ownerIndex++);
            for (int k = 0; k < 8; k++) {
                int cell = 8 * f + k;
                BlockPos pos = new BlockPos(gridX + 32 * (cell % 16), 200, gridZ + 32 * (cell / 16));
                forceChunk(overworld, pos);
                overworld.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                overworld.setBlockAndUpdate(pos, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
                GravityFieldBlockEntity field = (GravityFieldBlockEntity) overworld.getBlockEntity(pos);
                field.assignOwner(owner);
                field.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
                Device device = new Device(overworld, pos, owner);
                device.steps.add(GravityFieldMenu.BUTTON_START);
                load.fields.add(device);
            }
        }
        // Visitors stand inside the fields of the first owners.
        for (int v = 0; v < FIELD_VISITORS; v++) {
            Device field = load.fields.get(v * 3 % load.fields.size());
            ReleaseTestPlayers.place(server, visitor(v), "arceVisitor" + v, overworld,
                    Vec3.atCenterOf(field.pos.above(2)));
        }
        // Railguns: one owner, sixteen pairs 24 blocks apart in z.
        UUID railgunOwner = owner(ownerIndex++);
        for (int r = 0; r < RAILGUN_PAIRS; r++) {
            BlockPos source = new BlockPos(x0 + 16 * r, 200, z0 + 160);
            BlockPos destination = source.offset(0, 0, 24);
            forceChunk(overworld, source);
            forceChunk(overworld, destination);
            RailgunBlockEntity from = EndgameReleaseTestCommands.build(overworld, source, railgunOwner);
            RailgunBlockEntity to = EndgameReleaseTestCommands.build(overworld, destination, railgunOwner);
            from.selectForTest(to.deviceId().orElseThrow(), false);
            load.railgunSources.add(new Device(overworld, source, railgunOwner));
            load.railgunDestinations.add(new Device(overworld, destination, railgunOwner));
        }
        // Elevators: eight pairs, one station per owner, each with a member rider.
        for (int e = 0; e < ELEVATOR_PAIRS; e++) {
            UUID owner = owner(ownerIndex++);
            UUID rider = visitor(FIELD_VISITORS + e);
            BlockPos anchor = new BlockPos(x0 + 32 * e, 200, z0 + 256);
            forceChunk(overworld, anchor);
            ElevatorReleaseTestCommands.Built built = ElevatorReleaseTestCommands.buildPair(server, anchor, owner, rider);
            load.elevators.add(new Elevator(built.stationId(), anchor, built.terminalPos(), owner, rider));
        }
        int synthetic = synthetic(load.drills.size() + load.markers.size() + load.generators.size()
                + load.fields.size() + 2 * RAILGUN_PAIRS + 2 * ELEVATOR_PAIRS);
        load.built = true;
        return "refload built drills=" + load.drills.size() + " markers=" + load.markers.size()
                + " generators=" + load.generators.size() + " fields=" + load.fields.size() + " railguns="
                + 2 * RAILGUN_PAIRS + " elevators=" + load.elevators.size() + " visitors=" + FIELD_VISITORS
                + " synthetic_endpoints=" + synthetic + " owners=" + ownerIndex;
    }

    /** A station of {@code owner} orbiting {@code body}; its landing pad's corner device position, chunk forced. */
    private static BlockPos station(MinecraftServer server, ServerLevel space, UUID owner, ResourceLocation body) {
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, owner, "Reference load", body, server.overworld().getGameTime());
        StationState station = stations.commit(stationId);
        stations.flush(server);
        BlockPos pad = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                station.landingPad().z() + 6);
        for (int k = 0; k < 4; k++) {
            forceChunk(space, pad.offset(6 * k, 0, 0));
        }
        return pad;
    }

    private static void forceChunk(ServerLevel level, BlockPos pos) {
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
    }

    private static Device drill(ServerLevel space, BlockPos controller, UUID owner, boolean physical) {
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = 0; dz <= 2; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        space.setBlockAndUpdate(controller.offset(dx, dy, dz), casing);
                    }
                }
            }
        }
        space.setBlockAndUpdate(controller, ModBlocks.ORBITAL_LASER_DRILL.get().defaultBlockState()
                .setValue(OrbitalLaserDrillBlock.FACING, Direction.NORTH));
        OrbitalLaserDrillBlockEntity drill = (OrbitalLaserDrillBlockEntity) space.getBlockEntity(controller);
        drill.assignOwner(owner);
        drill.storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill.storage().setEnergy(LaserDrillStorage.ENERGY_CAPACITY);
        Device device = new Device(space, controller, owner);
        if (physical) {
            device.steps.addAll(List.of(OrbitalLaserDrillMenu.BUTTON_MODE, OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT,
                    OrbitalLaserDrillMenu.BUTTON_LINK, OrbitalLaserDrillMenu.BUTTON_START,
                    OrbitalLaserDrillMenu.BUTTON_CONFIRM));
        } else {
            device.steps.add(OrbitalLaserDrillMenu.BUTTON_START);
        }
        return device;
    }

    private static Device generator(ServerLevel space, BlockPos controller, UUID owner) {
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos = controller.offset(x - 1, y - 1, z);
                    BlockState state = casing;
                    if (y == 1 && (x == 0 && z == 1 || x == 2 && z == 1 || x == 1 && z == 2)) {
                        state = Blocks.OBSIDIAN.defaultBlockState();
                    } else if (y == 1 && x == 1 && z == 1) {
                        state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                    } else if (y == 1 && x == 1 && z == 0) {
                        state = ModBlocks.BLACK_HOLE_GENERATOR.get().defaultBlockState()
                                .setValue(BlackHoleGeneratorBlock.FACING, Direction.NORTH);
                    }
                    space.setBlockAndUpdate(pos, state);
                }
            }
        }
        BlackHoleGeneratorBlockEntity generator = (BlackHoleGeneratorBlockEntity) space.getBlockEntity(controller);
        generator.assignOwner(owner);
        // No fuel yet: the load stands idle until the driver feeds it.
        return new Device(space, controller, owner);
    }

    /**
     * Synthetic root mass up to the reference root: endpoints of sixteen synthetic owners (64 each) in far chunks that
     * never load, settled tombstones, and transfers between them. Returns the synthetic endpoints added.
     */
    private int synthetic(int liveEndpoints) {
        int endpoints = REFERENCE_ENDPOINTS - liveEndpoints;
        ResourceLocation kind = RailgunBlockEntity.KIND;
        return service.barrier(root -> {
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < endpoints + REFERENCE_TOMBSTONES; i++) {
                UUID id = UUID.nameUUIDFromBytes(("arce-refload-endpoint-" + i).getBytes(StandardCharsets.UTF_8));
                UUID owner = UUID.nameUUIDFromBytes(("arce-refload-synthetic-" + (i / 64))
                        .getBytes(StandardCharsets.UTF_8));
                long pos = new BlockPos(5_000_000 + 64 * (i % 256), 64, 5_000_000 + 64 * (i / 256)).asLong();
                if (root.register(id, kind, owner, OVERWORLD, pos, false, EndgameLimits.MAX_ENDPOINTS, 64)
                        != EndgameCode.OK) {
                    continue;
                }
                if (i < endpoints) {
                    ids.add(id);
                } else {
                    // Settled at once: a young tombstone would hold an endpoint place.
                    root.remove(id);
                    root.settle(id, root::pinned);
                }
            }
            TransitPayload payload = TransitPayload.of(List.of(new ItemStack(Items.IRON_INGOT, 64),
                    new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.DIAMOND, 64),
                    new ItemStack(Items.REDSTONE, 64))).orElseThrow();
            for (int i = 0; i < REFERENCE_TRANSFERS && 2 * i + 1 < endpoints; i++) {
                UUID source = ids.get(2 * i);
                UUID destination = ids.get(2 * i + 1);
                root.registerTransit(TransitRecord.registered(source, new OutboxEntry(1L, destination, payload, 1,
                        600, EndgameSystem.RAILGUN), root.endpoint(source).orElseThrow().owner(), root.saveEpoch(), 0L));
            }
            return ids.size();
        });
    }
}
