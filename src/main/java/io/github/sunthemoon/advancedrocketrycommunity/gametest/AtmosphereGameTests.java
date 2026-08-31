package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereGameTests {
    private static final String TEMPLATE = "atmosphere_test";

    private AtmosphereGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void sealedRoomBecomesBreathable(GameTestHelper helper) {
        BlockPos ventPosition = new BlockPos(3, 1, 3);
        OxygenVentBlockEntity vent = buildOneCellRoom(helper, ventPosition);
        AtmosphereLevelService service = testService(helper, AtmosphereLimits.MAX_VOLUME_CELLS);

        service.observeVent(vent);
        AtmosphereLevelMetrics metrics = service.tick();

        helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "Sealed room Vent did not activate");
        helper.assertTrue(
                service.breathabilityAt(helper.absolutePos(ventPosition.above())) == BreathabilityState.BREATHABLE,
                "Sealed room was not authoritative breathable space"
        );
        helper.assertTrue(metrics.activeProviders() == 1, "Sealed room did not elect exactly one provider");
        helper.assertTrue(metrics.lastTickInspections() <= AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK,
                "Sealed scan exceeded its per-Level budget");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void openRoomFailsClosed(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable for open-room test");
        BlockPos allocation = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos ventPosition = new BlockPos(allocation.getX(), 16, allocation.getZ());
        OxygenVentBlockEntity vent = buildOneCellRoom(moon, ventPosition);
        moon.setBlock(ventPosition.offset(0, 2, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        AtmosphereLevelService service = testService(moon, AtmosphereLimits.MAX_VOLUME_CELLS);

        service.observeVent(vent);
        service.tick();

        BlockPos seed = ventPosition.above();
        helper.assertTrue(vent.status() == VentOperatingStatus.OPEN,
                "Open room was not diagnosed as open: status=" + vent.status()
                        + " seedSky=" + moon.canSeeSky(seed)
                        + " roof=" + moon.getBlockState(seed.above()));
        helper.assertTrue(
                service.breathabilityAt(seed) == BreathabilityState.VACUUM,
                "Open room was incorrectly breathable"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void doorChangeInvalidatesAndRecovers(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable for door test");
        BlockPos allocation = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos ventPosition = new BlockPos(allocation.getX(), 16, allocation.getZ());
        OxygenVentBlockEntity vent = buildOneCellRoom(moon, ventPosition);
        BlockPos doorPosition = ventPosition.offset(1, 1, 0);
        buildShell(moon, ventPosition.offset(1, 0, -1), ventPosition.offset(4, 2, 1));
        moon.setBlock(ventPosition.offset(3, 2, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        moon.setBlock(doorPosition, Blocks.IRON_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
        AtmosphereLevelService service = testService(moon, AtmosphereLimits.MAX_VOLUME_CELLS);

        // Cross-dimension block placement updates the fresh chunk heightmap at
        // the end of the server tick. Exercise the door transition only after
        // that normal world lifecycle boundary instead of reading stale sky
        // exposure in the setup tick.
        helper.runAfterDelay(1, () -> {
            service.observeVent(vent);
            service.tick();
            helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE,
                    "Closed door did not seal the room: status=" + vent.status()
                            + " energy=" + vent.energyStored()
                            + " oxygen=" + vent.oxygenUnits()
                            + " door=" + moon.getBlockState(doorPosition)
                            + " roof=" + moon.getBlockState(ventPosition.above(2))
                            + " seedSky=" + moon.canSeeSky(ventPosition.above())
                            + " metrics=" + service.metrics());

            setDoor(moon, doorPosition, true);
            service.markDirty(doorPosition);
            service.observeVent(vent);
            service.tick();
            helper.assertTrue(vent.status() == VentOperatingStatus.OPEN,
                    "Opening the door did not fail closed: status=" + vent.status()
                            + " door=" + moon.getBlockState(doorPosition)
                            + " corridorSky=" + moon.canSeeSky(ventPosition.offset(3, 1, 0)));

            setDoor(moon, doorPosition, false);
            service.markDirty(doorPosition);
            service.observeVent(vent);
            service.tick();
            helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE,
                    "Closing the door did not rebuild the sealed room");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void oversizedRoomStopsAtHardLimit(GameTestHelper helper) {
        BlockPos ventPosition = new BlockPos(4, 1, 4);
        buildShell(helper, new BlockPos(2, 1, 2), new BlockPos(6, 5, 6));
        OxygenVentBlockEntity vent = placePreparedVent(helper, ventPosition);
        AtmosphereLevelService service = testService(helper, 8);

        service.observeVent(vent);
        service.tick();

        helper.assertTrue(vent.status() == VentOperatingStatus.TOO_LARGE,
                "Oversized room did not stop at the configured hard limit");
        helper.assertTrue(service.metrics().indexedCells() == 0,
                "Oversized room leaked a partial authoritative index");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void unloadedObservationCreatesNoChunkTicket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(25_000_000, level.getMinBuildHeight() + 8, 25_000_000);
        helper.assertTrue(!level.hasChunkAt(far), "Far test chunk was unexpectedly loaded before observation");
        ServerLevelVolumeWorldView view = new ServerLevelVolumeWorldView(level, true);
        VolumePosition position = new VolumePosition(far.getX(), far.getY(), far.getZ());

        helper.assertTrue(view.observe(position) == CellObservation.UNLOADED,
                "Unloaded world observation did not return UNLOADED");
        VolumeScanTask task = new VolumeScanTask(position);
        task.step(view, 1);

        helper.assertTrue(task.outcome() == VolumeScanOutcome.PENDING,
                "Unloaded scan did not suspend as PENDING");
        helper.assertTrue(!level.hasChunkAt(far), "Atmosphere observation force-loaded a chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void disconnectedRoomsRemainIndependent(GameTestHelper helper) {
        OxygenVentBlockEntity first = buildOneCellRoom(helper, new BlockPos(2, 1, 2));
        OxygenVentBlockEntity second = buildOneCellRoom(helper, new BlockPos(10, 1, 2));
        AtmosphereLevelService service = testService(helper, AtmosphereLimits.MAX_VOLUME_CELLS);

        service.observeVent(first);
        service.observeVent(second);
        AtmosphereLevelMetrics metrics = service.tick();

        helper.assertTrue(metrics.indexedVolumes() == 2, "Disconnected rooms merged into one volume");
        helper.assertTrue(metrics.activeProviders() == 2, "Disconnected rooms did not retain two providers");
        helper.assertTrue(!service.volumeAt(first.getBlockPos().above()).orElseThrow().id().equals(
                service.volumeAt(second.getBlockPos().above()).orElseThrow().id()
        ), "Disconnected room identities collided");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void sharedRoomElectsOnlyOneVent(GameTestHelper helper) {
        buildShell(helper, new BlockPos(1, 1, 1), new BlockPos(7, 3, 3));
        OxygenVentBlockEntity first = placePreparedVent(helper, new BlockPos(2, 1, 2));
        OxygenVentBlockEntity second = placePreparedVent(helper, new BlockPos(6, 1, 2));
        int energyBefore = first.energyStored() + second.energyStored();
        AtmosphereLevelService service = testService(helper, AtmosphereLimits.MAX_VOLUME_CELLS);

        service.observeVent(first);
        service.observeVent(second);
        AtmosphereLevelMetrics metrics = service.tick();

        helper.assertTrue(metrics.indexedVolumes() == 1, "Shared-room Vents did not deduplicate their volume");
        helper.assertTrue(metrics.activeProviders() == 1, "Shared room elected more than one provider");
        helper.assertTrue(
                (first.status() == VentOperatingStatus.ACTIVE && second.status() == VentOperatingStatus.STANDBY)
                        || (second.status() == VentOperatingStatus.ACTIVE && first.status() == VentOperatingStatus.STANDBY),
                "Shared-room Vent statuses do not expose active/standby election"
        );
        helper.assertTrue(
                energyBefore - first.energyStored() - second.energyStored()
                        == AtmosphereLimits.VENT_ENERGY_PER_TICK,
                "Shared-room providers consumed more than one Vent tick of energy"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void ventCapabilitiesAndFutureSchemaAreBounded(GameTestHelper helper) {
        OxygenVentBlockEntity vent = placePreparedVent(helper, new BlockPos(3, 1, 3));
        helper.assertTrue(vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
                "Vent top item capability is missing");
        helper.assertTrue(vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).isPresent(),
                "Vent bottom item capability is missing");
        helper.assertTrue(vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(),
                "Vent side energy capability is missing");
        helper.assertTrue(!vent.getCapability(ForgeCapabilities.ENERGY, Direction.UP).isPresent(),
                "Vent exposed energy on its item-input face");

        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", OxygenVentPersistence.SCHEMA_VERSION + 1);
        future.putString("future_marker", "preserve-exactly");
        CompoundTag parent = new CompoundTag();
        parent.put(OxygenVentPersistence.DATA_KEY, future.copy());
        vent.load(parent);
        helper.assertTrue(vent.status() == VentOperatingStatus.UNSUPPORTED_DATA,
                "Future Vent schema did not block operation");
        helper.assertTrue(
                vent.saveWithoutMetadata().getCompound(OxygenVentPersistence.DATA_KEY).equals(future),
                "Future Vent payload was not preserved exactly"
        );
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void registeredSuitConsumesOxygenAndVacuumDamagesPlayer(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable for vacuum player test");
        DamageableFakePlayer player = new DamageableFakePlayer(moon);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(20.0F);

        ItemStack chest = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
        helper.assertTrue(SpaceSuitArmorItem.countEquippedPieces(player) == 0,
                "Wrong-slot suit pieces were counted as valid protection");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
        ModItems.OXYGEN_CANISTER.get().use(moon, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == AtmosphereLimits.OXYGEN_UNITS_PER_CANISTER,
                "Registered oxygen canister did not refill the suit chest piece");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_SUIT_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SPACE_SUIT_BOOTS.get()));

        AtmosphereManager atmosphere = new AtmosphereManager(
                new CelestialEnvironmentService(new CelestialCatalogManager())
        );
        List<PlayerLifeSupportSnapshot> snapshots = new ArrayList<>();
        PlayerLifeSupportService lifeSupport = new PlayerLifeSupportService(
                atmosphere,
                (ignored, snapshot) -> snapshots.add(snapshot)
        );
        for (int tick = 0; tick < 20; tick++) {
            lifeSupport.tickPlayer(player);
        }
        helper.assertTrue(SpaceSuitOxygen.read(chest).oxygenUnits() == 999,
                "Complete registered suit did not consume exactly one oxygen unit per second");
        helper.assertTrue(player.getHealth() == 20.0F, "Oxygenated complete suit took vacuum damage");

        SpaceSuitOxygen.set(chest, 0);
        for (int tick = 0; tick < 20; tick++) {
            lifeSupport.tickPlayer(player);
        }
        helper.assertTrue(player.getHealth() == 18.0F,
                "Empty suit did not take exactly one configured vacuum damage interval: health="
                        + player.getHealth()
                        + " invulnerable=" + player.getAbilities().invulnerable
                        + " bypassesArmor=" + io.github.sunthemoon.advancedrocketrycommunity.registry.ModDamageTypes
                        .vacuum(moon).is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR));
        helper.assertTrue(!snapshots.isEmpty()
                        && snapshots.get(snapshots.size() - 1).status() == PlayerProtectionStatus.OXYGEN_EMPTY,
                "Player status did not expose empty suit oxygen");
        lifeSupport.clear();
        atmosphere.clear();
        helper.succeed();
    }

    private static AtmosphereLevelService testService(GameTestHelper helper, int maxVolumeCells) {
        return testService(helper.getLevel(), maxVolumeCells);
    }

    private static AtmosphereLevelService testService(ServerLevel level, int maxVolumeCells) {
        return new AtmosphereLevelService(
                level,
                false,
                true,
                maxVolumeCells,
                AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
        );
    }

    private static OxygenVentBlockEntity buildOneCellRoom(GameTestHelper helper, BlockPos ventPosition) {
        buildShell(
                helper,
                ventPosition.offset(-1, 0, -1),
                ventPosition.offset(1, 2, 1)
        );
        return placePreparedVent(helper, ventPosition);
    }

    private static OxygenVentBlockEntity buildOneCellRoom(ServerLevel level, BlockPos ventPosition) {
        buildShell(
                level,
                ventPosition.offset(-1, 0, -1),
                ventPosition.offset(1, 2, 1)
        );
        return placePreparedVent(level, ventPosition);
    }

    private static void buildShell(GameTestHelper helper, BlockPos minimum, BlockPos maximum) {
        for (int x = minimum.getX(); x <= maximum.getX(); x++) {
            for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                    boolean boundary = x == minimum.getX() || x == maximum.getX()
                            || y == minimum.getY() || y == maximum.getY()
                            || z == minimum.getZ() || z == maximum.getZ();
                    helper.setBlock(new BlockPos(x, y, z), boundary ? Blocks.IRON_BLOCK : Blocks.AIR);
                }
            }
        }
    }

    private static void buildShell(ServerLevel level, BlockPos minimum, BlockPos maximum) {
        for (int x = minimum.getX(); x <= maximum.getX(); x++) {
            for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                    boolean boundary = x == minimum.getX() || x == maximum.getX()
                            || y == minimum.getY() || y == maximum.getY()
                            || z == minimum.getZ() || z == maximum.getZ();
                    level.setBlock(
                            new BlockPos(x, y, z),
                            (boundary ? Blocks.IRON_BLOCK : Blocks.AIR).defaultBlockState(),
                            Block.UPDATE_ALL
                    );
                }
            }
        }
    }

    private static OxygenVentBlockEntity placePreparedVent(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
        OxygenVentBlockEntity vent = (OxygenVentBlockEntity) helper.getBlockEntity(position);
        IItemHandler input = vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Missing Vent input capability"));
        ItemStack remainder = input.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
        if (!remainder.isEmpty()) {
            throw new IllegalStateException("Could not insert Vent test oxygen");
        }
        OxygenVentBlockEntity.serverTick(
                helper.getLevel(),
                helper.absolutePos(position),
                vent.getBlockState(),
                vent
        );
        refillEnergy(vent);
        return vent;
    }

    private static OxygenVentBlockEntity placePreparedVent(ServerLevel level, BlockPos position) {
        level.setBlock(position, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
        OxygenVentBlockEntity vent = (OxygenVentBlockEntity) level.getBlockEntity(position);
        if (vent == null) {
            throw new IllegalStateException("Missing placed Vent BlockEntity");
        }
        IItemHandler input = vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Missing Vent input capability"));
        ItemStack remainder = input.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
        if (!remainder.isEmpty()) {
            throw new IllegalStateException("Could not insert Vent test oxygen");
        }
        OxygenVentBlockEntity.serverTick(level, position, vent.getBlockState(), vent);
        refillEnergy(vent);
        return vent;
    }

    private static void refillEnergy(OxygenVentBlockEntity vent) {
        IEnergyStorage energy = vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Missing Vent energy capability"));
        energy.receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
    }

    private static void setDoor(GameTestHelper helper, BlockPos position, boolean open) {
        BlockState state = helper.getBlockState(position).setValue(BlockStateProperties.OPEN, open);
        helper.getLevel().setBlock(helper.absolutePos(position), state, Block.UPDATE_CLIENTS);
    }

    private static void setDoor(ServerLevel level, BlockPos position, boolean open) {
        BlockState state = level.getBlockState(position).setValue(BlockStateProperties.OPEN, open);
        level.setBlock(position, state, Block.UPDATE_CLIENTS);
    }

    private static final class DamageableFakePlayer extends FakePlayer {
        private DamageableFakePlayer(ServerLevel level) {
            super(level, new GameProfile(
                    UUID.fromString("a78caef4-5885-4fef-a7a8-0bf91b73ae44"),
                    "ARCEAtmosphereTest"
            ));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }
    }
}
