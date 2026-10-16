package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.CarbonCartridge;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.ScrubberBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * W68 (W62 S5): the CO2 scrubber's one-cartridge facade under vanilla automation. Twenty three-use cartridges enter
 * from two droppers in the room floor and a capability call on a third face; a hopper under the scrubber takes each
 * one out again into a chest. A test-owned atmosphere service ticks the vent, as LifeSupportDeviceGameTests does. Every vent tick
 * may cost one oxygen unit or one cartridge use, never both and never anything else; cartridges and uses are counted.
 */
final class ScrubberAutomationRun {
    static final int CARTRIDGES = 20, USES = 3;
    private static final int SERVICE_TICKS = 40;
    private final AutomationRig rig;
    private final AtmosphereLevelService service;
    private final OxygenVentBlockEntity vent;
    private final ScrubberBlockEntity scrubber;
    private final DispenserBlockEntity east, south;
    private final HopperBlockEntity out;
    private final Container chest;
    private final List<ItemStack> pipe = new ArrayList<>();
    private long oxygenDebits, usesSpent;
    private int subTicks, source;
    private boolean reloaded;

    ScrubberAutomationRun(GameTestHelper helper) {
        rig = new AutomationRig(helper);
        service = new AtmosphereLevelService(rig.level, false, true, AtmosphereLimits.MAX_VOLUME_CELLS,
                AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK);
        rig.beforeClose(service::clear);
        try {
            BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
            rig.room(base.offset(-1, -2, -1), base.offset(7, 3, 7));
            for (int x = 0; x <= 6; x++) { for (int y = 0; y <= 2; y++) { for (int z = 0; z <= 6; z++) {
                boolean wall = x == 0 || x == 6 || y == 0 || y == 2 || z == 0 || z == 6;
                rig.place(base.offset(x, y, z), wall ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            } } }
            BlockPos ventAt = base.offset(3, 0, 3), at = ventAt.east();
            rig.place(ventAt, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
            vent = (OxygenVentBlockEntity) rig.level.getBlockEntity(ventAt);
            CompoundTag saved = new CompoundTag(); saved.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(0, 0, 1_000, 40_000, 0));
            vent.load(saved);
            rig.place(at, ModBlocks.CO2_SCRUBBER.get().defaultBlockState());
            scrubber = (ScrubberBlockEntity) rig.level.getBlockEntity(at);
            east = rig.dropper(at.east(), Direction.WEST, cartridges(9));
            south = rig.dropper(at.south(), Direction.NORTH, cartridges(9));
            pipe.addAll(cartridges(CARTRIDGES - 18));
            out = rig.hopper(at.below(), Direction.DOWN, List.of());
            // Cartridges do not stack, so the hopper passes them on into a chest.
            rig.place(at.below(2), Blocks.CHEST.defaultBlockState());
            chest = (Container) rig.level.getBlockEntity(at.below(2));
            service.observeVent(vent); service.tick();
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }
    private static List<ItemStack> cartridges(int count) {
        var stacks = new ArrayList<ItemStack>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(ModItems.CARBON_SCRUBBER_CARTRIDGE.get());
            CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("uses", USES);
            stack.getOrCreateTag().put(CarbonCartridge.KEY, root); stacks.add(stack);
        }
        return stacks;
    }
    private IItemHandler facade(Direction side) {
        return scrubber.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(() -> new IllegalStateException("Scrubber facade"));
    }
    private int usesInSlot() { int uses = CarbonCartridge.uses(facade(null).getStackInSlot(0)); return Math.max(uses, 0); }

    void start(int timeoutTicks) {
        rig.drive(timeoutTicks, this::gameTick, this::verify, () -> "returned " + returned() + ", slot " + facade(null).getStackInSlot(0)
                + ", bound " + scrubber.boundTo(vent, vent.scrubbers().generation()) + ", vent " + vent.status() + " oxygen " + vent.oxygenUnits()
                + ", droppers " + AutomationRig.count(east, ModItems.CARBON_SCRUBBER_CARTRIDGE.get()) + "/"
                + AutomationRig.count(south, ModItems.CARBON_SCRUBBER_CARTRIDGE.get()) + ", pipe " + pipe.size()
                + ", spent " + usesSpent + ", debits " + oxygenDebits + ", reloaded " + reloaded);
    }
    private int returned() {
        return AutomationRig.count(out, ModItems.CARBON_SCRUBBER_CARTRIDGE.get()) + AutomationRig.count(chest, ModItems.CARBON_SCRUBBER_CARTRIDGE.get());
    }

    /**
     * Forty vent ticks per game tick, each with one hopper step and, every tenth, one insertion attempt. Between game
     * ticks the overworld's own atmosphere service may also visit this vent; there the base air is breathable, so it
     * only marks the vent STANDBY and restarts its oxygen phase, debiting nothing.
     */
    private boolean gameTick() {
        var helper = rig.helper;
        for (int i = 0; i < SERVICE_TICKS; i++) {
            subTicks++; rig.tickHoppers();
            if (subTicks % 10 == 0 && facade(null).getStackInSlot(0).isEmpty()) {
                switch (source++ % 3) {
                    case 0 -> { if (!AutomationRig.empty(east)) { rig.pulse(east); } }
                    case 1 -> { if (!AutomationRig.empty(south)) { rig.pulse(south); } }
                    default -> { if (!pipe.isEmpty() && facade(Direction.UP).insertItem(0, pipe.get(0), false).isEmpty()) { pipe.remove(0); } }
                }
            }
            int oxygen = vent.oxygenUnits(), uses = usesInSlot();
            service.observeVent(vent); service.tick();
            int debited = oxygen - vent.oxygenUnits(), spent = uses - usesInSlot();
            helper.assertTrue(debited >= 0 && spent >= 0 && debited + spent <= 1,
                    "One vent tick took " + debited + " oxygen and " + spent + " cartridge uses");
            oxygenDebits += debited; usesSpent += spent;
            if (!reloaded && returned() >= CARTRIDGES / 2 && !facade(null).getStackInSlot(0).isEmpty()) {
                reloaded = true;
                for (Direction side : Direction.values()) {
                    ItemStack offered = cartridges(1).get(0);
                    helper.assertTrue(facade(side).insertItem(0, offered, false) == offered, "An occupied scrubber took a second cartridge from side " + side);
                }
                var bank = vent.scrubbers().encode(); int stock = vent.oxygenUnits();
                CompoundTag ventSaved = vent.saveWithoutMetadata(), scrubberSaved = scrubber.saveWithoutMetadata();
                vent.load(ventSaved); scrubber.load(scrubberSaved);
                helper.assertTrue(vent.scrubbers().encode().equals(bank) && vent.oxygenUnits() == stock,
                        "Vent and scrubber save and load changed the cartridge bank or oxygen");
            }
        }
        return returned() == CARTRIDGES && reloaded && AutomationRig.empty(east) && AutomationRig.empty(south) && pipe.isEmpty();
    }

    private void verify() {
        var helper = rig.helper; int left = 0, count = 0;
        for (Container container : List.of(out, chest)) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(ModItems.CARBON_SCRUBBER_CARTRIDGE.get())) { count += stack.getCount(); left += CarbonCartridge.uses(stack); }
            }
        }
        long supplied = (long) CARTRIDGES * USES;
        helper.assertTrue(count == CARTRIDGES && facade(null).getStackInSlot(0).isEmpty(), "Scrubber returned " + count + " of " + CARTRIDGES + " cartridges");
        helper.assertTrue(supplied - left == usesSpent, "Scrubber uses: " + supplied + " supplied, " + left + " returned, " + usesSpent + " spent");
        helper.assertTrue(usesSpent > 0 && oxygenDebits > 0, "The vent never debited: " + vent.status());
        String spilled = rig.spilled(scrubber.getBlockPos(), 6);
        helper.assertTrue(spilled.isEmpty(), "Scrubber automation spilled items into the world: " + spilled);
    }
}
