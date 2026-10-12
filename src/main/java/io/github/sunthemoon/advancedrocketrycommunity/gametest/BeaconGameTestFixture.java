package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconServices;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconIndexSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
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
    final GameTestHelper helper;
    final ServerLevel level;
    final MinecraftServer server;
    final Map<UUID, List<String>> replies = new HashMap<>();
    private final List<BlockPos> controllers = new ArrayList<>();
    private final List<BlockPos> blocks = new ArrayList<>();
    private final List<UUID> records = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<ServerPlayer> operators = new ArrayList<>();
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

    /** A record written straight into the index, for a beacon with no block. */
    BeaconRecord record(ServerPlayer owner, BlockPos at, String body, boolean active, String name) {
        BeaconRecord record = new BeaconRecord(UUID.randomUUID(), owner.getUUID(), body,
                level.dimension().location().toString(), at.getX(), at.getY(), at.getZ(), 0, active, name);
        helper.assertTrue(index().put(record).equals(Optional.of(BeaconIndex.Admission.ADMITTED)),
                "The index refused a fixture record");
        records.add(record.beaconId());
        return record;
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
