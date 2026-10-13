package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconServices;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconIndexSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Beacons in the GameTest Overworld, high above the test cell: the 5-by-3-by-3 pattern behind a north-facing
 * controller placed through the real item, connected players, and records written straight into the index for
 * beacons that need no block. Closing removes the controllers first (so their own records go), then every other
 * block, every direct record and every player; it is safe to call twice.
 */
final class BeaconGameTestFixture implements AutoCloseable {
    private static final TicketType<UUID> TICKET = TicketType.create("arce_beacon_test",
            Comparator.comparing(UUID::toString));
    final GameTestHelper helper;
    final ServerLevel level;
    final MinecraftServer server;
    final Map<UUID, List<String>> replies = new HashMap<>();
    private final List<BlockPos> controllers = new ArrayList<>();
    private final List<BlockPos> blocks = new ArrayList<>();
    private final List<UUID> records = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<ServerPlayer> operators = new ArrayList<>();
    private final List<ChunkPos> fixtureChunks = new ArrayList<>();
    private final UUID ticketId = UUID.randomUUID();
    private boolean closed;

    BeaconGameTestFixture(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.server = level.getServer();
        helper.assertTrue(BeaconRuntime.services().isPresent(), "The beacon services are not installed");
    }

    BeaconServices services() {
        return BeaconRuntime.services().orElseThrow();
    }

    BeaconIndexSavedData index() {
        return BeaconIndexSavedData.get(server);
    }

