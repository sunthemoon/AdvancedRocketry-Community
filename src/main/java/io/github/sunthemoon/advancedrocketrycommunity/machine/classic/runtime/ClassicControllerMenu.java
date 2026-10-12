package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/** Eight unsigned-short-safe scalars; no resource slots or client-authorized machine operations. */
public final class ClassicControllerMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 8;
    private final ClassicControllerBlockEntity owner;
    private final ContainerData data;
    public ClassicControllerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, null, readOpening(buffer)); }
    public ClassicControllerMenu(int id, ClassicControllerBlockEntity owner) {
        this(id, owner, new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public void set(int index, int value) { }
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> owner.formed() ? 1 : 0;
                    case 1 -> owner.progressTicks() & 0xFFFF; case 2 -> owner.progressTicks() >>> 16;
                    case 3 -> owner.durationTicks() & 0xFFFF; case 4 -> owner.durationTicks() >>> 16;
                    case 5 -> owner.storedEnergy() & 0xFFFF; case 6 -> owner.storedEnergy() >>> 16;
                    case 7 -> owner.reason().ordinal(); default -> 0;
                };
            }
        });
    }
    private ClassicControllerMenu(int id, ClassicControllerBlockEntity owner, ContainerData data) {
        super(ModMenuTypes.LATHE.get(), id);
        if (data.getCount() != DATA_COUNT) { throw new IllegalArgumentException("Classic menu data count"); }
        this.owner = owner; this.data = data; addDataSlots(data);
    }
    public static void writeOpening(FriendlyByteBuf buffer, BlockPos position) {
        if (buffer.writerIndex() != 0) { throw new IllegalArgumentException("Opening buffer must be empty"); }
        buffer.writeBlockPos(position); buffer.writeByte(1); buffer.writeByte(DATA_COUNT);
    }
    public static ContainerData readOpening(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() != 10) { throw new IllegalArgumentException("Classic opening frame length"); }
        buffer.readBlockPos();
        if (buffer.readUnsignedByte() != 1 || buffer.readUnsignedByte() != DATA_COUNT) { throw new IllegalArgumentException("Classic opening schema"); }
        return new SimpleContainerData(DATA_COUNT);
    }
    public boolean formed() { return data.get(0) == 1; }
    private int integer(int index, int maximum) {
        int low = data.get(index) & 0xFFFF, high = data.get(index + 1) & 0xFFFF;
        long value = low | (long) high << 16; return value <= maximum ? (int) value : 0;
    }
    public int progress() { return integer(1, 72_000); }
    public int duration() { return integer(3, 72_000); }
    public int energy() { return integer(5, 640_000); }
    public ClassicWait reason() { return ClassicWait.fromCode(data.get(7)); }
    public static boolean canOpen(Player player, ClassicControllerBlockEntity owner) {
        return player instanceof ServerPlayer server && !(player instanceof FakePlayer) && !server.hasDisconnected()
                && server.connection != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.level() == owner.getLevel() && owner.physical() && player.level().mayInteract(player, owner.getBlockPos())
                && server.getServer() != null && server.getServer().isSameThread()
                && server.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.distanceToSqr(owner.getBlockPos().getX() + 0.5, owner.getBlockPos().getY() + 0.5, owner.getBlockPos().getZ() + 0.5) <= 64;
    }
    @Override public boolean stillValid(Player player) { return owner == null ? player.isAlive() && !player.isRemoved() : canOpen(player, owner); }
    @Override public void clicked(int slot, int button, ClickType click, Player player) { }
    @Override public boolean clickMenuButton(Player player, int button) { return false; }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
