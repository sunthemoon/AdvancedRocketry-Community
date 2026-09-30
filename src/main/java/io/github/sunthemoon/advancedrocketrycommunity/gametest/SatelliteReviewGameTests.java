package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SurveyScanResultPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.ScanSettings;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.SurveyScanService;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SolarLinks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalViews;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** The tests the C7 independent review required (C7-H1, C7-M1..M4 and the Low fixes). */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteReviewGameTests {
    private static final ResourceLocation SURVEY = ModIdentity.id("survey_satellite");

    private SatelliteReviewGameTests() {
    }

    /** C7-H1: a replay after a failed barrier flush keeps the package until the registry is durable. */
    @GameTest(template = "empty", batch = SatelliteRegistryFixture.FLUSH_FAILURE_BATCH, timeoutTicks = 30)
    public static void aFailedLaunchFlushKeepsThePackageUntilTheReplayIsDurable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteTerminalBlockEntity terminal = terminal(helper);
        FakePlayer owner = player(helper, "FlushOwner");
        IItemHandler slots = terminal.menuInventory();
        SatelliteIdentity identity = surveyIdentity(owner.getUUID());
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), identity));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), identity));
        power(terminal);
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path obstacle = data.resolve(SatelliteMissionSavedData.DATA_NAME + ".dat.arce-pending");
        try {
            Files.createDirectory(obstacle);
            try {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_EXPECTED_SAVE_FAILURE testing the idle-launch barrier");
                press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.UNSUPPORTED_DATA);
                helper.assertTrue(!slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                        "A launch whose flush failed consumed the package");
                press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.UNSUPPORTED_DATA);
                helper.assertTrue(!slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                        "A replay consumed the package before the registry was durable");
            } finally {
                Files.delete(obstacle);
            }
            press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.IDEMPOTENT);
            helper.assertTrue(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                    "The durable replay did not consume the package");
            CompoundTag disk = NbtIo.readCompressed(data.resolve(SatelliteMissionSavedData.DATA_NAME + ".dat").toFile())
                    .getCompound("data");
            helper.assertTrue(SatelliteMissionSavedData.load(disk).satellite(identity.satelliteId()).isPresent(),
                    "The satellite is not on disk after the package was consumed");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Flush-failure fixture failed", exception);
        }
        helper.succeed();
    }

    /** C7-M1 and C7-L1: a blocked registry fails closed everywhere and never crashes the server. */
    @GameTest(template = "empty", batch = SatelliteRegistryFixture.BLOCKED_BATCH, timeoutTicks = 60)
    public static void aBlockedRegistryFailsClosedWithoutCrashing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(!SatelliteMissionSavedData.get(server).operational(), "The fixture registry is not blocked");
        FakePlayer owner = player(helper, "BlockedOwner");
        SatelliteIdentity solar = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), ModIdentity.id("solar_satellite"),
                SatelliteKind.SOLAR, List.of(id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SOLAR_TRANSMITTER_MODULE.get()),
                id(ModItems.SATELLITE_SOLAR_MODULE.get())));
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.MICROWAVE_RECEIVER.get());
        MicrowaveReceiverBlockEntity receiver = (MicrowaveReceiverBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
        receiver.menuInventory().insertItem(0, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), solar), false);

        helper.assertTrue(SatelliteRuntime.requestScan(owner, surveyIdentity(owner.getUUID()))
                == SatelliteOperationCode.SATELLITE_NOT_FOUND, "A scan was accepted on a blocked registry");
        owner.setItemInHand(InteractionHand.MAIN_HAND, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), solar));
        var source = owner.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        helper.assertTrue(server.getCommands().performPrefixedCommand(source, "arce satellite admin blank-chip @s") == 0
                        && SatelliteItemData.read(owner.getMainHandItem()).identity().isPresent(),
                "blank-chip blanked a chip while the registry was blocked");
        SatelliteTerminalBlockEntity terminal = terminal(helper, new BlockPos(3, 2, 1));
        IItemHandler slots = terminal.menuInventory();
        SatelliteIdentity survey = surveyIdentity(owner.getUUID());
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), survey));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), survey));
        power(terminal);
        terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_LAUNCH);
        helper.assertTrue(!slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                "A launch on a blocked registry consumed the package");

        helper.runAfterDelay(22, () -> {
            helper.assertTrue(receiver.status(0) == SolarLinks.LinkStatus.UNAVAILABLE && receiver.output() == 0,
                    "A receiver produced power or linked on a blocked registry");
            BlockPos position = receiver.getBlockPos();
            helper.getLevel().destroyBlock(position, false);
            helper.assertTrue(helper.getLevel().getBlockEntity(position) == null
                    && !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(1.5)).isEmpty(),
                    "Breaking a receiver on a blocked registry orphaned it or lost its chip");
            helper.succeed();
        });
    }

    /** C7-M4: far-away cells are UNKNOWN, and the scan loads no chunk. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void aScanOfUnloadedChunksIsUnknownAndLoadsNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer owner = player(helper, "FarScanner");
        owner.setPos(2_000_008.5D, 80.0D, 2_000_008.5D);
        int chunkX = owner.getBlockX() >> 4;
        int chunkZ = owner.getBlockZ() >> 4;
        helper.assertTrue(!helper.getLevel().hasChunk(chunkX, chunkZ), "The far chunk is already loaded");
        SatelliteIdentity identity = launchSurvey(server, owner.getUUID(), 10_720L);
        List<SurveyScanResultPacket> results = new ArrayList<>();
        SurveyScanService service = new SurveyScanService(SatelliteRuntime::celestialCatalog,
                () -> ScanSettings.DEFAULTS, (player, packet) -> results.add(packet));
        helper.assertTrue(service.request(owner, identity) == SatelliteOperationCode.SUCCESS, "The far scan was refused");
        for (int tick = 0; tick < 10 && results.isEmpty(); tick++) {
            service.tick(server);
        }
        helper.assertTrue(results.size() == 1 && results.get(0).cells().stream().allMatch(SurveyScanResultPacket.Cell::unknown),
                "Cells in unloaded chunks were not UNKNOWN");
        helper.assertTrue(!helper.getLevel().hasChunk(chunkX, chunkZ), "The scan loaded a chunk");
        helper.succeed();
    }

    /** C7-M4: the chip's use() starts a scan that the production server tick finishes. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 120)
    public static void theSurveyChipStartsAScanThatTheServerTickFinishes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer owner = player(helper, "ChipScanner");
        SatelliteIdentity identity = launchSurvey(server, owner.getUUID(), 10_720L);
        ItemStack chip = bound(ModItems.SATELLITE_CONTROL_CHIP.get(), identity);
        owner.setItemInHand(InteractionHand.MAIN_HAND, chip);
        chip.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(SatelliteRuntime.scanRunning(owner.getUUID()), "Using the survey chip started no scan");
        SatelliteKindState.Survey paid = (SatelliteKindState.Survey) SatelliteMissionSavedData.get(server)
                .satellite(identity.satelliteId()).orElseThrow().kindState();
        helper.assertTrue(paid.charge() == 9_720L, "The chip's scan was not paid exactly once");
        helper.succeedWhen(() -> helper.assertTrue(!SatelliteRuntime.scanRunning(owner.getUUID()),
                "The production tick has not finished the scan yet"));
    }

    /** C7-M2: however many broadcasts happen, the view is composed and sent at most once per 5 ticks. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void theViewIsSentAtMostOncePerFiveTicks(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = terminal(helper);
        FakePlayer owner = player(helper, "ViewWatcher");
        List<SatelliteTerminalViewPacket> sent = new ArrayList<>();
        SatelliteTerminalViews views = new SatelliteTerminalViews(owner, 7, (player, view) -> sent.add(view));
        for (int call = 0; call < 50; call++) {
            views.tick(terminal);
        }
        helper.assertTrue(sent.size() == 1, "The first tick sent " + sent.size() + " views");
        insert(helper, terminal.menuInventory(), SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                bound(ModItems.SATELLITE_CONTROL_CHIP.get(), surveyIdentity(owner.getUUID())));
        for (int call = 0; call < 50; call++) {
            views.tick(terminal);
        }
        helper.assertTrue(sent.size() == 1, "A change inside the same 5 ticks was sent early");
        helper.runAfterDelay(SatelliteTerminalViews.SEND_INTERVAL_TICKS, () -> {
            views.tick(terminal);
            views.tick(terminal);
            helper.assertTrue(sent.size() == 2 && sent.get(1).kind().equals(Optional.of(SatelliteKind.SURVEY)),
                    "The changed view was not sent once after the interval");
            helper.succeed();
        });
    }

    /** C7-M4: OUTPUT_BLOCKED and DEFINITION_NOT_FOUND, and a stale menu cannot assemble. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 60)
    public static void builderReportsABlockedOutputAMissingDefinitionAndStaleMenus(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.SATELLITE_BUILDER.get().defaultBlockState()
                .setValue(SatelliteBuilderBlock.FACING, Direction.NORTH));
        SatelliteBuilderBlockEntity builder = (SatelliteBuilderBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
        FakePlayer owner = player(helper, "BuilderReview");
        IItemHandler slots = builder.menuInventory();
        builder.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().receiveEnergy(10_000, false);
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHASSIS, new ItemStack(ModItems.SATELLITE_CHASSIS.get()));
        // The adapter test mod defines this primary component, but no definition selects it.
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_PRIMARY, new ItemStack(Items.RECOVERY_COMPASS));
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHIP, new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
        expect(helper, builder, owner, SatelliteOperationCode.DEFINITION_NOT_FOUND, "a primary without a definition");
        IItemHandler automation = builder.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
        slots.extractItem(SatelliteBuilderBlockEntity.SLOT_CHIP, 1, false);
        helper.assertTrue(automation.insertItem(SatelliteBuilderBlockEntity.SLOT_CHIP,
                new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()), false).getCount() == 1,
                "Automation inserted a chip into the builder");
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHIP, new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
        slots.extractItem(SatelliteBuilderBlockEntity.SLOT_PRIMARY, 1, false);
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_PRIMARY, new ItemStack(ModItems.SURVEY_SCANNER_MODULE.get()));
        for (Item module : List.of(ModItems.SATELLITE_SOLAR_MODULE.get(), ModItems.DATA_STORAGE_UNIT.get(),
                ModItems.SATELLITE_BATTERY.get())) {
            helper.assertTrue(slots.insertItem(firstEmptyModule(slots), new ItemStack(module), false).isEmpty(),
                    "Could not insert a module");
        }
        var stale = new SatelliteBuilderMenu(1, owner.getInventory(), builder, SatelliteRuntime.catalogGeneration() + 1);
        helper.assertTrue(!stale.clickMenuButton(owner, SatelliteBuilderMenu.BUTTON_ASSEMBLE)
                        && slots.getStackInSlot(SatelliteBuilderBlockEntity.SLOT_OUTPUT).isEmpty(),
                "A menu of an older catalog generation assembled");
        helper.assertTrue(builder.assemble(owner), "The valid survey blueprint was refused");
        helper.runAfterDelay(SatelliteBuilderBlockEntity.ASSEMBLY_COOLDOWN_TICKS + 1, () -> {
            expect(helper, builder, owner, SatelliteOperationCode.OUTPUT_BLOCKED, "an occupied output");
            helper.succeed();
        });
    }

    /** C7-L6 and C7-L1: a new chip starts at the first target; blank-chip refuses an unreadable chip of a live satellite. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void aNewChipStartsAtTheFirstTargetAndUnreadableChipsStayBound(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteTerminalBlockEntity terminal = terminal(helper);
        FakePlayer owner = player(helper, "TargetOwner");
        IItemHandler slots = terminal.menuInventory();
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                bound(ModItems.SATELLITE_CONTROL_CHIP.get(), surveyIdentity(owner.getUUID())));
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_NEXT, SatelliteOperationCode.SUCCESS);
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_NEXT, SatelliteOperationCode.SUCCESS);
        slots.extractItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 1, false);
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                bound(ModItems.SATELLITE_CONTROL_CHIP.get(), surveyIdentity(owner.getUUID())));
        var view = SatelliteTerminalViews.compose(terminal, 1, server);
        helper.assertTrue(view.target().position() == 0 && view.target().size() == 6
                        && view.target().id().equals(Optional.of(CelestialIds.EARTH_ID)),
                "A new chip inherited the previous target selection: " + view.target());

        SatelliteIdentity live = launchSurvey(server, owner.getUUID(), 0L);
        ItemStack unreadable = bound(ModItems.SATELLITE_CONTROL_CHIP.get(), live);
        unreadable.getTag().getCompound(SatelliteItemData.DATA_KEY).putInt("schema_version", 99);
        owner.setItemInHand(InteractionHand.MAIN_HAND, unreadable);
        var source = owner.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        helper.assertTrue(server.getCommands().performPrefixedCommand(source, "arce satellite admin blank-chip @s") == 0
                        && SatelliteItemData.rawSatelliteId(owner.getMainHandItem()).equals(Optional.of(live.satelliteId())),
                "blank-chip blanked an unreadable chip of a registered satellite");
        helper.succeed();
    }

    private static int firstEmptyModule(IItemHandler slots) {
        for (int slot = SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST; slot <= SatelliteBuilderBlockEntity.SLOT_MODULE_LAST; slot++) {
            if (slots.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        throw new IllegalStateException("No empty module slot");
    }

    private static void expect(GameTestHelper helper, SatelliteBuilderBlockEntity builder, FakePlayer player,
                               SatelliteOperationCode expected, String what) {
        helper.assertTrue(!builder.assemble(player), "Assembly succeeded for " + what);
        int actual = builder.saveWithoutMetadata().getCompound("SatelliteBuilder").getInt("last_result");
        helper.assertTrue(actual == expected.ordinal(), "Expected " + expected + " for " + what + " but got "
                + SatelliteOperationCode.values()[actual]);
    }

    private static SatelliteIdentity launchSurvey(MinecraftServer server, UUID ownerId, long charge) {
        SatelliteIdentity identity = surveyIdentity(ownerId);
        SatelliteMissionSavedData.get(server).launchIdle(time -> SatelliteState.launchIdle(identity.satelliteId(), SURVEY,
                ownerId, time, CelestialIds.EARTH_ID, new SatelliteBlueprint(identity.components(), false,
                        new SatelliteStats(4, 10_720, 1_000, 0, 10)),
                new SatelliteKindState.Survey(charge, time, 1_000, 16, 16)), server.overworld().getGameTime());
        return identity;
    }

    private static SatelliteIdentity surveyIdentity(UUID ownerId) {
        return new SatelliteIdentity(UUID.randomUUID(), ownerId, SURVEY, SatelliteKind.SURVEY, List.of(
                id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SURVEY_SCANNER_MODULE.get()),
                id(ModItems.SATELLITE_SOLAR_MODULE.get()), id(ModItems.DATA_STORAGE_UNIT.get()),
                id(ModItems.SATELLITE_BATTERY.get())));
    }

    private static ItemStack bound(Item item, SatelliteIdentity identity) {
        ItemStack stack = new ItemStack(item);
        SatelliteItemData.write(stack, identity);
        return stack;
    }

    private static SatelliteTerminalBlockEntity terminal(GameTestHelper helper) {
        return terminal(helper, new BlockPos(1, 2, 1));
    }

    private static SatelliteTerminalBlockEntity terminal(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH)
                .setValue(SatelliteTerminalBlock.LIT, false));
        return (SatelliteTerminalBlockEntity) helper.getBlockEntity(position);
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        return player;
    }

    private static void power(SatelliteTerminalBlockEntity terminal) {
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
    }

    private static void insert(GameTestHelper helper, IItemHandler slots, int slot, ItemStack stack) {
        helper.assertTrue(slots.insertItem(slot, stack, false).isEmpty(), "Could not insert into slot " + slot);
    }

    private static void press(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer player,
                              int button, SatelliteOperationCode expected) {
        terminal.handleButton(player, button);
        int actual = terminal.saveWithoutMetadata().getCompound("SatelliteTerminal").getInt("last_result");
        helper.assertTrue(actual == expected.ordinal(), "Button " + button + ": expected " + expected + " but got "
                + SatelliteOperationCode.values()[actual]);
    }

    private static ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
