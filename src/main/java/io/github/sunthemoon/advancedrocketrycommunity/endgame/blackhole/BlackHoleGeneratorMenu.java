package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * ADR-057 section 6 menu: the 9 fuel slots (plain items only), energy, the output rate and the remaining burn as
 * data slots, and the orbit body and status in the device view. It has no buttons: no intent changes generation.
 */
public final class BlackHoleGeneratorMenu extends EndgameDeviceMenu {
    public static final int FORMAT_MARKER = -1;
    public static final int FORMAT_VERSION = 1;
    public static final int DATA_COUNT = 5;
    private static final int PLAYER_START = BlackHoleGeneratorBlockEntity.FUEL_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final ContainerData data;

    public BlackHoleGeneratorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, null, new ItemStackHandler(BlackHoleGeneratorBlockEntity.FUEL_SLOTS), readOpenData(buffer));
    }

    public BlackHoleGeneratorMenu(int id, Inventory inventory, BlackHoleGeneratorBlockEntity generator) {
        this(id, inventory, generator, generator.fuel(), new Data(generator));
    }

    private BlackHoleGeneratorMenu(int id, Inventory inventory, BlackHoleGeneratorBlockEntity generator,
                                   IItemHandler fuel, ContainerData data) {
        super(ModMenuTypes.BLACK_HOLE_GENERATOR.get(), id, generator == null ? null : targetOf(generator),
                inventory.player);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        for (int slot = 0; slot < BlackHoleGeneratorBlockEntity.FUEL_SLOTS; slot++) {
            addSlot(new FuelSlot(fuel, slot, 62 + (slot % 3) * 18, 18 + (slot / 3) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 104 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 162));
        }
        addDataSlots(data);
    }

    public static void writeOpenData(FriendlyByteBuf buffer, BlackHoleGeneratorBlockEntity generator) {
        buffer.writeVarInt(FORMAT_MARKER);
        buffer.writeVarInt(FORMAT_VERSION);
        buffer.writeBlockPos(generator.getBlockPos());
    }

    private static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != FORMAT_MARKER || buffer.readVarInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported black-hole generator menu format; update both host and client");
        }
        buffer.readBlockPos();
        return new SimpleContainerData(DATA_COUNT);
    }

    @Override
    protected Optional<EndgameIntentGuard.Intent> intent(int button) {
        return Optional.empty();
    }

    @Override
    protected EndgameCode apply(int button, ServerPlayer player, EndgameDeviceBlockEntity device) {
        return EndgameCode.OK;
    }

    @Override
    protected EndgameDeviceView view(int containerId, EndgameDeviceBlockEntity device, boolean detail) {
        BlackHoleGeneratorBlockEntity generator = (BlackHoleGeneratorBlockEntity) device;
        List<EndgameDeviceView.Line> lines = new ArrayList<>();
        lines.add(EndgameDeviceView.Line.key(OrbitalLaserDrillMenu.VIEW + "structure",
                generator.structureCode().translationKey()));
        if (detail) {
            generator.orbitBody().ifPresent(body -> lines.add(EndgameDeviceView.Line.text(OrbitalLaserDrillMenu.VIEW
                    + "body", body.toString())));
            generator.orbitBody().flatMap(body -> EndgameRuntime.devices().flatMap(devices -> devices.celestial()
                    .flatMap(catalog -> devices.blackHoleData().at(body, catalog)))).ifPresent(profile -> lines.add(
                    EndgameDeviceView.Line.text(OrbitalLaserDrillMenu.VIEW + "fuel_table",
                            profile.fuelTable().toString())));
        }
        return new EndgameDeviceView(containerId, EndgameSystem.BLACK_HOLE_GENERATOR, generator.status(),
                generator.status(), detail, lines);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PLAYER_START ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                : BlackHoleGeneratorBlockEntity.plain(stack) && moveItemStackTo(stack, 0, PLAYER_START, false);
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

    public int energyStored() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int rate() {
        return data.get(2) & 0xFFFF;
    }

    public int remaining() {
        return (data.get(3) & 0xFFFF) | ((data.get(4) & 0xFFFF) << 16);
    }

    private static final class FuelSlot extends SlotItemHandler {
        FuelSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack) {
            return BlackHoleGeneratorBlockEntity.plain(stack) && super.mayPlace(stack);
        }
    }

    private static final class Data implements ContainerData {
        private final BlackHoleGeneratorBlockEntity generator;

        Data(BlackHoleGeneratorBlockEntity generator) {
            this.generator = generator;
        }

        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> generator.energy() & 0xFFFF;
                case 1 -> (generator.energy() >>> 16) & 0xFFFF;
                case 2 -> generator.rate() & 0xFFFF;
                case 3 -> generator.remaining() & 0xFFFF;
                case 4 -> (generator.remaining() >>> 16) & 0xFFFF;
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
    }
}
