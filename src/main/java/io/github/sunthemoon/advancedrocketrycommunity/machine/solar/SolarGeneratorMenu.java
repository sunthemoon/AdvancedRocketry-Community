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

/** Seven bounded unsigned-short-safe scalars. No slots, action packet, click or quick-move mutation. */
public final class SolarGeneratorMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 7;
    private final ContainerData data;
    @Nullable private final SolarGeneratorBlockEntity generator;
    public SolarGeneratorMenu(MenuType<SolarGeneratorMenu> type, int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(type, id, null, readOpenData(buffer));
    }
    public SolarGeneratorMenu(MenuType<SolarGeneratorMenu> type, int id, SolarGeneratorBlockEntity generator) {
        this(type, id, generator, liveData(generator));
    }
    private SolarGeneratorMenu(MenuType<SolarGeneratorMenu> type, int id,
                               @Nullable SolarGeneratorBlockEntity generator, ContainerData data) {
        super(type, id);
        this.generator = generator;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }
    public static void writeOpenData(FriendlyByteBuf buffer) { buffer.writeVarInt(-1); buffer.writeVarInt(1); }
    public static ContainerData readOpenData(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != -1 || buffer.readVarInt() != 1 || buffer.isReadable()) {
            throw new IllegalArgumentException("Unsupported solar menu format");
        }
        return new SimpleContainerData(DATA_COUNT);
    }
    public static ContainerData liveData(SolarGeneratorBlockEntity generator) {
        return new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) { }
            @Override public int get(int index) {
                var environment = generator.environment();
                return switch (index) {
                    case 0 -> generator.energyStored();
                    case 1 -> generator.actualCredit();
                    case 2 -> generator.reason().code();
                    case 3 -> environment.sky() ? 1 : 0;
                    case 4 -> environment.day();
                    case 5 -> environment.context();
                    case 6 -> environment.weatherPermille();
                    default -> 0;
                };
            }
        };
    }
    public record View(int energy, int credit, SolarGeneration.Reason reason, boolean sky, int day, int context, int weather) { }
    public static View project(ContainerData data) {
        int energy = data.get(0), credit = data.get(1), reason = data.get(2), sky = data.get(3);
        int day = data.get(4), context = data.get(5), weather = data.get(6);
        if (energy < 0 || energy > SolarGeneration.CAPACITY || credit < 0 || credit > SolarGeneration.MAX_CREDIT
                || reason < 0 || reason > 7 || sky < 0 || sky > 1 || day < 0 || day > 2
                || context < 0 || context > 3 || weather < 250 || weather > 1_000) {
            return new View(0, 0, SolarGeneration.Reason.REPAIR_REQUIRED, false, 2, 0, 1_000);
        }
        return new View(energy, credit, SolarGeneration.Reason.fromCode(reason), sky == 1, day, context, weather);
    }
    public View view() { return project(data); }
    public static boolean admitted(Player player, SolarGeneratorBlockEntity generator) {
        return player instanceof ServerPlayer connected && !(player instanceof FakePlayer) && player.isAlive()
                && !player.isRemoved() && !player.isSpectator() && !connected.hasDisconnected() && connected.connection != null
                && generator.getLevel() == player.level() && connected.getServer() != null
                && connected.getServer().isSameThread()
                && connected.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.distanceToSqr(generator.getBlockPos().getX() + 0.5, generator.getBlockPos().getY() + 0.5,
                        generator.getBlockPos().getZ() + 0.5) <= 64;
    }
    @Override public boolean stillValid(Player player) {
        return generator == null ? !player.isRemoved() && player.isAlive() && !player.isSpectator() && !(player instanceof FakePlayer)
                : admitted(player, generator) && generator.available();
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) { }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
