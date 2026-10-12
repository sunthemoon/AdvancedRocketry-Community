package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/** Nine bounded unsigned-short-safe scalars. No slots, action packet, click or quick-move mutation. */
public final class SolarArrayMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 9;
    private final ContainerData data;
    @Nullable private final SolarArrayBlockEntity array;
    public SolarArrayMenu(MenuType<SolarArrayMenu> type, int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(type, id, null, readOpenData(buffer));
    }
    public SolarArrayMenu(MenuType<SolarArrayMenu> type, int id, SolarArrayBlockEntity array) {
        this(type, id, array, liveData(array));
    }
    private SolarArrayMenu(MenuType<SolarArrayMenu> type, int id,
                               @Nullable SolarArrayBlockEntity array, ContainerData data) {
        super(type, id);
        this.array = array;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }
    public static void writeOpenData(FriendlyByteBuf buffer) { buffer.writeVarInt(-2); buffer.writeVarInt(1); }
    public static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != -2 || buffer.readVarInt() != 1 || buffer.isReadable()) {
            throw new IllegalArgumentException("Unsupported solar menu format");
        }
        return new SimpleContainerData(DATA_COUNT);
    }
    public static ContainerData liveData(SolarArrayBlockEntity array) {
        return new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) { }
            @Override public int get(int index) {
                var environment = array.environment();
                return switch (index) {
                    case 0 -> array.energyStored();
                    case 1 -> array.actualCredit();
                    case 2 -> array.reason().code();
                    case 3 -> environment.sky() ? 1 : 0;
                    case 4 -> environment.day();
                    case 5 -> environment.context();
                    case 6 -> environment.weatherPermille();
                    case 7 -> array.structure().code();
                    case 8 -> array.collectors();
                    default -> 0;
                };
            }
        };
    }
    public record View(int energy, int credit, SolarGeneration.Reason reason, boolean sky, int day, int context, int weather,
                       SolarArrayRules.Structure structure, int collectors) { }
    public static View project(ContainerData data) {
        int energy = data.get(0), credit = data.get(1), reason = data.get(2), sky = data.get(3);
        int day = data.get(4), context = data.get(5), weather = data.get(6);
        int structure = data.get(7), collectors = data.get(8);
        if (energy < 0 || energy > SolarArrayRules.CAPACITY || credit < 0 || credit > SolarArrayRules.MAX_CREDIT
                || reason < 0 || reason > 7 || sky < 0 || sky > 1 || day < 0 || day > 2
                || context < 0 || context > 3 || weather < 250 || weather > 1_000
                || structure < 0 || structure > 6 || collectors < 0 || collectors > SolarGeneration.MAX_COLLECTORS) {
            return new View(0, 0, SolarGeneration.Reason.REPAIR_REQUIRED, false, 2, 0, 1_000, SolarArrayRules.Structure.UNAVAILABLE, 0);
        }
        return new View(energy, credit, SolarGeneration.Reason.fromCode(reason), sky == 1, day, context, weather, SolarArrayRules.Structure.fromCode(structure), collectors);
    }
    public View view() { return project(data); }
    public static boolean admitted(Player player, SolarArrayBlockEntity array) {
        return SolarGeneratorMenu.admitted(player, array);
    }
    @Override public boolean stillValid(Player player) {
        return array == null ? !player.isRemoved() && player.isAlive() && !player.isSpectator() && !(player instanceof FakePlayer)
                : admitted(player, array) && array.available();
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) { }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
