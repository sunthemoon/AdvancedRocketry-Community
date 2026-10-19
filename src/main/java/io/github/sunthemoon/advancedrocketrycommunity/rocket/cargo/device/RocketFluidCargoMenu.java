package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Design section 3's menu: no slots and no buttons, only eleven scalar values that vanilla synchronizes as 16-bit shorts.
 * The amount and the capacity take one slot each, the Fluid takes the low and high halves of the session's numeric
 * registry ID (-1 when empty), then the status ordinal whether a binding is held, the fixed role and optional relative cursor coordinates. The client never sends any of it
 * back, and the open data is a format number only.
 */
public final class RocketFluidCargoMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 11;
    public static final int NO_FLUID = -1;
    private static final int FORMAT = 1;
    @Nullable private final RocketFluidCargoBlockEntity loader;
    /** The device identity the menu opened on; a changed root closes it. */
    @Nullable private final UUID device;
    private final Player viewer;
    private final ContainerData data;

    public RocketFluidCargoMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, readOpenData(buffer));
    }

    public RocketFluidCargoMenu(int id, Inventory inventory, RocketFluidCargoBlockEntity loader) {
        this(id, inventory, loader, liveData(loader));
    }

    private RocketFluidCargoMenu(int id, Inventory inventory, @Nullable RocketFluidCargoBlockEntity loader, ContainerData data) {
        super(ModMenuTypes.ROCKET_FLUID_CARGO.get(), id);
        this.loader = loader;
        this.device = loader == null ? null : loader.root().map(RocketFluidCargoRoot::device).orElse(null);
        this.viewer = inventory.player;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(FORMAT);
    }

    public static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT || buffer.isReadable()) {
            throw new IllegalArgumentException("Unsupported rocket fluid cargo menu format");
        }
        return new SimpleContainerData(DATA_COUNT);
    }

    /** The session's numeric registry ID of the tank's Fluid, or {@link #NO_FLUID}. */
    static int fluidId(LoaderTank tank) {
        if (tank.isEmpty()) {
            return NO_FLUID;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(tank.fluid());
        return fluid == null ? NO_FLUID : BuiltInRegistries.FLUID.getId(fluid);
    }

    public static ContainerData liveData(RocketFluidCargoBlockEntity loader) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> loader.tank().amount();
                    case 1 -> LoaderTank.CAPACITY;
                    case 2 -> fluidId(loader.tank()) & 0xFFFF;
                    case 3 -> (fluidId(loader.tank()) >>> 16) & 0xFFFF;
                    case 4 -> loader.displayStatus().ordinal();
                    case 5 -> loader.root().filter(root -> root.binding() != null).isPresent() ? 1 : 0;
                    case 6 -> loader.role().ordinal();
                    case 7 -> loader.cursor().isPresent() ? 1 : 0;
                    case 8 -> loader.cursor().map(BlockPos::getX).orElse(0);
                    case 9 -> loader.cursor().map(BlockPos::getY).orElse(0);
                    case 10 -> loader.cursor().map(BlockPos::getZ).orElse(0);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** The synchronized values; an unknown status ordinal is null and shown with a generic label. */
    public record View(int amount, int capacity, int fluidId, @Nullable RocketCargoStatus status, boolean bound,
                       @Nullable RocketCargoRole role, @Nullable BlockPos cursor) {
    }

    /** Rebuilds the values from the sign-extended shorts that vanilla delivers. */
    public View view() {
        int fluid = (data.get(3) & 0xFFFF) << 16 | data.get(2) & 0xFFFF;
        int ordinal = data.get(4);
        RocketCargoStatus[] statuses = RocketCargoStatus.values();
        return new View(data.get(0) & 0xFFFF, data.get(1) & 0xFFFF, fluid,
                ordinal >= 0 && ordinal < statuses.length ? statuses[ordinal] : null, data.get(5) == 1,
                data.get(6) >= 0 && data.get(6) < RocketCargoRole.values().length ? RocketCargoRole.values()[data.get(6)] : null,
                data.get(7) == 1 ? new BlockPos((short) data.get(8), (short) data.get(9), (short) data.get(10)) : null);
    }

    @Nullable
    public RocketFluidCargoBlockEntity loader() {
        return loader;
    }

    /** The exact block entity, still loaded, still the same device and still admitting this owner. */
    @Override
    public boolean stillValid(Player player) {
        return player == viewer && (loader == null || device != null && loader.admitted(player)
                && loader.root().map(RocketFluidCargoRoot::device).filter(device::equals).isPresent());
    }

    /** There are no slots; a click, replayed or not, changes nothing. */
    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
