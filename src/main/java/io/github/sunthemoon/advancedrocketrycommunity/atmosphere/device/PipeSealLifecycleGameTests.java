package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipeSealLifecycleGameTests {
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void insertionStopsWhenInspectionRevokesSeal(GameTestHelper helper) { abort(helper, Change.REVOKE, false); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void extractionStopsWhenInspectionRevokesSeal(GameTestHelper helper) { abort(helper, Change.REVOKE, true); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void insertionStopsWhenInspectionRemovesTarget(GameTestHelper helper) { abort(helper, Change.REMOVE, false); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void extractionStopsWhenInspectionRemovesTarget(GameTestHelper helper) { abort(helper, Change.REMOVE, true); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void insertionStopsWhenInspectionReplacesTarget(GameTestHelper helper) { abort(helper, Change.REPLACE, false); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void extractionStopsWhenInspectionReplacesTarget(GameTestHelper helper) { abort(helper, Change.REPLACE, true); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void insertionStopsWhenInspectionDisablesOperation(GameTestHelper helper) { abort(helper, Change.DISABLE, false); }
    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void extractionStopsWhenInspectionDisablesOperation(GameTestHelper helper) { abort(helper, Change.DISABLE, true); }

    private enum Change { REVOKE, REMOVE, REPLACE, DISABLE }
    private static void abort(GameTestHelper helper, Change change, boolean extraction) {
        BlockPos sealPos = new BlockPos(4, 2, 4), targetPos = helper.absolutePos(sealPos.west());
        helper.setBlock(sealPos, ModBlocks.PIPE_SEAL.get()); helper.setBlock(sealPos.west(), Blocks.CHEST);
        var seal = (PipeSealBlockEntity) helper.getBlockEntity(sealPos);
        var target = new InspectionChest(targetPos, helper.getLevel().getBlockState(targetPos));
        helper.getLevel().setBlockEntity(target);
        helper.assertTrue(DeviceAccess.loaded(target), "Inspection fixture target must be the exact loaded entity");
        IItemHandler port = seal.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.EAST).orElseThrow(IllegalStateException::new);
        if (extraction) { target.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3)); }
        var before = target.inventory.serializeNBT(); ItemStack offered = new ItemStack(Items.IRON_INGOT, 2);
        target.inspection = () -> {
            switch (change) {
                case REVOKE -> { seal.invalidateCaps(); seal.reviveCaps(); }
                case REMOVE -> helper.getLevel().removeBlockEntity(targetPos);
                case REPLACE -> helper.getLevel().setBlockEntity(new ChestBlockEntity(targetPos, target.getBlockState()));
                case DISABLE -> SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            }
        };
        try {
            if (extraction) {
                helper.assertTrue(port.extractItem(0, 1, false).isEmpty(), "Revoked forwarding extracted an item");
            } else {
                helper.assertTrue(port.insertItem(0, offered, false) == offered && offered.getCount() == 2,
                        "Revoked forwarding accepted or changed the offered item");
            }
            helper.assertTrue(target.inspections == 1 && target.mutations == 0 && before.equals(target.inventory.serializeNBT()),
                    "Inspection was not reached exactly once or the revoked target was mutated");
            if (change == Change.REPLACE) {
                var replacement = helper.getLevel().getBlockEntity(targetPos);
                helper.assertTrue(replacement != target && replacement instanceof ChestBlockEntity chest && chest.isEmpty(),
                        "Transfer redirected to a replacement target");
            }
            helper.succeed();
        } finally {
            if (change == Change.DISABLE) { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); }
            target.items.invalidate();
            helper.getLevel().removeBlockEntity(targetPos);
            helper.setBlock(sealPos.west(), Blocks.AIR); helper.setBlock(sealPos, Blocks.AIR);
        }
    }

    /** Foreign inventory whose positive slot count is returned only after a one-shot lifecycle callback. */
    private static final class InspectionChest extends ChestBlockEntity {
        private Runnable inspection = () -> { };
        private int inspections;
        private int mutations;
        private final ItemStackHandler inventory = new ItemStackHandler(1) {
            @Override public int getSlots() {
                inspections++;
                Runnable callback = inspection; inspection = () -> { }; callback.run();
                return super.getSlots();
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                mutations++; return super.insertItem(slot, stack, simulate);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                mutations++; return super.extractItem(slot, amount, simulate);
            }
        };
        private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> inventory);
        private InspectionChest(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction face) {
            return capability == ForgeCapabilities.ITEM_HANDLER ? items.cast() : super.getCapability(capability, face);
        }
    }
    private PipeSealLifecycleGameTests() { }
}
