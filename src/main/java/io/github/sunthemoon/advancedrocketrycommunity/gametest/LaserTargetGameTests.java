package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-054 sections 9 and 9.1 for the laser target: registration on persistence, removal, retirement and freezing. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserTargetGameTests {
    private LaserTargetGameTests() {
    }

    @GameTest(template = "empty", batch = "endgame_laser_target", timeoutTicks = 300)
    public static void aTargetRegistersOnceSavedAndARemovedIdComesBackFrozen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState state = ModBlocks.LASER_TARGET.get().defaultBlockState();
        helper.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE)
                        && state.getPistonPushReaction() == PushReaction.BLOCK
                        && state.getBlock().getExplosionResistance() >= 1200.0F,
                "The target lacks the endpoint protections");
        level.setBlockAndUpdate(pos, state);
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        UUID owner = UUID.randomUUID();
        helper.assertTrue(target.assignOwner(owner), "The fixture owner was not assigned");
        UUID id = target.deviceId().orElseThrow();
        CompoundTag[] copy = new CompoundTag[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE,
                        "Not awaiting a save: " + target.describe()))
                .thenExecuteAfter(25, () -> helper.assertTrue(root().endpoint(id).isEmpty()
                        && target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE, "Registered before any save"))
                .thenWaitUntil(() -> {
                    chunkSaved(level, pos);
                    helper.assertTrue(target.endpointStatus() == EndgameCode.OK && target.endpointActive(),
                            "Not registered after the save: " + target.describe());
                })
                .thenExecute(() -> {
                    EndpointRecord record = root().endpoint(id).orElseThrow();
                    helper.assertTrue(record.kind().equals(LaserTargetBlockEntity.KIND) && record.owner().equals(owner)
                                    && record.pos() == pos.asLong() && record.level().equals(level.dimension().location()),
                            "The record does not match the target");
                    target.buffer().setStackInSlot(4, new ItemStack(Items.COBBLESTONE, 7));
                    var items = target.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                            .orElseThrow(IllegalStateException::new);
                    helper.assertTrue(items.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1
                            && items.extractItem(4, 2, false).getCount() == 2, "The buffer is not extract-only");
                    copy[0] = target.saveWithoutMetadata();
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(root().endpoint(id).isEmpty() && root().retired(id),
                            "A removed target was not retired");
                    int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D)).stream()
                            .mapToInt(entity -> entity.getItem().is(Items.COBBLESTONE) ? entity.getItem().getCount() : 0)
                            .sum();
                    helper.assertTrue(dropped == 5, "The buffer did not drop: " + dropped);
                    // A copy of the removed target comes back (a crash or a block mover): it is retired and frozen.
                    level.setBlockAndUpdate(pos, state);
                    ((LaserTargetBlockEntity) level.getBlockEntity(pos)).load(copy[0]);
                })
                .thenWaitUntil(() -> {
                    LaserTargetBlockEntity back = (LaserTargetBlockEntity) level.getBlockEntity(pos);
                    helper.assertTrue(back.endpointStatus() == EndgameCode.ENDPOINT_RETIRED && back.frozen()
                            && !back.endpointActive(), "A returning copy was not frozen: " + back.describe());
                })
                .thenExecute(() -> {
                    helper.assertTrue(root().endpoint(id).isEmpty(), "A frozen copy registered again");
                    ((LaserTargetBlockEntity) level.getBlockEntity(pos)).buffer().setStackInSlot(4, ItemStack.EMPTY);
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D)).forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    /**
     * Review C11R-M1: a reset raises the marker generation, so a link dropped by a reset is never adopted again, also
     * after many more links and resets; a new link and a link recorded at the current generation are; the generation
     * survives a save.
     */
    @GameTest(template = "empty", batch = "endgame_laser_target_generation", timeoutTicks = 100)
    public static void aResetMarkerNeverAdoptsAnEarlierLinkAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        UUID owner = UUID.randomUUID();
        helper.assertTrue(target.assignOwner(owner), "The fixture owner was not assigned");
        try {
            UUID controllerA = UUID.randomUUID();
            UUID linkA = UUID.randomUUID();
            helper.assertTrue(target.generation() == 0 && target.accepts(controllerA, linkA, -1L),
                    "A new link was refused by a new marker");
            target.adopt(controllerA, linkA);
            long touchedA = target.generation();
            target.reset(owner);
            helper.assertTrue(!target.accepts(controllerA, linkA, touchedA), "A reset link was adopted again");
            UUID controllerB = UUID.randomUUID();
            for (int i = 0; i < 12; i++) {
                UUID link = UUID.randomUUID();
                helper.assertTrue(target.accepts(controllerB, link, -1L), "A new link was refused");
                target.adopt(controllerB, link);
                target.reset(owner);
            }
            helper.assertTrue(target.generation() == 13 && !target.accepts(controllerA, linkA, touchedA),
                    "A reset link was adopted again after 12 more resets");
            // A marker a crash returned unlinked at the generation the link recorded still adopts it.
            helper.assertTrue(target.accepts(controllerA, linkA, target.generation()),
                    "A link of the current generation was refused");
            CompoundTag saved = target.saveWithoutMetadata();
            target.load(saved);
            helper.assertTrue(target.generation() == 13 && !target.quarantined(),
                    "The generation did not survive a save: " + target.describe());
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /**
     * Review C11R-M2: {@code /arce endgame device owner} moves a registered marker's endpoint record to the new owner
     * with the block entity, and the marker drops the old owner's link.
     */
    @GameTest(template = "empty", batch = "endgame_laser_target_owner", timeoutTicks = 300)
    public static void anOwnerChangeMovesTheEndpointRecordAndDropsTheLink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        UUID oldOwner = UUID.randomUUID();
        helper.assertTrue(target.assignOwner(oldOwner), "The fixture owner was not assigned");
        UUID id = target.deviceId().orElseThrow();
        ServerPlayer newOwner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "markerNewOwner", level,
                pos.east(3), new ArrayList<>());
        helper.startSequence()
                .thenWaitUntil(() -> {
                    chunkSaved(level, pos);
                    helper.assertTrue(target.endpointActive(), "The marker did not register: " + target.describe());
                })
                .thenExecute(() -> {
                    target.adopt(UUID.randomUUID(), UUID.randomUUID());
                    int result;
                    try {
                        result = server.getCommands().getDispatcher().execute("arce endgame device owner "
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " @a[name=markerNewOwner]",
                                server.createCommandSourceStack().withSuppressedOutput());
                    } catch (CommandSyntaxException exception) {
                        result = -1;
                    }
                    EndpointRecord record = root().endpoint(id).orElseThrow();
                    boolean blockEntityMoved = target.ownerId().filter(newOwner.getUUID()::equals).isPresent();
                    boolean recordMoved = record.owner().equals(newOwner.getUUID());
                    boolean linkDropped = target.linkedController().isEmpty() && target.generation() == 1;
                    server.getPlayerList().remove(newOwner);
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(result == 1 && blockEntityMoved && recordMoved && linkDropped,
                            "result=" + result + " block_entity=" + blockEntityMoved + " record=" + recordMoved
                                    + " link_dropped=" + linkDropped);
                })
                .thenSucceed();
    }

    /**
     * Review C11R-M3: a refused {@code endpoint forget} (unknown ID, another player's record) neither writes the root
     * nor adds an audit line; the owner's forget of their MISSING record is a coalesced change with one line.
     */
    @GameTest(template = "empty", batch = "endgame_endpoint_forget", timeoutTicks = 100)
    public static void aRefusedForgetWritesNothingAndTheOwnersForgetIsCoalesced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        EndgameService service = EndgameRuntime.operational().orElseThrow();
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(service.coalesced(root -> root.register(id, LaserTargetBlockEntity.KIND, owner,
                level.dimension().location(), pos.asLong(), false, 4096, 256)) == EndgameCode.OK
                && service.coalesced(root -> root.markMissing(id)), "The MISSING fixture record was not created");
        String zone = "gt_forget_" + id.toString().substring(0, 8);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(service.coalesced(root -> root.addZone(ProtectedZone.of(zone, level.dimension().location(),
                corner.getX(), corner.getZ(), corner.getX() + 1, corner.getZ() + 1, List.of()), 256))
                == EndgameCode.OK && service.writePending(), "No change is pending");
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            ServerPlayer stranger = ConnectedTestPlayers.join(server, UUID.randomUUID(), "forgetStranger", level,
                    corner, new ArrayList<>());
            joined.add(stranger);
            long linesBefore = forgetLines(service);
            int unknown = command(server, stranger, "arce endgame endpoint forget " + UUID.randomUUID());
            int foreign = command(server, stranger, "arce endgame endpoint forget " + id);
            helper.assertTrue(unknown == 0 && foreign == 0, "A refused forget succeeded");
            helper.assertTrue(service.writePending() && forgetLines(service) == linesBefore
                            && service.root().orElseThrow().endpoint(id).isPresent(),
                    "A refused forget wrote the root, changed it or added an audit line");
            ServerPlayer recordOwner = ConnectedTestPlayers.join(server, owner, "forgetOwner", level, corner,
                    new ArrayList<>());
            joined.add(recordOwner);
            // Review C11R-I8: an /execute as source does not act for the owner.
            try {
                server.getCommands().getDispatcher().execute("execute as " + owner + " run arce endgame endpoint forget "
                        + id, server.createCommandSourceStack().withSuppressedOutput());
            } catch (CommandSyntaxException ignored) {
                // A refusal is the expected outcome either way.
            }
            helper.assertTrue(service.root().orElseThrow().endpoint(id).isPresent(),
                    "An /execute as source forgot the owner's record");
            helper.assertTrue(command(server, recordOwner, "arce endgame endpoint forget " + id) == 1
                            && service.root().orElseThrow().endpoint(id).isEmpty()
                            && forgetLines(service) == linesBefore + 1 && service.writePending(),
                    "The owner's forget was not a coalesced change with one audit line");
        } finally {
            joined.forEach(player -> server.getPlayerList().remove(player));
            service.barrier(root -> root.removeZone(zone));
        }
        helper.succeed();
    }

    private static long forgetLines(EndgameService service) {
        long total = 0;
        for (int page = 0; page < 32; page++) {
            total += service.audit().page("endgame", page).stream().filter(line -> line.contains("endpoint_forget"))
                    .count();
        }
        return total;
    }

    private static int command(MinecraftServer server, ServerPlayer player, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        } catch (CommandSyntaxException exception) {
            return -1;
        }
    }

    /**
     * Review C11R-L5: a marker waiting for registration keeps its chunk dirty, so the next save (an autosave or the
     * unload save) registers it. One status check runs here in the test's own tick, because vanilla saves dirty
     * chunks eagerly between ticks.
     */
    @GameTest(template = "empty", batch = "endgame_laser_target_dirty", timeoutTicks = 200)
    public static void anAwaitingMarkerKeepsItsChunkDirty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(target.assignOwner(UUID.randomUUID()), "The fixture owner was not assigned");
        UUID id = target.deviceId().orElseThrow();
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE
                            && root().endpoint(id).isEmpty(), "Not awaiting a save: " + target.describe());
                    LevelChunk chunk = level.getChunkAt(pos);
                    target.onLoad(); // The next status check is due now.
                    chunk.setUnsaved(false);
                    LaserTargetBlockEntity.serverTick(level, pos, level.getBlockState(pos), target);
                    helper.assertTrue(chunk.isUnsaved(), "An awaiting marker left its chunk clean");
                })
                .thenWaitUntil(() -> {
                    chunkSaved(level, pos);
                    helper.assertTrue(target.endpointActive(), "Not registered: " + target.describe());
                })
                .thenExecute(() -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()))
                .thenSucceed();
    }

    /** Review C11R-L8: {@code zone list} pages through every zone, 16 per page. */
    @GameTest(template = "empty", batch = "endgame_zone_pages", timeoutTicks = 40)
    public static void zoneListPagesReachEveryZone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        EndgameService service = EndgameRuntime.operational().orElseThrow();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String name = "gt_page_" + (char) ('a' + i);
            names.add(name);
            helper.assertTrue(service.coalesced(root -> root.addZone(ProtectedZone.of(name,
                    level.dimension().location(), corner.getX(), corner.getZ(), corner.getX() + 1, corner.getZ() + 1,
                    List.of()), 256)) == EndgameCode.OK, "A fixture zone was not added");
        }
        try {
            List<String> lines = new ArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                    lines.add(message.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            int zones = service.root().orElseThrow().zones().size();
            int lastPage = (zones - 1) / 16;
            List<String> listed = new ArrayList<>();
            for (int page = 0; page <= lastPage; page++) {
                lines.clear();
                server.getCommands().getDispatcher().execute("arce endgame zone list " + page,
                        server.createCommandSourceStack().withSource(capture));
                helper.assertTrue(lines.size() == 1 + Math.min(16, zones - page * 16),
                        "Page " + page + " showed " + (lines.size() - 1) + " zones");
                listed.addAll(lines.subList(1, lines.size()));
            }
            helper.assertTrue(names.stream().allMatch(name -> listed.stream().anyMatch(line -> line.startsWith(
                    name + " "))), "Not every zone was listed: " + listed);
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException(exception);
        } finally {
            names.forEach(name -> service.coalesced(root -> root.removeZone(name)));
        }
        helper.succeed();
    }

    /**
     * Review C11R2-L1: {@code device owner} on a marker that is not registered yet; a save right after the command
     * registers it for the new owner.
     */
    @GameTest(template = "empty", batch = "endgame_laser_target_owner_waiting", timeoutTicks = 200)
    public static void anOwnerChangeBeforeRegistrationRegistersTheNewOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(target.assignOwner(UUID.randomUUID()), "The fixture owner was not assigned");
        UUID id = target.deviceId().orElseThrow();
        ServerPlayer newOwner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "waitingNewOwner", level,
                pos.east(3), new ArrayList<>());
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(root().endpoint(id).isEmpty(), "Registered before the owner change");
                    int result;
                    try {
                        result = server.getCommands().getDispatcher().execute("arce endgame device owner "
                                + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " @a[name=waitingNewOwner]",
                                server.createCommandSourceStack().withSuppressedOutput());
                    } catch (CommandSyntaxException exception) {
                        result = -1;
                    }
                    helper.assertTrue(result == 1, "The owner command failed: " + result);
                    // The save comes before the marker's next status check.
                    chunkSaved(level, pos);
                })
                .thenWaitUntil(() -> helper.assertTrue(root().endpoint(id).isPresent(), "Not registered"))
                .thenExecute(() -> {
                    boolean newOwnerRecord = root().endpoint(id).orElseThrow().owner().equals(newOwner.getUUID());
                    server.getPlayerList().remove(newOwner);
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(newOwnerRecord, "The record registered for the old owner");
                })
                .thenSucceed();
    }

    /**
     * Test fixture: the event a chunk save posts, with the tag it would write (vanilla throttles real saves of one
     * chunk to one per 10 s of wall time, which a GameTest outruns).
     */
    static void chunkSaved(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, ChunkSerializer.write(level, chunk)));
    }

    private static EndgameRoot root() {
        return EndgameRuntime.operational().orElseThrow().root().orElseThrow();
    }
}
