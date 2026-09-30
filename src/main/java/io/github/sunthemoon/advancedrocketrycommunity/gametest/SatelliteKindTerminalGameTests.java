package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteTerminalViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalViews;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** ADR-049 sections 6–7 at the terminal: idle launch of a non-data kind, replay, gates, decommission, blank-chip. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteKindTerminalGameTests {
    private static final String KEY = "SatelliteTerminal";
    private static final ResourceLocation SURVEY = ModIdentity.id("survey_satellite");

    private SatelliteKindTerminalGameTests() {
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void terminalLaunchesAnIdleSurveySatelliteAndConsumesAReplay(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = place(helper);
        FakePlayer owner = player(helper, terminal);
        IItemHandler slots = terminal.menuInventory();
        SatelliteIdentity identity = surveyIdentity(owner.getUUID());
        helper.assertTrue(!slots.insertItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE,
                        bound(ModItems.SATELLITE_PACKAGE.get(), new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(),
                                SatelliteIds.DATA_SATELLITE)), false).isEmpty(),
                "The generic package slot accepted a data identity");
        helper.assertTrue(!slots.insertItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE,
                        bound(ModItems.DATA_SATELLITE_PACKAGE.get(), identity), false).isEmpty(),
                "The data package slot accepted a survey identity");
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), identity));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), identity));
        power(terminal);

        // Launch targets start with Earth, which needs no discovery.
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        helper.assertTrue(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                "A successful launch kept the package");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(helper.getLevel().getServer());
        SatelliteState state = data.satellite(identity.satelliteId()).orElseThrow();
        helper.assertTrue(state.kind() == SatelliteKind.SURVEY && state.currentMissionId().isEmpty()
                        && state.orbitBody().equals(Optional.of(CelestialIds.EARTH_ID))
                        && state.blueprint().components().equals(identity.components())
                        && state.blueprint().stats().battery() == 10_720
                        && state.kindState() instanceof SatelliteKindState.Survey survey
                        && survey.charge() == 0L && survey.scanEnergy() == 1_000,
                "The registered survey satellite does not match its blueprint and definition");
        helper.assertTrue(!data.isDirty(), "The idle launch was not flushed before the package was extracted");
        SatelliteTerminalViewPacket view = SatelliteTerminalViews.compose(terminal, 3, helper.getLevel().getServer());
        helper.assertTrue(view.containerId() == 3 && view.kind().equals(Optional.of(SatelliteKind.SURVEY))
                        && view.definition().id().equals(Optional.of(SURVEY)) && view.definition().size() == 4
                        && view.target().equals(new SatelliteTerminalViewPacket.Selection(
                                Optional.of(CelestialIds.EARTH_ID), 0, 6))
                        && view.orbitBody().equals(Optional.of(CelestialIds.EARTH_ID))
                        && view.missions().isEmpty() && view.buffer().isEmpty() && view.instanceView().isEmpty(),
                "The terminal view does not show the server-selected survey satellite");

        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), identity));
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.IDEMPOTENT);
        helper.assertTrue(slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                "A replayed package was not consumed");
        helper.assertTrue(data.satellites().stream().filter(value -> value.satelliteId().equals(identity.satelliteId()))
                .count() == 1, "A replay registered a second satellite");
        // Without a package, a survey chip has no data mission to start.
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.DEFINITION_NOT_FOUND);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void terminalLaunchRechecksTargetsComponentsAndResearch(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = place(helper);
        FakePlayer owner = player(helper, terminal);
        IItemHandler slots = terminal.menuInventory();
        power(terminal);

        SatelliteIdentity unknownComponent = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), SURVEY,
                SatelliteKind.SURVEY, List.of(id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SURVEY_SCANNER_MODULE.get()),
                ResourceLocation.tryParse("minecraft:stone")));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                bound(ModItems.SATELLITE_CONTROL_CHIP.get(), unknownComponent));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), unknownComponent));
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.COMPONENT_UNAVAILABLE);
        clear(slots);

        SatelliteIdentity solar = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), ModIdentity.id("solar_satellite"),
                SatelliteKind.SOLAR, List.of(id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SOLAR_TRANSMITTER_MODULE.get()),
                id(ModItems.SATELLITE_SOLAR_MODULE.get())));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), solar));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), solar));
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.RESEARCH_LOCKED);
        clear(slots);

        // The selection persists per terminal; the refusals above all used the first target, Earth.
        SatelliteIdentity survey = surveyIdentity(owner.getUUID());
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), survey));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), survey));
        // Earth, Moon, then Mars: Mars requires discovery.
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_NEXT, SatelliteOperationCode.SUCCESS);
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_NEXT, SatelliteOperationCode.SUCCESS);
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.TARGET_NOT_ALLOWED);
        helper.assertTrue(!slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                "A refused launch consumed the package");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(helper.getLevel().getServer());
        helper.assertTrue(data.satellite(survey.satelliteId()).isEmpty() && data.satellite(unknownComponent.satelliteId()).isEmpty()
                && data.satellite(solar.satelliteId()).isEmpty(), "A refused launch registered a satellite");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void decommissionBlanksTheChipOnlyForAnIdleSatellite(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = place(helper);
        FakePlayer owner = player(helper, terminal);
        IItemHandler slots = terminal.menuInventory();
        power(terminal);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(helper.getLevel().getServer());

        // A data satellite with its first mission running is busy.
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CHASSIS, new ItemStack(ModItems.SATELLITE_CHASSIS.get()));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_SOLAR_MODULE, new ItemStack(ModItems.SATELLITE_SOLAR_MODULE.get()));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE, new ItemStack(ModItems.DATA_STORAGE_UNIT.get()));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_ASSEMBLE, SatelliteOperationCode.SUCCESS);
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        ItemStack dataChip = slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP).copy();
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.MISSION_BUSY);
        helper.assertTrue(ItemStack.matches(dataChip, slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP)),
                "A refused decommission changed the chip");
        clear(slots);
        power(terminal);

        SatelliteIdentity survey = surveyIdentity(owner.getUUID());
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), survey));
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), survey));
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.SUCCESS);
        FakePlayer intruder = player(helper, terminal);
        terminal.setOwner(owner.getUUID());
        press(helper, terminal, intruder, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.UNAUTHORIZED);
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.SUCCESS);
        helper.assertTrue(data.satellite(survey.satelliteId()).isEmpty(), "Decommission kept the satellite");
        helper.assertTrue(!data.isDirty(), "Decommission was not flushed before the chip was blanked");
        ItemStack chip = slots.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP);
        helper.assertTrue(chip.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                        && SatelliteItemData.read(chip).status() == SatelliteItemData.DecodeStatus.EMPTY,
                "Decommission did not blank the chip");

        // An inert chip of a removed satellite answers SATELLITE_NOT_FOUND and can be blanked by an operator.
        clear(slots);
        ItemStack inert = bound(ModItems.SATELLITE_CONTROL_CHIP.get(), survey);
        insert(helper, slots, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, inert.copy());
        press(helper, terminal, owner, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.SATELLITE_NOT_FOUND);
        helper.assertTrue(blankChip(helper, owner, inert.copy()) == 1, "The operator could not blank an inert chip");
        helper.assertTrue(SatelliteItemData.read(owner.getMainHandItem()).status() == SatelliteItemData.DecodeStatus.EMPTY,
                "The blank-chip command left the chip bound");
        helper.assertTrue(blankChip(helper, owner, dataChip.copy()) == 0,
                "The blank-chip command stranded a registered satellite");
        helper.assertTrue(ItemStack.matches(owner.getMainHandItem(), dataChip), "A refused blank-chip changed the chip");
        helper.succeed();
    }

    private static int blankChip(GameTestHelper helper, FakePlayer player, ItemStack chip) {
        player.setItemInHand(InteractionHand.MAIN_HAND, chip);
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        return helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(source, "arce satellite admin blank-chip @s");
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

    private static SatelliteTerminalBlockEntity place(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 2, 1);
        helper.setBlock(position, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH)
                .setValue(SatelliteTerminalBlock.LIT, false));
        return (SatelliteTerminalBlockEntity) helper.getBlockEntity(position);
    }

    private static FakePlayer player(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SatelliteKinds"));
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

    private static void clear(IItemHandler slots) {
        for (int slot = 0; slot < SatelliteTerminalBlockEntity.SLOT_COUNT; slot++) {
            slots.extractItem(slot, 64, false);
        }
    }

    private static void press(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer player,
                              int button, SatelliteOperationCode expected) {
        terminal.handleButton(player, button);
        int actual = terminal.saveWithoutMetadata().getCompound(KEY).getInt("last_result");
        helper.assertTrue(actual == expected.ordinal(), "Button " + button + ": expected " + expected + " but got "
                + SatelliteOperationCode.values()[actual]);
    }

    private static ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
