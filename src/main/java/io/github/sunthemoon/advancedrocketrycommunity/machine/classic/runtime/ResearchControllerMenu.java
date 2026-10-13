package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Fixed schema-1 wire: exactly 16 signed-short DataSlots, three masked 32-bit pairs. */
public final class ResearchControllerMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 16, MAX_FE = 640_000;
    private final ResearchControllerBlockEntity controller;
    private final UUID viewer;
    private final ContainerData data;
    public ResearchControllerMenu(int id, Inventory inventory, FriendlyByteBuf opening) {
        this(id, null, null, readOpening(opening));
    }
    public ResearchControllerMenu(int id, ResearchControllerBlockEntity controller, Player player) {
        this(id, controller, player.getUUID(), new SimpleContainerData(DATA_COUNT)); refresh();
    }
    private ResearchControllerMenu(int id, ResearchControllerBlockEntity controller, UUID viewer, ContainerData data) {
        super(ModMenuTypes.GROUND_RESEARCH.get(), id); this.controller = controller; this.viewer = viewer; this.data = data; addDataSlots(data);
    }
    public static void writeOpening(FriendlyByteBuf buffer, BlockPos position, boolean processor) {
        if (buffer.writerIndex() != 0) { throw new IllegalArgumentException("Research opening must be empty"); }
        buffer.writeBlockPos(position); buffer.writeByte(1); buffer.writeByte(DATA_COUNT); buffer.writeBoolean(processor);
    }
    public static ContainerData readOpening(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() != 11) { throw new IllegalArgumentException("Research opening length"); }
        buffer.readBlockPos();
        if (buffer.readUnsignedByte() != 1 || buffer.readUnsignedByte() != DATA_COUNT) { throw new IllegalArgumentException("Research opening schema"); }
        int kind = buffer.readUnsignedByte(); if (kind > 1) { throw new IllegalArgumentException("Research opening kind"); }
        var data = new SimpleContainerData(DATA_COUNT); data.set(15, kind); return data;
    }
    public static int wide(ContainerData data, int low, int maximum) {
        long value = (data.get(low) & 0xFFFFL) | (data.get(low + 1) & 0xFFFFL) << 16;
        return value <= maximum ? (int) value : 0;
    }
    public static void setWide(ContainerData data, int low, int value) { data.set(low, value & 0xFFFF); data.set(low + 1, (value >>> 16) & 0xFFFF); }
    private void refresh() {
        if (controller == null || !(controller.getLevel() instanceof ServerLevel level)) { return; }
        var service = ResearchRuntime.service(level); var job = service.displayed(controller).orElse(null);
        data.set(0, controller.researchStatus().ordinal());
        int relation = controller.researchOwner().map(owner -> owner.equals(viewer) ? 1 : 2).orElse(0);
        data.set(1, (job == null ? 0 : job.phase().ordinal() + 1 | (job.analysis().state().ordinal() + 1) << 3) | relation << 5);
        setWide(data, 2, job == null ? 0 : controller.processor() ? job.analysis().progress() : job.surveyProgress());
        setWide(data, 4, job == null ? 0 : controller.processor() ? job.analysis().ticks() : job.surveyTicks());
        setWide(data, 6, controller.storedEnergy());
        data.set(8, job == null ? ResearchService.ENERGY_PER_TICK : controller.processor() ? job.analysis().energy() : job.surveyEnergy());
        for (int index = 9; index <= 12; index++) { data.set(index, 0); }
        controller.firstDiagnostic().ifPresent(diagnostic -> {
            data.set(9, diagnostic.localPosition().x()); data.set(10, diagnostic.localPosition().y()); data.set(11, diagnostic.localPosition().z());
            data.set(12, expectedRole(diagnostic.expected()));
        });
        var choices = service.choices(controller); int index = -1;
        for (int i = 0; i < choices.size(); i++) { if (choices.get(i).jobId().equals(controller.selection())) { index = i; } }
        data.set(13, Math.max(0, index)); data.set(14, choices.size()); data.set(15, controller.processor() ? 1 : 0);
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    public ResearchStatus status() { return ResearchStatus.fromCode(data.get(0)); }
    public int progress() { return wide(data, 2, 72_000); }
    public int duration() { return wide(data, 4, 72_000); }
    public int energy() { return wide(data, 6, MAX_FE); }
    public int requiredEnergy() { return data.get(8) >= 0 && data.get(8) <= 10_000 ? data.get(8) : 0; }
    public int ownerRelation() { return (data.get(1) >>> 5) & 3; }
    public int phaseCode() { return data.get(1) & 7; }
    public int analysisCode() { return (data.get(1) >>> 3) & 3; }
    public boolean processor() { return data.get(15) == 1; }
    public int selectionIndex() { return Math.min(15, Math.max(0, data.get(13))); }
    public int selectionCount() { return Math.min(16, Math.max(0, data.get(14))); }
    public int diagnostic(int axis) { return axis >= 0 && axis < 3 ? (short) data.get(9 + axis) : 0; }
    public int diagnosticRole() { return data.get(12) >= 0 && data.get(12) <= 10 ? data.get(12) : 0; }
    static int expectedRole(String expected) {
        return switch (expected) {
            case "air" -> 1;
            case "block=advancedrocketrycommunity:machine_casing" -> 2;
            case "block=minecraft:glass" -> 3;
            case "tag=minecraft:slabs" -> 4;
            case "tag=advancedrocketrycommunity:motors" -> 5;
            case "port=energy_input" -> 6;
            case "tag=advancedrocketrycommunity:observatory_shell_parts" -> 7;
            case "tag=advancedrocketrycommunity:machine_clearance" -> 8;
            case "controller" -> 9;
            default -> 10;
        };
    }
    @Override public boolean stillValid(Player player) { return controller == null ? player.isAlive() && !player.isRemoved() : ClassicControllerMenu.canOpen(player, controller); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (controller == null || button < 0 || button > 5 || !stillValid(player) || player.containerMenu != this
                || !player.getUUID().equals(viewer) || !(controller.getLevel() instanceof ServerLevel level)) { return false; }
        var service = ResearchRuntime.service(level);
        return service.allowIntent(player.getUUID()) && service.intent(controller, player.getUUID(), button);
    }
    @Override public void clicked(int slot, int button, ClickType click, Player player) { }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
