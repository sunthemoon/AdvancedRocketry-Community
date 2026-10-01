package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.audit.EndgameAudit;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.IntentKind;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDraw;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTable;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** ADR-055 logical mode on a running server: a drill on a station, its operations, warps, stops and intents. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrbitalLaserDrillGameTests {
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_gametest_laser_drill",
            Comparator.comparing(UUID::toString));

    private OrbitalLaserDrillGameTests() {
    }

    @GameTest(template = "empty", batch = "endgame_laser_drill", timeoutTicks = 400)
    public static void aDrillOnAStationMinesItsOrbitBodyAndAWarpChangesTheTable(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        long seed = drill.seed();
        drill.storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill.storage().setEnergy(200_000);
        fixture.startAsOwner(helper);
        int[] chunks = new int[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(drill.operationIndex() == 1, "No first operation: "
                        + drill.describe()))
                .thenExecute(() -> {
                    helper.assertTrue(drill.storage().energy() == 190_000, "The cost was not 10,000 FE");
                    assertOutput(helper, drill, draw(CelestialIds.EARTH_ID, seed, 0));
                    helper.assertTrue(drill.lastTable().orElse("").startsWith("advancedrocketrycommunity:earth@"),
                            "Not the Earth table: " + drill.lastTable());
                    chunks[0] = fixture.space.getChunkSource().getLoadedChunksCount();
                    chunks[1] = helper.getLevel().getChunkSource().getLoadedChunksCount();
                    // The station warps to the Moon; the next operation re-reads the live orbit body.
                    fixture.stations.foldWarpCredits(Map.of(fixture.stationId, 2_000_000));
                    helper.assertTrue(fixture.stations.checkedRelocation(fixture.server, fixture.station(),
                                    CelestialIds.MOON_ID, 2_000_000) == StationRegistrySavedData.CheckedUpdate.COMMITTED,
                            "The relocation fixture failed");
                })
                .thenWaitUntil(() -> helper.assertTrue(drill.operationIndex() == 2, "No second operation"))
                .thenExecute(() -> {
                    helper.assertTrue(drill.lastTable().orElse("").startsWith("advancedrocketrycommunity:moon@"),
                            "The warp did not change the table: " + drill.lastTable());
                    helper.assertTrue(drill.storage().energy() == 180_000, "The second cost was not 10,000 FE");
                    LaserDrillTable.Entry second = draw(CelestialIds.MOON_ID, seed, 1);
                    helper.assertTrue(contains(drill.storage().output(), second), "The Moon draw is missing");
                    helper.assertTrue(fixture.space.getChunkSource().getLoadedChunksCount() <= chunks[0]
                                    && helper.getLevel().getChunkSource().getLoadedChunksCount() <= chunks[1],
                            "An operation loaded a chunk");
                    helper.assertTrue(audited("action=start result=OK device=" + drill.deviceId().orElseThrow()),
                            "The start is not audited");
                    fixture.close();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "endgame_laser_drill", timeoutTicks = 400)
    public static void aFullOutputPausesAndRetriesTheSameDrawWithoutLoss(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        drill.storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill.storage().setEnergy(200_000);
        ItemStackHandler output = drill.storage().output();
        for (int slot = 0; slot < output.getSlots(); slot++) {
            output.setStackInSlot(slot, new ItemStack(Items.WOODEN_SWORD));
        }
        fixture.startAsOwner(helper);
        LaserDrillTable.Entry expected = draw(CelestialIds.EARTH_ID, drill.seed(), 0);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(drill.status() == EndgameCode.OUTPUT_FULL,
                        "The drill did not pause: " + drill.describe()))
                .thenExecute(() -> {
                    helper.assertTrue(drill.operationIndex() == 0 && drill.storage().energy() == 200_000,
                            "A paused operation took energy or advanced the index");
                    output.setStackInSlot(17, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(drill.operationIndex() == 1, "The retry did not run"))
                .thenExecute(() -> {
                    helper.assertTrue(output.getStackInSlot(17).is(ForgeRegistries.ITEMS.getValue(expected.item()))
                                    && output.getStackInSlot(17).getCount() == expected.count(),
                            "The retry did not deliver the same draw");
                    fixture.close();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "endgame_laser_drill", timeoutTicks = 200)
    public static void intentsAreRefusedForFakesDistanceOtherLevelsStrangersAndRate(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, CelestialIds.EARTH_ID);
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            OrbitalLaserDrillBlockEntity drill = fixture.drill();
            EndgameIntentGuard.Target target = new EndgameIntentGuard.Target(fixture.space.dimension(),
                    fixture.controller, drill.deviceId().orElseThrow());
            EndgameIntentGuard.Intent configure = new EndgameIntentGuard.Intent(EndgameAction.CONFIGURE,
                    IntentKind.STATE, false, false);
            List<String> replies = new ArrayList<>();
            ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "laserOwner", fixture.space,
                    fixture.controller.south(4).above(2), replies);
            joined.add(owner);
            FakePlayer fake = new FakePlayer(fixture.space, new GameProfile(fixture.owner, "laserFake"));
            fake.setPos(owner.getX(), owner.getY(), owner.getZ());
            expect(helper, fake, target, configure, EndgameCode.NOT_A_PLAYER);

            OrbitalLaserDrillMenu menu = new OrbitalLaserDrillMenu(1, owner.getInventory(), drill);
            helper.assertTrue(menu.clickMenuButton(owner, OrbitalLaserDrillMenu.BUTTON_REDSTONE),
                    "The owner's redstone intent was refused");
            helper.assertTrue(!menu.clickMenuButton(owner, OrbitalLaserDrillMenu.BUTTON_REDSTONE)
                    && drill.redstoneMode().name().equals("ON"), "A second intent inside 10 ticks changed state");
            expect(helper, owner, target, configure, EndgameCode.RATE_LIMITED);
            helper.assertTrue(!menu.clickMenuButton(fake, OrbitalLaserDrillMenu.BUTTON_REDSTONE),
                    "A FakePlayer's button was accepted");

            owner.teleportTo(fixture.space, fixture.controller.getX() + 20.5D, fixture.controller.getY(),
                    fixture.controller.getZ() + 0.5D, 0.0F, 0.0F);
            expect(helper, owner, target, configure, EndgameCode.TOO_FAR);
            owner.teleportTo(helper.getLevel(), owner.getX(), 100.0D, owner.getZ(), 0.0F, 0.0F);
            expect(helper, owner, target, configure, EndgameCode.WRONG_LEVEL);

            ServerPlayer stranger = ConnectedTestPlayers.join(fixture.server, UUID.randomUUID(), "laserStranger",
                    fixture.space, fixture.controller.south(4).above(2), new ArrayList<>());
            joined.add(stranger);
            expect(helper, stranger, target, configure, EndgameCode.UNAUTHORIZED);
            OrbitalLaserDrillMenu strangers = new OrbitalLaserDrillMenu(2, stranger.getInventory(), drill);
            helper.assertTrue(!strangers.stillValid(stranger), "A stranger kept a station device menu open");

            EndgameIntentGuard.Target moved = new EndgameIntentGuard.Target(fixture.space.dimension(),
                    fixture.controller, UUID.randomUUID());
            expect(helper, stranger, moved, configure, EndgameCode.DEVICE_CHANGED);
            BlockPos far = fixture.controller.offset(4_800_000, 0, 0);
            expect(helper, stranger, new EndgameIntentGuard.Target(fixture.space.dimension(), far, UUID.randomUUID()),
                    configure, EndgameCode.CHUNK_UNLOADED);
            helper.assertTrue(fixture.space.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                    "The guard loaded a chunk");
        } finally {
            joined.forEach(player -> fixture.server.getPlayerList().remove(player));
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "endgame_laser_drill", timeoutTicks = 200)
    public static void aBrokenStructureStopsTheDrillUntilItIsRepaired(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        BlockPos casing = fixture.controller.south().above();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(drill.structureCode() == EndgameCode.OK, "Not formed: "
                        + drill.describe()))
                .thenExecute(() -> fixture.space.setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState()))
                .thenWaitUntil(() -> helper.assertTrue(drill.structureCode() == EndgameCode.UNFORMED,
                        "A missing casing did not unform the structure"))
                .thenExecute(() -> fixture.space.setBlockAndUpdate(casing,
                        ModBlocks.ENDGAME_CASING.get().defaultBlockState()))
                .thenWaitUntil(() -> helper.assertTrue(drill.structureCode() == EndgameCode.OK,
                        "The repaired structure did not form"))
                .thenExecute(fixture::close)
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "endgame_laser_drill", timeoutTicks = 100)
    public static void malformedRootsAreQuarantinedResavedUnchangedAndInert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState state = ModBlocks.ORBITAL_LASER_DRILL.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        OrbitalLaserDrillBlockEntity drill = (OrbitalLaserDrillBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(drill.deviceId().isPresent() && drill.ownerId().isEmpty(),
                "A /setblock drill must have an ID and no owner");
        CompoundTag valid = drill.saveWithoutMetadata();
        List<Consumer<CompoundTag>> defects = List.of(
                root -> root.putInt("schema_version", 99),
                root -> root.remove("device_id"),
                root -> root.putString("device_id", "not-a-uuid"),
                root -> root.putInt("unexpected", 1),
                root -> root.putInt("energy", 200_001),
                root -> root.putLong("op_index", -1L),
                root -> root.putString("redstone", "SOMETIMES"),
                root -> root.getCompound("output").putInt("Size", 27),
                root -> {
                    CompoundTag item = new CompoundTag();
                    item.putInt("Slot", 0);
                    item.putString("id", "removedmod:ore");
                    item.putByte("Count", (byte) 1);
                    ListTag items = new ListTag();
                    items.add(item);
                    root.getCompound("output").put("Items", items);
                });
        for (Consumer<CompoundTag> defect : defects) {
            CompoundTag bad = valid.copy();
            defect.accept(bad.getCompound("endgame"));
            drill.load(bad);
            helper.assertTrue(drill.quarantined() && drill.deviceId().isEmpty(), "A defective root was accepted: " + bad);
            CompoundTag resaved = drill.saveWithoutMetadata().getCompound("endgame");
            helper.assertTrue(resaved.equals(bad.getCompound("endgame"))
                    && Arrays.equals(bytes(resaved), bytes(bad.getCompound("endgame"))),
                    "A quarantined root was not re-saved unchanged");
            helper.assertTrue(!drill.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    && !drill.getCapability(ForgeCapabilities.ENERGY).isPresent(),
                    "A quarantined drill exposes a capability");
        }
        ServerPlayer player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), "laserBreaker", level,
                pos.east(2), new ArrayList<>());
        try {
            helper.assertTrue(!state.onDestroyedByPlayer(level, pos, player, true, level.getFluidState(pos))
                    && level.getBlockState(pos).is(ModBlocks.ORBITAL_LASER_DRILL.get()),
                    "A non-operator broke a quarantined drill");
            drill.load(valid);
            helper.assertTrue(!drill.quarantined() && drill.deviceId().isPresent(), "The valid root did not load");
            UUID previous = drill.deviceId().orElseThrow();
            FakePlayer fake = new FakePlayer(level, new GameProfile(player.getUUID(), "laserFakePlacer"));
            state.getBlock().setPlacedBy(level, pos, state, fake, ItemStack.EMPTY);
            helper.assertTrue(drill.ownerId().isEmpty() && !drill.deviceId().orElseThrow().equals(previous),
                    "A FakePlayer placement created an owner or kept the old ID");
            state.getBlock().setPlacedBy(level, pos, state, player, ItemStack.EMPTY);
            helper.assertTrue(drill.ownerId().equals(Optional.of(player.getUUID())),
                    "A connected player's placement did not set the owner");
        } finally {
            level.getServer().getPlayerList().remove(player);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "endgame_laser_drill", timeoutTicks = 100)
    public static void energyIsInputOnlyAndBoundedAndAutomationOnlyExtractsOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.ORBITAL_LASER_DRILL.get().defaultBlockState());
        OrbitalLaserDrillBlockEntity drill = (OrbitalLaserDrillBlockEntity) level.getBlockEntity(pos);
        try {
            var energy = drill.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
            helper.assertTrue(energy.receiveEnergy(50_000, true) == 20_000 && energy.getEnergyStored() == 0,
                    "A simulation changed the buffer or passed the per-tick input");
            helper.assertTrue(energy.receiveEnergy(15_000, false) == 15_000
                    && energy.receiveEnergy(15_000, false) == 5_000 && energy.receiveEnergy(1, false) == 0,
                    "More than 20,000 FE entered in one tick");
            helper.assertTrue(energy.extractEnergy(1_000, false) == 0 && !energy.canExtract(),
                    "Energy could be extracted from the drill");
            var items = drill.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN)
                    .orElseThrow(IllegalStateException::new);
            ItemStack lens = new ItemStack(ModItems.LASER_LENS.get());
            helper.assertTrue(items.getSlots() == 18 && items.insertItem(0, lens, false).getCount() == 1,
                    "Automation inserted into the drill");
            drill.storage().output().setStackInSlot(3, new ItemStack(Items.RAW_IRON, 5));
            helper.assertTrue(items.extractItem(3, 64, false).getCount() == 5
                    && drill.storage().output().getStackInSlot(3).isEmpty(), "Automation could not extract output");
            helper.assertTrue(!drill.storage().lens().isItemValid(0, new ItemStack(Items.DIAMOND))
                    && drill.storage().lens().insertItem(0, new ItemStack(ModItems.LASER_LENS.get(), 4), false)
                    .getCount() == 3, "The lens slot accepted something other than one lens");
        } finally {
            drill.storage().reset();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    private static byte[] bytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Parallel tests share the audit ring, so every page is searched. */
    private static boolean audited(String fragment) {
        EndgameAudit audit = EndgameRuntime.operational().orElseThrow().audit();
        for (int page = 0; page < 32; page++) {
            for (String line : audit.page("laser_drill", page)) {
                if (line.contains(fragment)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void expect(GameTestHelper helper, net.minecraft.world.entity.player.Player player,
                               EndgameIntentGuard.Target target, EndgameIntentGuard.Intent intent, EndgameCode code) {
        EndgameCode actual = EndgameIntentGuard.check(player, target, intent).code();
        helper.assertTrue(actual == code, "Expected " + code + " but the guard returned " + actual);
    }

    static LaserDrillTable.Entry draw(ResourceLocation body, long seed, long index) {
        EndgameDevices devices = EndgameRuntime.devices().orElseThrow();
        LaserDrillTable table = devices.laserTables().forBody(devices.celestial().orElseThrow().get(body).orElseThrow())
                .orElseThrow();
        return LaserDraw.draw(table, seed, index);
    }

    private static void assertOutput(GameTestHelper helper, OrbitalLaserDrillBlockEntity drill,
                                     LaserDrillTable.Entry expected) {
        ItemStack first = drill.storage().output().getStackInSlot(0);
        helper.assertTrue(first.is(ForgeRegistries.ITEMS.getValue(expected.item())) && first.getCount() == expected.count(),
                "The output is not the laser-v1 draw " + expected + ": " + first);
    }

    private static boolean contains(ItemStackHandler handler, LaserDrillTable.Entry entry) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(ForgeRegistries.ITEMS.getValue(entry.item()))) {
                total += stack.getCount();
            }
        }
        return total >= entry.count();
    }

    /** A committed station with the 3 × 3 × 3 drill structure near its pad, kept ticking by a fixture ticket. */
    static final class Fixture {
        final MinecraftServer server;
        final ServerLevel space;
        final StationRegistrySavedData stations;
        final UUID stationId = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        final BlockPos controller;
        private final ChunkPos chunk;
        private boolean closed;

        Fixture(GameTestHelper helper, ResourceLocation body) {
            server = helper.getLevel().getServer();
            space = server.getLevel(CelestialIds.SPACE_LEVEL);
            stations = StationRegistrySavedData.get(server);
            stations.reserve(stationId, owner, "Laser fixture", body, helper.getLevel().getGameTime());
            StationState station = stations.commit(stationId);
            controller = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                    station.landingPad().z() + 6);
            chunk = new ChunkPos(controller);
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
            space.getChunkAt(controller); // Test setup only; the drill never loads a chunk.
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
            helper.assertTrue(drill().assignOwner(owner), "The fixture owner was not assigned");
        }

        StationState station() {
            return stations.find(stationId).orElseThrow();
        }

        OrbitalLaserDrillBlockEntity drill() {
            return (OrbitalLaserDrillBlockEntity) space.getBlockEntity(controller);
        }

        /** Starts through the owner's menu button, as a connected player next to the drill. */
        void startAsOwner(GameTestHelper helper) {
            ServerPlayer player = ConnectedTestPlayers.join(server, owner, "laserStarter", space,
                    controller.south(4).above(2), new ArrayList<>());
            try {
                OrbitalLaserDrillMenu menu = new OrbitalLaserDrillMenu(1, player.getInventory(), drill());
                helper.assertTrue(menu.clickMenuButton(player, OrbitalLaserDrillMenu.BUTTON_START)
                        && drill().running(), "The owner could not start the drill");
            } finally {
                server.getPlayerList().remove(player);
            }
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            OrbitalLaserDrillBlockEntity drill = drill();
            if (drill != null) {
                drill.storage().reset();
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 2; dz++) {
                        space.setBlockAndUpdate(controller.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
            stations.delete(stationId);
            stations.flush(server);
        }
    }
}
