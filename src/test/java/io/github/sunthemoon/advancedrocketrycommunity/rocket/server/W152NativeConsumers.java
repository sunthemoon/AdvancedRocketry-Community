package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/** Explicit native test artifact: ordinary independent recipient saves, never a production publication fence. */
final class W152NativeConsumers {
    static final BlockPos RECIPIENT = new BlockPos(80, 100, 15);
    private static final List<BlockPos> SOURCES = List.of(new BlockPos(15, 100, 16), new BlockPos(16, 100, 15));
    private final MinecraftServer server;
    private final String experiment;
    private final Consumer<String> pause;
    private final List<ChunkPos> saves = new ArrayList<>();
    private boolean consumed, pending;
    private int saved;
    private EscrowBarrier.Result result;

    W152NativeConsumers(MinecraftServer server, String experiment, Consumer<String> pause) {
        this.server = server; this.experiment = experiment; this.pause = pause;
    }
    boolean active() { return !experiment.isEmpty(); }
    boolean vanilla() { return experiment.equals("VANILLA_SOURCE_CONSUMER_ONLY"); }
    void loadRecipient() {
        if (!active()) { return; }
        var level = server.overworld(); level.setChunkForced(5, 0, true); level.getChunk(5, 0);
    }
    void prepareRecipient() {
        if (!active()) { return; }
        server.overworld().setBlock(RECIPIENT, Blocks.CHEST.defaultBlockState(), 3);
        require(port(RECIPIENT).getStackInSlot(0).isEmpty(), "Recipient baseline is not empty");
    }
    void prepareVanilla() {
        var level = server.overworld();
        for (int x = 0; x < 2; x++) { for (int z = 0; z < 2; z++) {
            var at = new BlockPos(15 + x, 100, 15 + z);
            var state = x == z ? Blocks.IRON_BLOCK.defaultBlockState() : x == 1
                    ? Blocks.CHEST.defaultBlockState() : Blocks.BARREL.defaultBlockState();
            level.setBlock(at, state, 3);
            if (x != z) {
                ((RandomizableContainerBlockEntity) level.getBlockEntity(at)).setItem(0, labelled(true, 7));
            }
        } }
    }
    /** Called only after installed C0-C5/lazy publication is stable (or for the ordinary vanilla control). */
    void tick() {
        if (!consumed) {
            var recipient = port(RECIPIENT);
            for (var at : SOURCES) {
                var source = port(at); var original = source.extractItem(0, 7, false);
                require(ItemStack.matches(original, labelled(true, 7)), "Consumer extracted another resource");
                require(recipient.insertItem(0, original, false).isEmpty(), "Recipient refused original resource");
                require(source.insertItem(0, labelled(false, 3), false).isEmpty(), "Source refused foreign resource");
            }
            require(ItemStack.matches(recipient.getStackInSlot(0), labelled(true, 14)), "Recipient count/identity differs");
            if (experiment.equals("BOTH_SIDES_SAVED")) {
                for (int x = 0; x <= 1; x++) { for (int z = 0; z <= 1; z++) { saves.add(new ChunkPos(x, z)); } }
            }
            saves.add(new ChunkPos(RECIPIENT)); consumed = true;
        }
        if (result != null) {
            var completed = result; result = null; pending = false;
            try { require(completed.confirmed(), "Consumer terrain proof failed: " + completed.reason()); }
            finally { if (completed.proof() != null) { completed.proof().release(); } }
            saved++;
        }
        if (saved == saves.size()) { pause.accept(experiment); return; }
        if (pending) { return; }
        var runtime = EscrowRuntime.of(server); var store = runtime.store(server.overworld());
        String dimension = server.overworld().dimension().location().toString(); var chunk = saves.get(saved);
        pending = runtime.barrier().beginOrdinaryTerrain(store, dimension, chunk,
                root -> contents(root, chunk), server.getTickCount(), label -> { }, value -> result = value);
    }
    private boolean contents(CompoundTag root, ChunkPos chunk) {
        if (root.getInt("xPos") != chunk.x || root.getInt("zPos") != chunk.z) { return false; }
        if (chunk.equals(new ChunkPos(RECIPIENT))) { return exact(root, RECIPIENT, labelled(true, 14)); }
        for (var source : SOURCES) {
            if (chunk.equals(new ChunkPos(source)) && !exact(root, source, labelled(false, 3))) { return false; }
        }
        return true;
    }
    private static boolean exact(CompoundTag root, BlockPos at, ItemStack stack) {
        for (var raw : root.getList("block_entities", Tag.TAG_COMPOUND)) {
            var be = (CompoundTag) raw;
            if (be.getInt("x") != at.getX() || be.getInt("y") != at.getY() || be.getInt("z") != at.getZ()) { continue; }
            var items = be.getList("Items", Tag.TAG_COMPOUND);
            return items.size() == 1 && ItemStack.matches(ItemStack.of(items.getCompound(0)), stack);
        }
        return false;
    }
    private IItemHandler port(BlockPos at) {
        var be = server.overworld().getBlockEntity(at);
        require(be != null, "Native container is absent at " + at);
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Container capability absent"));
    }
    private static ItemStack labelled(boolean original, int count) {
        var stack = new ItemStack(original ? Items.DIAMOND : Items.GOLD_INGOT, count);
        stack.getOrCreateTag().putString("W152", original ? "original" : "foreign"); return stack;
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
