package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentPersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeSupportDeviceGameTests {
    private static final String TEMPLATE = "atmosphere_test";
    private static final BlockPos VENT = new BlockPos(4, 1, 4);
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void oneScrubberSkipsExactlyHalfAndSpendsTwoUses(GameTestHelper helper) { savings(helper, 1); }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void twoScrubbersRetainFourthDebitAndSpendThreeUses(GameTestHelper helper) { savings(helper, 2); }
    private static void savings(GameTestHelper helper, int devices) {
        OxygenVentBlockEntity vent = room(helper); var service = service(helper);
        try {
            var first = scrubber(helper, VENT.east());
            var second = devices == 2 ? scrubber(helper, VENT.west()) : null;
            service.observeVent(vent); service.tick();
            insert(helper, first, new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.CARBON_SCRUBBER_CARTRIDGE.get()));
            if (second != null) { insert(helper, second, new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.CARBON_SCRUBBER_CARTRIDGE.get())); }
            int oxygen = vent.oxygenUnits(); int energy = vent.energyStored();
            for (int tick = 0; tick < 80; tick++) { service.observeVent(vent); service.tick(); }
            helper.assertTrue(oxygen - vent.oxygenUnits() == (devices == 1 ? 2 : 1), "Scrubber savings were not exact");
            helper.assertTrue(energy - vent.energyStored() == 80 * AtmosphereLimits.VENT_ENERGY_PER_TICK, "Scrubber bypassed FE payment");
            int remaining = CarbonCartridge.uses(handler(first).getStackInSlot(0));
            if (second != null) { remaining += CarbonCartridge.uses(handler(second).getStackInSlot(0)); }
            helper.assertTrue(devices * CarbonCartridge.MAX_USES - remaining == (devices == 1 ? 2 : 3), "Cartridge debit was not one use per skip");
            helper.succeed();
        } finally { service.clear(); }
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void missingAndExhaustedCartridgesUseBaselineOxygen(GameTestHelper helper) {
        OxygenVentBlockEntity vent = room(helper); var service = service(helper);
        try {
            var facade = scrubber(helper, VENT.east()); service.observeVent(vent); service.tick();
            int oxygen = vent.oxygenUnits();
            for (int i = 0; i < 80; i++) { service.observeVent(vent); service.tick(); }
            helper.assertTrue(oxygen - vent.oxygenUnits() == 4, "Empty scrubber created oxygen savings");
            ItemStack exhausted = cartridge(0); insert(helper, facade, exhausted); oxygen = vent.oxygenUnits();
            for (int i = 0; i < 80; i++) { service.observeVent(vent); service.tick(); }
            helper.assertTrue(oxygen - vent.oxygenUnits() == 4, "Exhausted scrubber created oxygen savings");
            helper.assertTrue(CarbonCartridge.uses(handler(facade).getStackInSlot(0)) == 0, "Exhausted cartridge changed");
            helper.succeed();
        } finally { service.clear(); }
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void cartridgeExemptionStillPaysTheFourthOxygenDebitAndEveryEnergyTick(GameTestHelper helper) {
        OxygenVentBlockEntity vent = room(helper); var service = service(helper);
        try {
            scrubber(helper, VENT.east()); scrubber(helper, VENT.west());
            service.observeVent(vent); service.tick(); int oxygen = vent.oxygenUnits(); int energy = vent.energyStored();
            io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides.set(
                    io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig.SCRUBBER_REQUIRES_CARTRIDGE, false);
            try {
                for (int i = 0; i < 80; i++) { service.observeVent(vent); service.tick(); }
            } finally { io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides.clear(
                    io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig.SCRUBBER_REQUIRES_CARTRIDGE); }
            helper.assertTrue(oxygen - vent.oxygenUnits() == 1, "Cartridge exemption waived mandatory oxygen");
            helper.assertTrue(energy - vent.energyStored() == 80 * AtmosphereLimits.VENT_ENERGY_PER_TICK, "Cartridge exemption waived FE");
            helper.succeed();
        } finally { service.clear(); }
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void ventSaveLoadKeepsSingleCartridgeOwnerAndBothPhases(GameTestHelper helper) {
        OxygenVentBlockEntity vent = room(helper); var service = service(helper);
        try {
            var facade = scrubber(helper, VENT.east()); service.observeVent(vent); service.tick();
            ItemStack cartridge = cartridge(10); cartridge.getOrCreateTag().putString("marker", "retain-unrelated"); insert(helper, facade, cartridge);
            for (int i = 0; i < 45; i++) { service.observeVent(vent); service.tick(); }
            CompoundTag saved = vent.saveWithoutMetadata(); CompoundTag facadeSaved = facade.saveWithoutMetadata();
            helper.assertTrue(!facadeSaved.toString().contains("cartridge"), "Facade persisted a duplicate cartridge");
            int oxygen = vent.oxygenUnits(); int energy = vent.energyStored(); var bank = vent.scrubbers().encode();
            vent.load(saved); facade.load(facadeSaved);
            helper.assertTrue(vent.oxygenUnits() == oxygen && vent.energyStored() == energy && vent.scrubbers().encode().equals(bank), "Vent load lost gas, uses or phase");
            helper.assertTrue(vent.saveWithoutMetadata().getCompound(OxygenVentPersistence.DATA_KEY).equals(saved.getCompound(OxygenVentPersistence.DATA_KEY)), "Vent root failed exact round-trip");
            helper.assertTrue(handler(facade).getStackInSlot(0).getTag().getString("marker").equals("retain-unrelated"), "Cartridge item data was lost");
            helper.succeed();
        } finally { service.clear(); }
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void scrubberSimulationAndExpiredCapabilityCannotEditTheBank(GameTestHelper helper) {
        OxygenVentBlockEntity vent = room(helper); var service = service(helper);
        try {
            var facade = scrubber(helper, VENT.east()); service.observeVent(vent); service.tick(); var handler = handler(facade);
            CompoundTag before = vent.scrubbers().encode(); ItemStack cartridge = cartridge(3);
            helper.assertTrue(handler.insertItem(0, cartridge, true).isEmpty() && before.equals(vent.scrubbers().encode()), "Simulation mutated vent bank");
            facade.invalidateCaps(); facade.reviveCaps();
            helper.assertTrue(!handler.insertItem(0, cartridge, false).isEmpty() && before.equals(vent.scrubbers().encode()), "Expired scrubber capability remained writable");
            helper.assertTrue(handler(facade).insertItem(0, cartridge, false).isEmpty(), "Fresh scrubber capability did not work");
            helper.succeed();
        } finally { service.clear(); }
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void pipeSealSealsAndRemovalUnsealsGap(GameTestHelper helper) {
        OxygenVentBlockEntity vent = room(helper); var service = service(helper); BlockPos gap = new BlockPos(7, 2, 4);
        helper.setBlock(gap, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.PIPE_SEAL.get());
        try {
            service.observeVent(vent); service.tick(); helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "Pipe seal failed to seal room");
            helper.setBlock(gap, Blocks.AIR); service.markDirty(helper.absolutePos(gap)); service.observeVent(vent); service.tick();
            helper.assertTrue(vent.status() != VentOperatingStatus.ACTIVE && !service.controlledAt(helper.absolutePos(VENT.above())), "Removed seal retained room supply");
            helper.setBlock(gap, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.PIPE_SEAL.get());
            service.markDirty(helper.absolutePos(gap)); service.observeVent(vent); service.tick();
            helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "Replaced seal did not restore room"); helper.succeed();
        } finally { service.clear(); }
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void pipeSealForwardsOneLoadedTargetAndExpiresRetainedView(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 1, 4); helper.setBlock(pos, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.PIPE_SEAL.get());
        helper.setBlock(pos.west(), Blocks.CHEST);
        PipeSealBlockEntity seal = (PipeSealBlockEntity) helper.getBlockEntity(pos);
        IItemHandler forwarded = seal.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(IllegalStateException::new);
        IItemHandler target = helper.getBlockEntity(pos.west()).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(IllegalStateException::new);
        ItemStack input = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(forwarded.insertItem(0, input, true).isEmpty() && target.getStackInSlot(0).isEmpty(), "Forwarding simulation mutated target");
        helper.assertTrue(forwarded.insertItem(0, input, false).isEmpty() && target.getStackInSlot(0).getCount() == 1, "Item did not move to opposite neighbour");
        seal.invalidateCaps(); seal.reviveCaps(); helper.assertTrue(forwarded.extractItem(0, 1, false).isEmpty(), "Expired seal wrapper stayed writable");
        helper.setBlock(pos.west(), io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.PIPE_SEAL.get());
        var fresh = seal.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(IllegalStateException::new);
        helper.assertTrue(fresh.getSlots() == 0, "Seal-to-seal forwarding exposed target slots");
        // InvWrapper stored the accepted input by reference; chest destruction consumed that owned
        // stack into drops. The rejection probe must be a fresh, still-owned item.
        ItemStack refused = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(fresh.insertItem(0, refused, false) == refused && refused.getCount() == 1,
                "Seal-to-seal forwarding did not return the unchanged offered item"); helper.succeed();
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void unsupportedDeviceRootsRemainExactAndUnusable(GameTestHelper helper) {
        OxygenVentBlockEntity vent = room(helper); CompoundTag raw = new CompoundTag(); raw.putByteArray("large", new byte[65_537]);
        CompoundTag saved = new CompoundTag(); saved.put(OxygenVentPersistence.DATA_KEY, raw); vent.load(saved);
        helper.assertTrue(vent.protectedDeviceData() && vent.saveWithoutMetadata().get(OxygenVentPersistence.DATA_KEY).equals(raw), "Vent erased an oversized retained root");
        var position = helper.absolutePos(VENT); var state = vent.getBlockState();
        helper.assertTrue(!state.getBlock().canEntityDestroy(state, helper.getLevel(), position, null), "Entity destruction admitted quarantined vent");
        helper.assertTrue(!state.getBlock().onDestroyedByPlayer(state, helper.getLevel(), position, helper.makeMockSurvivalPlayer(), true,
                helper.getLevel().getFluidState(position)), "Player destruction admitted quarantined vent");
        state.getBlock().onBlockExploded(state, helper.getLevel(), position, null);
        helper.assertTrue(helper.getBlockEntity(VENT) == vent, "Explosion removed quarantined vent");
        var facade = scrubber(helper, VENT.east()); saved = new CompoundTag(); saved.put(ScrubberBlockEntity.KEY, StringTag.valueOf("not-a-compound")); facade.load(saved);
        helper.assertTrue(facade.protectedData() && facade.saveWithoutMetadata().get(ScrubberBlockEntity.KEY).equals(saved.get(ScrubberBlockEntity.KEY)), "Facade erased wrong-type raw root");
        position = helper.absolutePos(VENT.east()); state = facade.getBlockState();
        helper.assertTrue(!state.getBlock().canEntityDestroy(state, helper.getLevel(), position, null), "Entity destruction admitted quarantined scrubber");
        helper.assertTrue(handler(facade).insertItem(0, cartridge(3), false).getCount() == 1, "Quarantined facade remained writable");
        // Restore the test-owned fixtures before native server autosave; this case tests raw retention,
        // not the deliberate chunk-save ERROR fixtures covered by the existing guarded-save suite.
        saved = new CompoundTag(); saved.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(0, 0, 0, 0, 0));
        vent.load(saved); facade.load(new CompoundTag()); helper.succeed();
    }
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void detectorMeasuresOnlyNonSolidNeighboursAndFailsClosedWhenDisabled(GameTestHelper helper) {
        var block = io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.ATMOSPHERE_DETECTOR.get();
        BlockPos pos = new BlockPos(4, 2, 4); helper.setBlock(pos, block);
        for (Direction side : Direction.values()) { helper.setBlock(pos.relative(side), Blocks.STONE); }
        var detector = (AtmosphereDetectorBlockEntity) helper.getBlockEntity(pos); detector.measure();
        helper.assertTrue(detector.reason() == AtmosphereDetectorBlockEntity.Reason.UNAVAILABLE && !detector.signal(), "Solid neighbours granted a measurement");
        helper.setBlock(pos.east(), Blocks.AIR); detector.measure();
        helper.assertTrue(detector.reason() == AtmosphereDetectorBlockEntity.Reason.AVAILABLE && detector.signal(), "Known Overworld air did not measure breathable");
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("mode", "VACUUM");
        CompoundTag outer = new CompoundTag(); outer.put(AtmosphereDetectorBlockEntity.KEY, root); detector.load(outer); detector.measure();
        helper.assertTrue(!detector.signal(), "Breathable exterior matched VACUUM");
        io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides.set(
                io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig.OXYGEN_DETECTION, false);
        try {
            detector.measure(); helper.assertTrue(!detector.signal() && detector.reason() == AtmosphereDetectorBlockEntity.Reason.DISABLED, "Disabled detector retained output");
        } finally { io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides.clear(
                io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig.OXYGEN_DETECTION); }
        helper.succeed();
    }
    private static ItemStack cartridge(int uses) {
        ItemStack stack = new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.CARBON_SCRUBBER_CARTRIDGE.get());
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("uses", uses); stack.getOrCreateTag().put(CarbonCartridge.KEY, root); return stack;
    }
    private static void insert(GameTestHelper helper, ScrubberBlockEntity facade, ItemStack stack) {
        helper.assertTrue(handler(facade).insertItem(0, stack, false).isEmpty(), "Scrubber cartridge insertion failed");
    }
    private static IItemHandler handler(ScrubberBlockEntity facade) { return facade.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new); }
    private static ScrubberBlockEntity scrubber(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.CO2_SCRUBBER.get()); return (ScrubberBlockEntity) helper.getBlockEntity(pos);
    }
    private static AtmosphereLevelService service(GameTestHelper helper) {
        return new AtmosphereLevelService(helper.getLevel(), false, true, AtmosphereLimits.MAX_VOLUME_CELLS, AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK);
    }
    private static OxygenVentBlockEntity room(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) { for (int y = 1; y <= 3; y++) { for (int z = 1; z <= 7; z++) {
            helper.setBlock(new BlockPos(x, y, z), x == 1 || x == 7 || y == 1 || y == 3 || z == 1 || z == 7 ? Blocks.IRON_BLOCK : Blocks.AIR);
        } } }
        helper.setBlock(VENT, io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.OXYGEN_VENT.get());
        OxygenVentBlockEntity vent = (OxygenVentBlockEntity) helper.getBlockEntity(VENT); CompoundTag saved = new CompoundTag();
        saved.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(0, 0, 20, 40_000, 0)); vent.load(saved); return vent;
    }
    private LifeSupportDeviceGameTests() { }
}
