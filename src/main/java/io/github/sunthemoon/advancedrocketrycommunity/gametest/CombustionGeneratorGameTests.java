package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionBurn;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionSave;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** C16a-02 A1: real Forge fuel, capabilities, menus, resource retention and loaded neighbours. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombustionGeneratorGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 2, 1);

    private static CombustionGeneratorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POSITION, ModBlocks.COMBUSTION_GENERATOR.get());
        return (CombustionGeneratorBlockEntity) helper.getBlockEntity(POSITION);
    }

    private static void state(CombustionGeneratorBlockEntity generator, int energy, int duration, int remaining,
            ItemStack fuel) {
        CompoundTag outer = new CompoundTag();
        outer.put(CombustionSave.ROOT, CombustionSave.encode(new CombustionBurn.State(energy, duration, remaining), fuel));
        generator.load(outer);
    }

    private static void tick(GameTestHelper helper, CombustionGeneratorBlockEntity generator) {
        CombustionGeneratorBlockEntity.serverTick(helper.getLevel(), generator.getBlockPos(),
                generator.getBlockState(), generator);
    }

    private static IEnergyStorage energy(CombustionGeneratorBlockEntity generator) {
        return generator.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    }

    private static IItemHandler items(CombustionGeneratorBlockEntity generator) {
        return generator.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void coalUsesForgeBurnTimeAndOneFuelOnly(GameTestHelper helper) {
        var generator = place(helper);
        var slot = items(generator);
        ItemStack input = new ItemStack(Items.COAL, 2);
        helper.assertTrue(slot.insertItem(0, input, true).isEmpty() && generator.fuelStack().isEmpty(), "Simulation consumed fuel");
        helper.assertTrue(slot.insertItem(0, input, false).isEmpty(), "Coal insertion failed");
        tick(helper, generator);
        helper.assertTrue(generator.burnState().equals(new CombustionBurn.State(40, 1_600, 1_599)), "Wrong coal credit");
        helper.assertTrue(generator.fuelStack().getCount() == 1 && input.getCount() == 2, "Input was consumed twice or aliased");
        helper.assertTrue(generator.getBlockState().getValue(CombustionGeneratorBlock.LIT), "Active generator did not light");
        ItemStack view = slot.getStackInSlot(0);
        view.setCount(64);
        helper.assertTrue(generator.fuelStack().getCount() == 1, "Capability exposed mutable fuel");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lavaBucketLeavesOneExtractableEmptyBucket(GameTestHelper helper) {
        var generator = place(helper);
        items(generator).insertItem(0, new ItemStack(Items.LAVA_BUCKET), false);
        tick(helper, generator);
        helper.assertTrue(generator.burnState().duration() == 20_000 && generator.burnState().remaining() == 19_999,
                "Lava burn duration differs from furnace");
        helper.assertTrue(generator.fuelStack().is(Items.BUCKET) && generator.fuelStack().getCount() == 1, "Container lost");
        helper.assertTrue(items(generator).extractItem(0, 1, true).is(Items.BUCKET)
                && generator.fuelStack().is(Items.BUCKET), "Simulation removed container");
        helper.assertTrue(items(generator).extractItem(0, 1, false).is(Items.BUCKET)
                && generator.fuelStack().isEmpty(), "Container extraction failed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullAndAlmostFullBuffersPreserveFuelAndCredit(GameTestHelper helper) {
        var generator = place(helper);
        for (int initial : new int[]{20_000, 19_999, 19_961}) {
            state(generator, initial, 1_600, 777, new ItemStack(Items.COAL, 2));
            tick(helper, generator);
            helper.assertTrue(generator.burnState().energy() == initial && generator.burnState().remaining() == 777
                    && generator.fuelStack().getCount() == 2, "Paused generator spent resources");
            helper.assertTrue(!generator.getBlockState().getValue(CombustionGeneratorBlock.LIT), "Paused block stayed lit");
        }
        state(generator, 19_960, 1_600, 777, new ItemStack(Items.COAL, 2));
        tick(helper, generator);
        helper.assertTrue(generator.burnState().energy() == 20_000 && generator.burnState().remaining() == 776
                && generator.fuelStack().getCount() == 2, "Resume spent new fuel");
        state(generator, 20_000, 0, 0, new ItemStack(Items.LAVA_BUCKET));
        tick(helper, generator);
        helper.assertTrue(generator.fuelStack().is(Items.LAVA_BUCKET) && generator.burnState().remaining() == 0,
                "Full buffer consumed a new fuel container");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void pullSimulationAndAllSidesShareOneTickBudget(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 20_000, 0, 0, ItemStack.EMPTY);
        helper.assertTrue(energy(generator).extractEnergy(9_999, true) == 1_000
                && generator.burnState().energy() == 20_000, "FE simulation mutated resources");
        helper.assertTrue(generator.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(IllegalStateException::new).extractEnergy(700, false) == 700,
                "First side could not extract");
        helper.assertTrue(generator.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH).orElseThrow(IllegalStateException::new).extractEnergy(700, false) == 300,
                "Output budget was per side instead of per block");
        helper.assertTrue(energy(generator).extractEnergy(Integer.MAX_VALUE, false) == 0
                && energy(generator).receiveEnergy(1_000, false) == 0, "Budget or output-only contract failed");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(energy(generator).extractEnergy(9_999, false) == 1_000, "Output budget did not reset on a later tick");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pushPowersAnExistingElectrolyzerAndSharesPullBudget(GameTestHelper helper) {
        var generator = place(helper);
        helper.setBlock(POSITION.east(), ModBlocks.ELECTROLYZER.get());
        var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(POSITION.east());
        state(generator, 20_000, 0, 0, ItemStack.EMPTY);
        helper.assertTrue(energy(generator).extractEnergy(700, false) == 700, "Pull failed");
        tick(helper, generator);
        helper.assertTrue(machine.energyStored() == 300 && generator.burnState().energy() == 19_000,
                "Push/pull exceeded shared 1000 FE/t or failed to power existing machine");
        helper.assertTrue(energy(generator).extractEnergy(1, false) == 0, "Push budget was not charged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void receiverCallbacksCannotReenterEnergyOrInventory(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 2_000, 0, 0, new ItemStack(Items.COAL));
        var resolvedEnergy = energy(generator);
        var resolvedItems = items(generator);
        BlockPos neighbour = generator.getBlockPos().east();
        helper.getLevel().setBlockAndUpdate(neighbour, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        int[] observed = new int[3];
        var receiver = new net.minecraft.world.level.block.entity.BlockEntity(
                net.minecraft.world.level.block.entity.BlockEntityType.CHEST, neighbour,
                net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()) {
            private final net.minecraftforge.common.util.LazyOptional<IEnergyStorage> port =
                    net.minecraftforge.common.util.LazyOptional.of(() -> new IEnergyStorage() {
                        @Override public int receiveEnergy(int amount, boolean simulate) {
                            observed[0] += resolvedEnergy.extractEnergy(1_000, false);
                            observed[1] += resolvedItems.extractItem(0, 1, false).getCount();
                            observed[2] += amount;
                            return amount;
                        }
                        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
                        @Override public int getEnergyStored() { return 0; }
                        @Override public int getMaxEnergyStored() { return 10_000; }
                        @Override public boolean canExtract() { return false; }
                        @Override public boolean canReceive() { return true; }
                    });
            @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                    net.minecraftforge.common.capabilities.Capability<T> capability, Direction side) {
                return capability == ForgeCapabilities.ENERGY ? port.cast() : super.getCapability(capability, side);
            }
        };
        helper.getLevel().setBlockEntity(receiver);
        try {
            tick(helper, generator);
            helper.assertTrue(observed[0] == 0 && observed[1] == 0 && observed[2] == 1_000,
                    "Reentrant receiver mutated generator resources");
            helper.assertTrue(generator.burnState().energy() == 1_040 && generator.fuelStack().isEmpty(),
                    "Receiver accounting differs from 40 generated minus 1000 exported");
        } finally {
            helper.getLevel().setBlockAndUpdate(neighbour, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidatedResolvedPortsCannotMutateEvenAfterRevival(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 1_000, 0, 0, new ItemStack(Items.COAL));
        var oldEnergy = energy(generator);
        var oldItems = items(generator);
        generator.invalidateCaps();
        generator.reviveCaps();
        helper.assertTrue(oldEnergy.extractEnergy(100, false) == 0 && oldItems.extractItem(0, 1, false).isEmpty(),
                "Old resolved capability revived");
        helper.assertTrue(energy(generator).extractEnergy(100, false) == 100 && items(generator).extractItem(0, 1, false).is(Items.COAL),
                "Fresh ports did not work");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void futureCorruptAndPrimitiveRootsRemainExactAndUnusable(GameTestHelper helper) {
        var generator = place(helper);
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 2);
        future.putString("future_fuel", "retain");
        CompoundTag corrupt = CombustionSave.encode(new CombustionBurn.State(1_000, 0, 0), new ItemStack(Items.COAL));
        corrupt.putInt("energy", -1);
        for (var raw : List.of(future, corrupt, IntTag.valueOf(42))) {
            CompoundTag outer = new CompoundTag();
            outer.put(CombustionSave.ROOT, raw);
            generator.load(outer);
            tick(helper, generator);
            helper.assertTrue(generator.repairRequired() && !generator.getCapability(ForgeCapabilities.ENERGY).isPresent()
                    && !generator.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Unsupported root exposes resources");
            helper.assertTrue(generator.saveWithoutMetadata().get(CombustionSave.ROOT).equals(raw), "Unsupported root was rewritten");
            Player player = helper.makeMockPlayer();
            helper.assertTrue(!ModBlocks.COMBUSTION_GENERATOR.get().onDestroyedByPlayer(generator.getBlockState(),
                    helper.getLevel(), generator.getBlockPos(), player, true, generator.getBlockState().getFluidState()),
                    "Ordinary removal destroyed retained root");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oversizedSaveFailsBeforeReplacingItsStoredRoot(GameTestHelper helper) {
        var generator = place(helper);
        CompoundTag huge = new CompoundTag();
        huge.putByteArray("payload", new byte[8_192]);
        CompoundTag outer = new CompoundTag();
        outer.put(CombustionSave.ROOT, huge);
        generator.load(outer);
        helper.assertTrue(generator.saveWithoutMetadata().get(CombustionSave.ROOT) == huge && generator.repairRequired(),
                "BE serialization omitted or copied oversized input");
        var chunk = helper.getLevel().getChunkAt(generator.getBlockPos());
        boolean refused = false;
        try {
            var outgoing = net.minecraft.world.level.chunk.storage.ChunkSerializer.write(helper.getLevel(), chunk);
            chunk.setUnsaved(false); // ChunkMap does this before its serialization/event/write sequence.
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.ChunkDataEvent.Save(chunk,
                    helper.getLevel(), outgoing));
        } catch (IllegalStateException expected) {
            refused = expected.getMessage().contains("oversized combustion input");
        }
        helper.assertTrue(refused && chunk.isUnsaved(), "Chunk save did not refuse oversized input and retain its retry");
        // Do not leave a deliberately unsavable fixture in the GameTest world's later chunk save.
        state(generator, 0, 0, 0, ItemStack.EMPTY);
        io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.restoreGameTestFixture(helper.getLevel(), chunk.getPos());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explosionsFilterRetainedDataBeforeComputingDrops(GameTestHelper helper) {
        var generator = place(helper);
        CompoundTag outer = new CompoundTag();
        outer.putInt(CombustionSave.ROOT, 42);
        generator.load(outer);
        BlockPos position = generator.getBlockPos();
        Explosion explosion = new Explosion(helper.getLevel(), null, position.getX(), position.getY(), position.getZ(),
                4.0F, false, Explosion.BlockInteraction.DESTROY);
        explosion.getToBlow().add(position);
        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, new ArrayList<>()));
        helper.assertTrue(!explosion.getToBlow().contains(position), "Quarantined block remains in explosion drops");
        generator.getBlockState().onBlockExploded(helper.getLevel(), position, explosion);
        helper.assertTrue(helper.getBlockEntity(POSITION) == generator, "Direct explosion callback removed data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void currentSnapshotSurvivesASecondLoadAndContinuesCredit(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 400, 20_000, 19_991, new ItemStack(Items.BUCKET));
        CompoundTag first = generator.saveWithoutMetadata();
        generator.load(first);
        CompoundTag second = generator.saveWithoutMetadata();
        generator.load(second);
        helper.assertTrue(first.get(CombustionSave.ROOT).equals(second.get(CombustionSave.ROOT)), "Repeated load changed snapshot");
        tick(helper, generator);
        helper.assertTrue(generator.burnState().equals(new CombustionBurn.State(440, 20_000, 19_990))
                && generator.fuelStack().is(Items.BUCKET), "Reload reset fuel credit or container");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void disabledGeneratorRetainsCreditAndAllowsFuelWithdrawal(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 400, 1_600, 500, new ItemStack(Items.COAL, 2));
        SwitchOverrides.set(CommonConfig.COMBUSTION_GENERATOR_ENABLED, false);
        try {
            tick(helper, generator);
            helper.assertTrue(generator.burnState().equals(new CombustionBurn.State(400, 1_600, 500))
                    && energy(generator).extractEnergy(40, false) == 0, "Disabled generator operated");
            helper.assertTrue(items(generator).extractItem(0, 2, false).getCount() == 2, "Disabled fuel cannot be withdrawn");
        } finally { SwitchOverrides.clear(CommonConfig.COMBUSTION_GENERATOR_ENABLED); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void overLimitForgeFuelIsNeitherInsertedNorBurned(GameTestHelper helper) {
        var generator = place(helper);
        java.util.function.Consumer<FurnaceFuelBurnTimeEvent> fuel = event -> {
            if (event.getItemStack().is(Items.DIAMOND)) { event.setBurnTime(1_000_001); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FurnaceFuelBurnTimeEvent.class, fuel);
        try {
            helper.assertTrue(items(generator).insertItem(0, new ItemStack(Items.DIAMOND), false).is(Items.DIAMOND),
                    "Over-limit custom fuel inserted");
            state(generator, 0, 0, 0, new ItemStack(Items.DIAMOND));
            tick(helper, generator);
            helper.assertTrue(generator.burnState().energy() == 0 && generator.fuelStack().is(Items.DIAMOND)
                    && generator.status() == CombustionBurn.Status.FUEL_TOO_LONG, "Over-limit credit consumed");
        } finally { MinecraftForge.EVENT_BUS.unregister(fuel); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void staleMenuAndOversizedItemInsertionCannotChangeResources(GameTestHelper helper) {
        var generator = place(helper);
        Player player = helper.makeMockPlayer();
        player.setPos(generator.getBlockPos().getX() + 0.5, generator.getBlockPos().getY(), generator.getBlockPos().getZ() + 0.5);
        var menu = new CombustionGeneratorMenu(1, player.getInventory(), generator);
        state(generator, 0, 0, 0, new ItemStack(Items.COAL));
        helper.assertTrue(menu.stillValid(player), "Nearby menu is invalid");
        ItemStack huge = new ItemStack(Items.COAL);
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("huge", new byte[8_192]);
        huge.setTag(payload);
        helper.assertTrue(items(generator).insertItem(0, huge, false) == huge && !menu.getSlot(0).mayPlace(huge),
                "Oversized item passed capability/menu preflight");
        player.setPos(generator.getBlockPos().getX() + 100, generator.getBlockPos().getY(), generator.getBlockPos().getZ());
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && generator.fuelStack().getCount() == 1,
                "Distant menu changed resources");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void menuTransportsMillionTickCreditAndPartiallyMovesContainers(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 20_000, 1_000_000, 987_654, new ItemStack(Items.BUCKET, 7));
        Player player = helper.makeMockPlayer();
        player.setPos(generator.getBlockPos().getX() + 0.5, generator.getBlockPos().getY(), generator.getBlockPos().getZ() + 0.5);
        var serverMenu = new CombustionGeneratorMenu(1, player.getInventory(), generator);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            CombustionGeneratorMenu.writeOpenData(buffer);
            var clientMenu = new CombustionGeneratorMenu(1, player.getInventory(), buffer);
            var fields = CombustionGeneratorMenu.liveData(generator);
            for (int i = 0; i < fields.getCount(); i++) {
                clientMenu.setData(i, (short) fields.get(i));
            }
            helper.assertTrue(clientMenu.energy() == 20_000 && clientMenu.duration() == 1_000_000
                    && clientMenu.remaining() == 987_654, "Signed-short transport truncated burn credit");
        } finally { buffer.release(); }
        for (int i = 0; i < 36; i++) { player.getInventory().setItem(i, new ItemStack(Items.STONE, 64)); }
        player.getInventory().setItem(8, new ItemStack(Items.BUCKET, 15));
        serverMenu.quickMoveStack(player, 0);
        helper.assertTrue(generator.fuelStack().getCount() == 6 && player.getInventory().getItem(8).getCount() == 16,
                "Partial container quick-move duplicated resources");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inboundQuickMoveCommitsOccupiedFuelAndKeepsUnacceptedRemainders(GameTestHelper helper) {
        var generator = place(helper);
        Player player = helper.makeMockPlayer();
        player.setPos(generator.getBlockPos().getX() + 0.5, generator.getBlockPos().getY(), generator.getBlockPos().getZ() + 0.5);
        var menu = new CombustionGeneratorMenu(1, player.getInventory(), generator);
        for (int occupied : new int[]{1, 60, 64}) {
            state(generator, 0, 0, 0, new ItemStack(Items.COAL, occupied));
            player.getInventory().setItem(9, new ItemStack(Items.COAL, 8));
            menu.quickMoveStack(player, 1);
            int accepted = Math.min(8, 64 - occupied);
            helper.assertTrue(generator.fuelStack().getCount() == occupied + accepted
                    && player.getInventory().getItem(9).getCount() == 8 - accepted, "Occupied inbound merge lost coal");
        }
        state(generator, 0, 0, 0, new ItemStack(Items.BUCKET));
        player.getInventory().setItem(9, new ItemStack(Items.COAL, 8));
        menu.quickMoveStack(player, 1);
        helper.assertTrue(generator.fuelStack().is(Items.BUCKET) && player.getInventory().getItem(9).getCount() == 8,
                "Incompatible occupied slot changed resources");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void breakingDropsUnspentFuelExactlyOnce(GameTestHelper helper) {
        var generator = place(helper);
        state(generator, 1_000, 1_600, 500, new ItemStack(Items.COAL, 3));
        var old = items(generator);
        helper.getLevel().destroyBlock(generator.getBlockPos(), false);
        helper.runAfterDelay(1, () -> {
            int count = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(generator.getBlockPos()).inflate(2)).stream().filter(item -> item.getItem().is(Items.COAL))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(count == 3 && old.extractItem(0, 64, false).isEmpty(), "Removal duplicated/lost fuel: " + count);
            helper.succeed();
        });
    }

    private CombustionGeneratorGameTests() { }
}
