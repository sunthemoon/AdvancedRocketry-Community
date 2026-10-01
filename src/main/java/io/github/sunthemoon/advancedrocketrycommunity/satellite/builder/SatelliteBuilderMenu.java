package io.github.sunthemoon.advancedrocketrycommunity.satellite.builder;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** Builder menu; its only intent is ASSEMBLE, and every value it shows is server authoritative. */
public final class SatelliteBuilderMenu extends AbstractContainerMenu {
    public static final int BUTTON_ASSEMBLE = 0;
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;

    private static final int PLAYER_SLOT_START = SatelliteBuilderBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_END = PLAYER_SLOT_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final long generation;
    @Nullable
    private final SatelliteBuilderBlockEntity builder;

    public SatelliteBuilderMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, new ItemStackHandler(SatelliteBuilderBlockEntity.SLOT_COUNT),
                new SimpleContainerData(SatelliteBuilderBlockEntity.MENU_DATA_COUNT),
                ContainerLevelAccess.NULL, null, readGeneration(buffer));
    }

    public SatelliteBuilderMenu(int id, Inventory playerInventory, SatelliteBuilderBlockEntity builder, long generation) {
        this(id, playerInventory, builder.menuInventory(), new MenuData(builder),
                ContainerLevelAccess.create(builder.getLevel(), builder.getBlockPos()), builder, generation);
    }

    private SatelliteBuilderMenu(
            int id,
            Inventory playerInventory,
            IItemHandler machine,
            ContainerData data,
            ContainerLevelAccess access,
            @Nullable SatelliteBuilderBlockEntity builder,
            long generation
    ) {
        super(ModMenuTypes.SATELLITE_BUILDER.get(), id);
        checkContainerDataCount(data, SatelliteBuilderBlockEntity.MENU_DATA_COUNT);
        this.data = data;
        this.access = access;
        this.builder = builder;
        this.generation = generation;
        Runnable changed = builder == null ? () -> { } : builder::setChanged;
        addSlot(new MachineSlot(machine, SatelliteBuilderBlockEntity.SLOT_CHASSIS, 18, 30, changed));
        addSlot(new MachineSlot(machine, SatelliteBuilderBlockEntity.SLOT_PRIMARY, 18, 54, changed));
        for (int index = 0; index < 6; index++) {
            addSlot(new MachineSlot(machine, SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST + index,
                    52 + (index % 3) * 20, 30 + (index / 3) * 24, changed));
        }
        addSlot(new MachineSlot(machine, SatelliteBuilderBlockEntity.SLOT_CHIP, 128, 30, changed));
        addSlot(new OutputSlot(machine, SatelliteBuilderBlockEntity.SLOT_OUTPUT, 162, 42, changed));
        addSlot(new MachineSlot(machine, SatelliteBuilderBlockEntity.SLOT_CHARGE, 198, 66, changed));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 31 + column * 18, 118 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 31 + column * 18, 176));
        }
        addDataSlots(data);
    }

    /** Menu-open data: marker, format version, position (display only) and catalog generation. */
    public static void writeOpenData(FriendlyByteBuf buffer, SatelliteBuilderBlockEntity builder, long generation) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(builder.getBlockPos());
        buffer.writeLong(generation);
    }

    private static long readGeneration(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported satellite builder menu format; update both host and client");
        }
        buffer.readBlockPos();
        return buffer.readLong();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != BUTTON_ASSEMBLE || !(player instanceof ServerPlayer serverPlayer) || builder == null
                || !stillValid(player)) {
            return false;
        }
        if (!SatelliteRuntime.allowIntent(serverPlayer, false)) {
            builder.reportRateLimited(serverPlayer);
            return false;
        }
        return builder.assemble(serverPlayer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < PLAYER_SLOT_START) {
            moved = moveItemStackTo(stack, PLAYER_SLOT_START, HOTBAR_SLOT_END, true);
        } else if (stack.is(Items.REDSTONE)) {
            moved = moveItemStackTo(stack, SatelliteBuilderBlockEntity.SLOT_CHARGE, SatelliteBuilderBlockEntity.SLOT_CHARGE + 1, false);
        } else if (stack.is(ModItems.SATELLITE_CONTROL_CHIP.get())) {
            moved = moveItemStackTo(stack, SatelliteBuilderBlockEntity.SLOT_CHIP, SatelliteBuilderBlockEntity.SLOT_CHIP + 1, false);
        } else {
            moved = moveItemStackTo(stack, SatelliteBuilderBlockEntity.SLOT_CHASSIS, SatelliteBuilderBlockEntity.SLOT_MODULE_LAST + 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return (builder == null || !builder.isRemoved() && builder.getLevel() != null
                && builder.getLevel().hasChunkAt(builder.getBlockPos())
                && builder.getLevel().getBlockEntity(builder.getBlockPos()) == builder
                && generation == SatelliteRuntime.catalogGeneration())
                && stillValid(access, player, ModBlocks.SATELLITE_BUILDER.get());
    }

    public int energyStored() {
        return data.get(0);
    }

    public int energyCapacity() {
        return data.get(1);
    }

    public SatelliteOperationCode status() {
        return code(data.get(2));
    }

    public SatelliteOperationCode previewCode() {
        return code(data.get(3));
    }

    public Optional<SatelliteStats> previewStats() {
        int battery = (data.get(5) & 0xFFFF) | ((data.get(6) & 0xFFFF) << 16);
        int dataStat = (data.get(7) & 0xFFFF) | ((data.get(8) & 0xFFFF) << 16);
        try {
            return data.get(11) == 0 ? Optional.empty()
                    : Optional.of(new SatelliteStats(data.get(4), battery, dataStat, data.get(9), data.get(10)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<SatelliteKind> previewKind() {
        int value = data.get(11) - 1;
        return value >= 0 && value < SatelliteKind.values().length ? Optional.of(SatelliteKind.values()[value]) : Optional.empty();
    }

    private static SatelliteOperationCode code(int value) {
        return value >= 0 && value < SatelliteOperationCode.values().length
                ? SatelliteOperationCode.values()[value]
                : SatelliteOperationCode.SERVER_ERROR;
    }

    /**
     * A slot over the builder's handler that marks the builder changed. Forge's {@link SlotItemHandler} hands out the
     * handler's live stack and its {@code setChanged()} reaches only a dummy container, so a partial shift-click or a
     * merge into a non-empty slot (redstone) changed the stack without marking the chunk (review C11R-C1).
     */
    private static class MachineSlot extends SlotItemHandler {
        private final Runnable changed;

        private MachineSlot(IItemHandler handler, int index, int x, int y, Runnable changed) {
            super(handler, index, x, y);
            this.changed = changed;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            changed.run();
        }
    }

    /** The package slot only gives items out. */
    private static final class OutputSlot extends MachineSlot {
        private OutputSlot(IItemHandler handler, int index, int x, int y, Runnable changed) {
            super(handler, index, x, y, changed);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return false;
        }
    }

    /** Read-only, server-authoritative menu values; battery and data are split into two 16-bit halves. */
    private static final class MenuData implements ContainerData {
        private final SatelliteBuilderBlockEntity builder;

        private MenuData(SatelliteBuilderBlockEntity builder) {
            this.builder = builder;
        }

        @Override
        public int get(int index) {
            SatelliteBuilderBlockEntity.Preview preview = builder.preview();
            SatelliteStats stats = preview.stats().orElse(null);
            return switch (index) {
                case 0 -> builder.energyStored();
                case 1 -> SatelliteBuilderBlockEntity.ENERGY_CAPACITY;
                case 2 -> builder.lastResultId();
                case 3 -> preview.code().ordinal();
                case 4 -> stats == null ? 0 : stats.power();
                case 5 -> stats == null ? 0 : stats.battery() & 0xFFFF;
                case 6 -> stats == null ? 0 : stats.battery() >>> 16;
                case 7 -> stats == null ? 0 : stats.data() & 0xFFFF;
                case 8 -> stats == null ? 0 : stats.data() >>> 16;
                case 9 -> stats == null ? 0 : stats.cargo();
                case 10 -> stats == null ? 0 : stats.rating();
                case 11 -> stats == null ? 0 : preview.definition().map(definition -> definition.kind().ordinal() + 1).orElse(0);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only.
        }

        @Override
        public int getCount() {
            return SatelliteBuilderBlockEntity.MENU_DATA_COUNT;
        }
    }
}
