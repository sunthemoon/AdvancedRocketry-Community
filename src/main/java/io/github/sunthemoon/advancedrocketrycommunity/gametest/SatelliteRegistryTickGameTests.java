package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalTargets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * W129c, design section 6.3: the production tick path of the registry writer and its barriers. Nothing in these batches
 * awaits the writer; every publication and terminal effect comes from a later server tick. Each batch runs alone, so
 * the request that produced a commit is known.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteRegistryTickGameTests {
    static final String REQUEST_BATCH = "satellite_registry_tick";
    static final String TERMINAL_BATCH = "satellite_terminal_tick";
    private static final String KEY = "SatelliteTerminal";

    private SatelliteRegistryTickGameTests() { }

    /** ADR-074 items 6 and 9: the coalesced request and an autosave's DataStorage save each return before their commit. */
    @GameTest(template = "empty", batch = REQUEST_BATCH, timeoutTicks = 300)
    public static void coalescedAndAutosaveRequestsArePublishedOnLaterTicks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        UUID solar = UUID.randomUUID(), receiver = UUID.randomUUID();
        data.launchIdle(time -> SatelliteState.launchIdle(solar, ModIdentity.id("solar_satellite"), UUID.randomUUID(),
                time, CelestialIds.EARTH_ID, new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                        ModIdentity.id("solar_transmitter_module")), false, new SatelliteStats(4, 720, 0, 0, 10)),
                new SatelliteKindState.Solar(100, Optional.empty())), server.overworld().getGameTime());
        data.updateKindState(solar, new SatelliteKindState.Solar(100, Optional.of(receiver)));
        long coalescedBefore = SatelliteRuntime.coalescedFlushes();
        SatelliteRegistryProof.Token linked = data.token();
        helper.assertTrue(data.flushPending() && !data.covers(linked), "The link change was not left for the coalesced request");
        SatelliteRegistryProof.Token[] saved = new SatelliteRegistryProof.Token[1];
        long[] coalescedAtSave = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(data.covers(linked) && !data.flushPending(),
                        "No later tick has published the coalesced commit yet"))
                .thenExecute(() -> {
                    helper.assertTrue(SatelliteRuntime.coalescedFlushes() > coalescedBefore,
                            "The commit was not requested by the coalesced flush");
                    helper.assertTrue(disk(server).satellite(solar).orElseThrow().kindState()
                            .equals(new SatelliteKindState.Solar(100, Optional.of(receiver))), "The commit did not carry the link");
                    data.updateKindState(solar, new SatelliteKindState.Solar(100, Optional.empty()));
                    saved[0] = data.token();
                    coalescedAtSave[0] = SatelliteRuntime.coalescedFlushes();
                    // The DataStorage save that autosave and /save-all run requests the write and returns.
                    server.overworld().getDataStorage().save();
                    helper.assertTrue(data.isDirty() && !data.covers(saved[0]), "The autosave waited for the registry's write");
                })
                .thenWaitUntil(() -> helper.assertTrue(data.covers(saved[0]), "No later tick has published the autosave's commit"))
                .thenExecute(() -> {
                    helper.assertTrue(SatelliteRuntime.coalescedFlushes() == coalescedAtSave[0],
                            "The autosave's commit waited for the next coalesced flush");
                    helper.assertTrue(disk(server).satellite(solar).orElseThrow().kindState()
                            .equals(new SatelliteKindState.Solar(100, Optional.empty())), "The autosave's commit missed the change");
                })
                .thenSucceed();
    }

    /**
     * ADR-074 items 6 and 7, design section 5.2: a terminal launch, then a decommission, each changes RAM at once and
     * completes its effect on a tick after the commit that covers it. While one waits, the package and chip slots refuse
     * automation, the terminal refuses player breaking and explosions, and every other state change is refused.
     */
    @GameTest(template = "empty", batch = TERMINAL_BATCH, timeoutTicks = 300)
    public static void aTerminalLaunchAndDecommissionCompleteOnTicksAfterTheirCommits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity terminal = place(helper);
        FakePlayer owner = player(helper, terminal);
        IItemHandler slots = terminal.menuInventory();
        SatelliteIdentity survey = surveyIdentity(owner.getUUID());
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), survey));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), survey));
        power(terminal);
        var menu = new SatelliteTerminalMenu(1, owner.getInventory(), terminal, SatelliteTerminalTargets.current());

        helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_LAUNCH), "The launch was refused");
        var registration = data.watch(survey.satelliteId());
        helper.assertTrue(data.satellite(survey.satelliteId()).isPresent() && !data.proves(registration),
                "The launch did not change RAM before its commit");
        helper.assertTrue(menu.status() == SatelliteOperationCode.REGISTRY_PENDING
                && stored(terminal) == SatelliteOperationCode.SUCCESS, "The waiting launch showed the wrong result");
        protectedWhileWaiting(helper, terminal, owner, slots, "launch");
        long refusedAt = data.mutationSequence();
        helper.assertTrue(!terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_DECOMMISSION)
                        && data.mutationSequence() == refusedAt && data.satellite(survey.satelliteId()).isPresent(),
                "A decommission ran while the launch waited");
        SatelliteRegistryProof.Token[] removal = new SatelliteRegistryProof.Token[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                        "The launch's package still waits for its commit"))
                .thenExecute(() -> {
                    helper.assertTrue(data.proves(registration) && disk(server).satellite(survey.satelliteId()).isPresent(),
                            "The package left before a published commit proved the registration");
                    helper.assertTrue(menu.status() == SatelliteOperationCode.SUCCESS, "The launch's stored result was not shown");
                    removableAgain(helper, terminal);
                    helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_DECOMMISSION),
                            "The decommission was refused");
                    removal[0] = data.token();
                    helper.assertTrue(data.satellite(survey.satelliteId()).isEmpty() && !data.covers(removal[0])
                                    && SatelliteItemData.read(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP))
                                    .identity().filter(survey::equals).isPresent(),
                            "The decommission blanked the chip before its commit or kept the satellite in RAM");
                    protectedWhileWaiting(helper, terminal, owner, slots, "decommission");
                })
                .thenWaitUntil(() -> helper.assertTrue(SatelliteItemData.read(slots.getStackInSlot(
                                SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP)).status() == SatelliteItemData.DecodeStatus.EMPTY,
                        "The chip still waits for the removal's commit"))
                .thenExecute(() -> {
                    helper.assertTrue(data.covers(removal[0]) && disk(server).satellite(survey.satelliteId()).isEmpty(),
                            "The chip was blanked before a published commit covered the removal");
                    helper.assertTrue(menu.status() == SatelliteOperationCode.SUCCESS, "The decommission's result was not shown");
                    removableAgain(helper, terminal);
                })
                .thenSucceed();
    }

    /** ADR-074 item 6: a data action shows its stored RAM result only after a commit covers its fixed token. */
    @GameTest(template = "empty", batch = TERMINAL_BATCH, timeoutTicks = 300)
    public static void aDataActionShowsItsStoredResultAfterItsCommit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteTerminalBlockEntity terminal = place(helper);
        FakePlayer owner = player(helper, terminal);
        IItemHandler slots = terminal.menuInventory();
        power(terminal);
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CHASSIS, new ItemStack(ModItems.SATELLITE_CHASSIS.get()));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_SOLAR_MODULE, new ItemStack(ModItems.SATELLITE_SOLAR_MODULE.get()));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE, new ItemStack(ModItems.DATA_STORAGE_UNIT.get()));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
        var menu = new SatelliteTerminalMenu(1, owner.getInventory(), terminal, SatelliteTerminalTargets.current());
        helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_ASSEMBLE)
                && menu.status() == SatelliteOperationCode.SUCCESS, "The data satellite was not assembled");
        SatelliteIdentity chip = SatelliteItemData.read(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP))
                .identity().orElseThrow();
        helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_LAUNCH)
                && menu.status() == SatelliteOperationCode.REGISTRY_PENDING, "The data launch did not wait for its commit");
        SatelliteRegistryProof.Token[] cancelled = new SatelliteRegistryProof.Token[1];
        UUID[] mission = new UUID[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                        "The data package still waits for its commit"))
                .thenExecute(() -> {
                    mission[0] = data.satellite(chip.satelliteId()).orElseThrow().currentMissionId().orElseThrow();
                    helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_CANCEL), "The cancel was refused");
                    cancelled[0] = data.token();
                    helper.assertTrue(data.mission(mission[0]).orElseThrow().status() == MissionStatus.CANCELLED
                                    && !data.covers(cancelled[0]), "The cancel did not change RAM before its commit");
                    helper.assertTrue(menu.status() == SatelliteOperationCode.REGISTRY_PENDING
                                    && stored(terminal) == SatelliteOperationCode.SUCCESS,
                            "The waiting cancel showed its result early, or a saved root stored the wait");
                    helper.assertTrue(!slots.extractItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 1, true).isEmpty(),
                            "A data action locked the chip");
                    helper.assertTrue(!terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_LAUNCH)
                                    && data.satellite(chip.satelliteId()).orElseThrow().currentMissionId().isEmpty(),
                            "A second data action ran while the cancel waited");
                })
                .thenWaitUntil(() -> helper.assertTrue(menu.status() == SatelliteOperationCode.SUCCESS,
                        "The cancel's stored result still waits for its commit"))
                .thenExecute(() -> helper.assertTrue(data.covers(cancelled[0])
                                && disk(server).mission(mission[0]).orElseThrow().status() == MissionStatus.CANCELLED,
                        "The cancel's result was shown before a published commit covered it"))
                .thenSucceed();
    }

    /** The package and chip refuse automation on every side, and the terminal refuses breaking and explosions. */
    private static void protectedWhileWaiting(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer owner,
                                              IItemHandler slots, String action) {
        for (Direction side : sides()) {
            IItemHandler handler = terminal.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElseThrow();
            helper.assertTrue(handler.extractItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE, 1, false).isEmpty()
                            && handler.extractItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 1, false).isEmpty()
                            && !handler.insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                                    new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()), false).isEmpty(),
                    "Automation from side " + side + " moved a locked item while the " + action + " waited");
        }
        // A real hopper below pulls through the same capability; it moves nothing and is removed before it ticks.
        BlockPos below = new BlockPos(1, 1, 1);
        var floor = helper.getBlockState(below);
        helper.setBlock(below, Blocks.HOPPER);
        try {
            var hopper = (HopperBlockEntity) helper.getBlockEntity(below);
            helper.assertTrue(!HopperBlockEntity.suckInItems(helper.getLevel(), hopper) && hopper.isEmpty(),
                    "A hopper pulled a locked item while the " + action + " waited");
        } finally {
            helper.setBlock(below, floor);
        }
        BlockPos at = terminal.getBlockPos();
        helper.assertTrue(!owner.gameMode.destroyBlock(at)
                        && helper.getLevel().getBlockEntity(at) == terminal && !terminal.isRemoved(),
                "A player broke the terminal while the " + action + " waited");
        var explosion = new Explosion(helper.getLevel(), null, at.getX(), at.getY(), at.getZ(), 4, false,
                Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(at);
        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, new ArrayList<>()));
        helper.assertTrue(!explosion.getToBlow().contains(at), "An explosion kept the terminal while the " + action + " waited");
    }

    private static void removableAgain(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        BlockPos at = terminal.getBlockPos();
        var breaking = new BlockEvent.BreakEvent(helper.getLevel(), at, terminal.getBlockState(), helper.makeMockPlayer());
        MinecraftForge.EVENT_BUS.post(breaking);
        var explosion = new Explosion(helper.getLevel(), null, at.getX(), at.getY(), at.getZ(), 4, false,
                Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(at);
        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, new ArrayList<>()));
        helper.assertTrue(!breaking.isCanceled() && explosion.getToBlow().contains(at),
                "A terminal with nothing waiting still refused breaking or explosions");
    }

    private static List<Direction> sides() {
        var sides = new ArrayList<Direction>(List.of(Direction.values()));
        sides.add(null);
        return sides;
    }

    private static SatelliteMissionSavedData disk(MinecraftServer server) {
        var file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(SatelliteMissionSavedData.DATA_NAME + ".dat");
        try {
            return SatelliteMissionSavedData.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));
        } catch (IOException exception) {
            throw new IllegalStateException("The registry file could not be read", exception);
        }
    }

    private static SatelliteOperationCode stored(SatelliteTerminalBlockEntity terminal) {
        return SatelliteOperationCode.values()[terminal.saveWithoutMetadata().getCompound(KEY).getInt("last_result")];
    }

    private static SatelliteIdentity surveyIdentity(UUID ownerId) {
        return new SatelliteIdentity(UUID.randomUUID(), ownerId, ModIdentity.id("survey_satellite"), SatelliteKind.SURVEY,
                List.of(id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SURVEY_SCANNER_MODULE.get()),
                        id(ModItems.SATELLITE_SOLAR_MODULE.get()), id(ModItems.DATA_STORAGE_UNIT.get()),
                        id(ModItems.SATELLITE_BATTERY.get())));
    }

    private static ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    private static ItemStack bound(Item item, SatelliteIdentity identity) {
        ItemStack stack = new ItemStack(item);
        SatelliteItemData.write(stack, identity);
        return stack;
    }

    private static SatelliteTerminalBlockEntity place(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 2, 1);
        helper.setBlock(position, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH).setValue(SatelliteTerminalBlock.LIT, false));
        return (SatelliteTerminalBlockEntity) helper.getBlockEntity(position);
    }

    private static FakePlayer player(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RegistryTick"));
        BlockPos position = terminal.getBlockPos();
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        return player;
    }

    private static void power(SatelliteTerminalBlockEntity terminal) {
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
    }

    private static void insert(GameTestHelper helper, IItemHandler slots, int slot, ItemStack stack) {
        helper.assertTrue(slots.insertItem(slot, stack, false).isEmpty(), "Could not insert into terminal slot " + slot);
    }
}