    /** A controller position {@code y} high above this test's cell. */
    BlockPos controllerAt(int y) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new BlockPos(origin.getX() + 1, y, origin.getZ());
    }

    /** Where a viewer stands: three blocks in front of the controller. */
    BlockPos standingBefore(BlockPos controller) {
        return controller.north(3);
    }

    ServerPlayer join(String name, BlockPos at) {
        UUID id = UUID.randomUUID();
        List<String> log = new ArrayList<>();
        replies.put(id, log);
        ServerPlayer player = ConnectedTestPlayers.join(server, id, name, level, at, log);
        players.add(player);
        return player;
    }

    /**
     * A connected player whose permission level comes from the ops list, given a level-4 entry here (the GameTest
     * server's own operator level is 0, so {@code PlayerList.op} would grant nothing).
     */
    ServerPlayer joinOperator(String name, BlockPos at) {
        UUID id = UUID.randomUUID();
        List<String> log = new ArrayList<>();
        replies.put(id, log);
        ServerPlayer player = ConnectedTestPlayers.join(server, id, name, level, at, log, true);
        players.add(player);
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 4, false));
        operators.add(player);
        helper.assertTrue(player.hasPermissions(2), "The operator fixture has no permission");
        return player;
    }

    List<String> replies(ServerPlayer player) {
        return replies.get(player.getUUID());
    }

    String lastReply(ServerPlayer player) {
        List<String> log = replies(player);
        return log.isEmpty() ? "" : log.get(log.size() - 1);
    }

    static String actionBar(String key, Object... arguments) {
        return ConnectedTestPlayers.ACTION_BAR + Component.translatable(key, arguments).getString();
    }

    /** The pattern behind a north-facing controller, without the controller; the clearance cells are made air. */
    void buildStructure(BlockPos controller) {
        BlockState casing = ModBlocks.MACHINE_CASING.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                if (dx != 0 || dz != 0) {
                    set(controller.offset(dx, 0, dz), casing);
                }
                for (int dy = 1; dy <= 4; dy++) {
                    set(controller.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            set(controller.offset(0, dy, 1), casing);
        }
        set(capOf(controller), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    static BlockPos capOf(BlockPos controller) {
        return controller.offset(0, 4, 1);
    }

    void set(BlockPos position, BlockState state) {
        if (!blocks.contains(position)) {
            blocks.add(position.immutable());
        }
        level.setBlock(position, state, Block.UPDATE_ALL);
    }

    /** Places a controller as a player does: the item used on the position, facing away from a south-facing owner. */
    PlanetaryBeaconBlockEntity place(ServerPlayer owner, BlockPos at, Optional<String> name) {
        ItemStack stack = new ItemStack(ModItems.PLANETARY_BEACON.get());
        name.ifPresent(value -> stack.setHoverName(Component.literal(value)));
        return placeStack(owner, at, stack);
    }

    /**
     * Places a beacon item stack: used by {@code placer} through the native game-mode path, or, without a placer, as a
     * block-placing dispenser does. A placer faces south, so the beacon faces north.
     */
    PlanetaryBeaconBlockEntity placeStack(@Nullable ServerPlayer placer, BlockPos at, ItemStack stack) {
        InteractionResult result;
        if (placer == null) {
            result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND,
                    stack, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        } else {
            placer.setYRot(0.0F);
            result = use(placer, at, stack);
            placer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        controllers.add(at.immutable());
        helper.assertTrue(result.consumesAction(), "Placing the beacon was refused: " + result);
        BlockState state = level.getBlockState(at);
        helper.assertTrue(state.is(ModBlocks.PLANETARY_BEACON.get()) && (placer == null
                || state.getValue(io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlock.FACING)
                == Direction.NORTH), "The beacon was not placed facing north: " + state);
        return (PlanetaryBeaconBlockEntity) level.getBlockEntity(at);
    }

    /** One right click on a block with {@code held} in the main hand, through the native game-mode path. */
    InteractionResult use(ServerPlayer player, BlockPos at, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    /** Removes a placed controller now, as a block change. */
    void removeController(BlockPos at) {
        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        controllers.remove(at);
    }

    /**
     * A record written straight into the index, for a beacon with no block. At a loaded position it is a phantom that
     * the owner's next finder snapshot removes; a last-known beacon stands at an {@link #unloaded} position.
     */
    BeaconRecord record(ServerPlayer owner, BlockPos at, String body, boolean active, String name) {
        BeaconRecord record = new BeaconRecord(UUID.randomUUID(), owner.getUUID(), body,
                level.dimension().location().toString(), at.getX(), at.getY(), at.getZ(), 0, active, name);
        restore(record);
        return record;
    }

    /** Writes a given record straight into the index, as a restarted server reads it from the saved index. */
    void restore(BeaconRecord record) {
        helper.assertTrue(index().put(record).equals(Optional.of(BeaconIndex.Admission.ADMITTED)),
                "The index refused a fixture record");
        records.add(record.beaconId());
    }

    /**
     * Fills the index root to exactly its 1 MiB bound with other owners' direct records, so that any record that
     * grows is refused with {@code BYTES_FULL}. It inserts one small record to grow later, then records of the
     * largest size while they fit, then small ones while they fit (leaving less than one small record free), and
     * finally grows the first small record by the exact number of bytes left, found by bisection. Returns the number
     * of records added; all are removed on close.
     */
    int fillIndexBytes() {
        int[] added = {0};
        UUID[] owner = {UUID.randomUUID()};
        Predicate<BeaconRecord> insert = record -> {
            Optional<BeaconIndex.Admission> admission = index().put(record);
            if (admission.equals(Optional.of(BeaconIndex.Admission.ADMITTED))) {
                records.add(record.beaconId());
                if (++added[0] % BeaconLimits.MAX_PER_OWNER == 0) {
                    owner[0] = UUID.randomUUID();
                }
                return true;
            }
            helper.assertTrue(admission.equals(Optional.of(BeaconIndex.Admission.BYTES_FULL)),
                    "The index refused a filler for another bound: " + admission);
            return false;
        };
        BeaconRecord grower = filler(owner[0], "s:b", 0, "");
        helper.assertTrue(insert.test(grower), "The index has no room for a filler");
        String largestName = "一".repeat(BeaconLimits.MAX_NAME_CHARS);
        while (insert.test(filler(owner[0], padded("f" + added[0] / BeaconLimits.MAX_PER_BODY + ":"),
                BeaconLimits.MAX_ID_CHARS - 3, largestName))) {
            helper.assertTrue(added[0] < BeaconLimits.MAX_TOTAL, "The largest fillers never filled the root");
        }
        while (insert.test(filler(owner[0], "s:b", 0, ""))) {
            helper.assertTrue(added[0] < BeaconLimits.MAX_TOTAL, "The small fillers never filled the root");
        }
        // Less than one small record (141 bytes) is left; the grower can take up to 125 + 32 more bytes.
        int low = 0;
        int high = BeaconLimits.MAX_ID_CHARS - 3 + BeaconLimits.MAX_NAME_CHARS + 1;
        while (high - low > 1) {
            int middle = (low + high) / 2;
            if (index().put(grown(grower, middle)).filter(BeaconIndex.Admission::stored).isPresent()) {
                low = middle;
            } else {
                high = middle;
            }
        }
        helper.assertTrue(index().put(grown(grower, low + 1)).equals(Optional.of(BeaconIndex.Admission.BYTES_FULL)),
                "The index root is not full after the fill");
        return added[0];
    }

    /** A filler record in a Level whose ID is {@code levelExtra} characters longer than {@code a:b}. */
    private static BeaconRecord filler(UUID owner, String body, int levelExtra, String name) {
        return new BeaconRecord(UUID.randomUUID(), owner, body, "a:" + "b".repeat(1 + levelExtra), 0, 64, 0, 0, false,
                name);
    }

    /** The grower, {@code bytes} longer: first a longer Level ID, then an ASCII name. */
    private static BeaconRecord grown(BeaconRecord grower, int bytes) {
        int levelExtra = Math.min(bytes, BeaconLimits.MAX_ID_CHARS - 3);
        return new BeaconRecord(grower.beaconId(), grower.owner(), grower.body(), "a:" + "b".repeat(1 + levelExtra),
                0, 64, 0, 0, false, "x".repeat(bytes - levelExtra));
    }

    private static String padded(String prefix) {
        return prefix + "b".repeat(BeaconLimits.MAX_ID_CHARS - prefix.length());
    }

    /** The {@code index}th position of a row far from every test, in a chunk that is not loaded. */
    BlockPos unloaded(int index) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos position = new BlockPos(origin.getX() + 1_000_000 + 16 * index, 64, origin.getZ() + 1_000_000);
        helper.assertTrue(!level.hasChunkAt(position), "A far fixture position is loaded: " + position);
        return position;
    }

    /** Test-owned tickets for an isolated chunk; releasing them permits a native unload. */
    void forceChunk(BlockPos at, boolean force) {
        ChunkPos chunk = new ChunkPos(at);
        if (!fixtureChunks.contains(chunk)) {
            helper.assertTrue(force && !level.getForcedChunks().contains(chunk.toLong()),
                    "The fixture does not own this chunk's forced ticket");
            fixtureChunks.add(chunk);
        }
        if (force) {
            level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketId);
        } else {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketId);
        }
        level.setChunkForced(chunk.x, chunk.z, force);
        if (force) {
            level.getChunkAt(at);
        }
    }

    String body(BlockPos at) {
        Optional<String> body = services().body(server, level.dimension(), at);
        helper.assertTrue(body.isPresent(), "The GameTest Overworld has no server-resolved body");
        return body.get();
    }

    /** Polls {@code ready} once per tick for at most {@code ticks}, then runs {@code next}; closes on failure. */
    void await(int ticks, String what, BooleanSupplier ready, Runnable next) {
        helper.runAfterDelay(1, () -> {
            boolean done;
            try {
                done = ready.getAsBoolean();
                if (!done && ticks <= 1) {
                    throw new GameTestAssertException("Timed out waiting for " + what);
                }
                if (done) {
                    next.run();
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
            if (!done) {
                await(ticks - 1, what, ready, next);
            }
        });
    }

    /** The last step: closes the fixture and passes the test. */
    void succeed() {
        close();
        helper.succeed();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (BlockPos controller : List.copyOf(controllers)) {
                level.setBlock(controller, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (int i = blocks.size() - 1; i >= 0; i--) {
                level.setBlock(blocks.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            records.forEach(id -> index().remove(id));
        } finally {
            for (ChunkPos chunk : fixtureChunks) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketId);
                level.setChunkForced(chunk.x, chunk.z, false);
            }
            for (ServerPlayer operator : operators) {
                server.getPlayerList().getOps().remove(operator.getGameProfile());
            }
            for (ServerPlayer player : players) {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    server.getPlayerList().remove(player);
                }
            }
        }
    }
}
