package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndpointObservations;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-054 sections 9 and 9.1 for the laser target: registration on persistence, removal, retirement and freezing. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserTargetGameTests {
    private static RegistrationFixture registrationFixture;
    private static WaitingOwnerFixture waitingOwnerFixture;

    private LaserTargetGameTests() {
    }

    @GameTest(template = "empty", batch = "endgame_laser_target", timeoutTicks = 300)
    public static void aTargetRegistersOnceSavedAndARemovedIdComesBackFrozen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(registrationFixture == null, "A previous registration fixture was not closed");
        helper.assertTrue(level.getBlockState(pos).isAir(), "The registration fixture position is not empty");
        RegistrationFixture fixture = new RegistrationFixture(level, pos);
        registrationFixture = fixture;
        try {
            fixture.install();
            BlockState state = ModBlocks.LASER_TARGET.get().defaultBlockState();
            helper.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE)
                            && state.getPistonPushReaction() == PushReaction.BLOCK
                            && state.getBlock().getExplosionResistance() >= 1200.0F,
                    "The target lacks the endpoint protections");
            try {
                level.setBlockAndUpdate(pos, state);
            } finally {
                fixture.captureTarget();
            }
            LaserTargetBlockEntity target = fixture.target;
            UUID owner = UUID.randomUUID();
            helper.assertTrue(target.assignOwner(owner), "The fixture owner was not assigned");
            UUID id = target.deviceId().orElseThrow();
            fixture.watch(id, owner);
            // A same-call negative control: native saves may happen after this call yields.
            LaserTargetBlockEntity.serverTick(level, pos, state, target);
            helper.assertTrue(target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE,
                    "Not awaiting a save: " + target.describe());
            helper.assertTrue(root().endpoint(id).isEmpty() && !target.endpointActive() && !fixture.saved,
                    "Registered before a save observation in the placement call");
            CompoundTag[] copy = new CompoundTag[1];
            helper.startSequence()
                    .thenExecuteAfter(25, () -> {
                        var record = root().endpoint(id);
                        if (record.isPresent()) {
                            fixture.assertRegistration(helper, record.orElseThrow());
                        } else {
                            helper.assertTrue(target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE
                                            && !target.endpointActive(), "Unregistered target is not awaiting a save");
                        }
                    })
                    .thenWaitUntil(() -> {
                        chunkSaved(level, pos);
                        helper.assertTrue(target.endpointStatus() == EndgameCode.OK && target.endpointActive(),
                                "Not registered after the save: " + target.describe());
                        fixture.assertRegistration(helper, root().endpoint(id).orElseThrow());
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
                        fixture.removeWithDrops();
                        helper.assertTrue(root().endpoint(id).isEmpty() && root().retired(id),
                                "A removed target was not retired");
                        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D)).stream()
                                .mapToInt(entity -> entity.getItem().is(Items.COBBLESTONE) ? entity.getItem().getCount() : 0)
                                .sum();
                        helper.assertTrue(dropped == 5, "The buffer did not drop: " + dropped);
                        // A copy of the removed target comes back (a crash or a block mover): it is retired and frozen.
                        try {
                            level.setBlockAndUpdate(pos, state);
                        } finally {
                            fixture.captureTarget();
                        }
                        fixture.target.load(copy[0]);
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
                        fixture.close();
                    })
                    .thenSucceed();
        } catch (RuntimeException | Error failure) {
            try {
                fixture.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** The one-test batch closes its observer even after an assertion or timeout fails. */
    @AfterBatch(batch = "endgame_laser_target")
    public static void closeRegistrationFixture(ServerLevel level) {
        RegistrationFixture fixture = registrationFixture;
        if (fixture != null && fixture.level == level) {
            fixture.close();
        }
    }

    /** Test-only, one owned target; no saved tags, growing event history or shared-root reset. */
    private static final class RegistrationFixture {
        private static final int MAX_CHUNK_BLOCK_ENTITIES = 1024;
        private static final int MAX_NEARBY_ITEMS = 16;
        private final ServerLevel level;
        private final BlockPos pos;
        private final long chunk;
        private final Set<String> types = Set.of(ModBlockEntities.LASER_TARGET.getId().toString());
        private final Consumer<ChunkDataEvent.Save> saveListener = this::onSave;
        private final Consumer<ServerStoppingEvent> stoppingListener = this::onStopping;
        private LaserTargetBlockEntity target;
        private UUID id;
        private UUID owner;
        private Tag savedId;
        private Tag savedOwner;
        private List<ItemEntity> drops = List.of();
        private boolean saved;
        private long firstSavedTick = -1L;
        private boolean closed;

        private RegistrationFixture(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
            this.chunk = new ChunkPos(pos).toLong();
        }

        private void install() {
            MinecraftForge.EVENT_BUS.addListener(saveListener);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, stoppingListener);
        }

        private void captureTarget() {
            if (level.getBlockEntity(pos) instanceof LaserTargetBlockEntity placed) {
                target = placed;
            }
        }

        private void watch(UUID id, UUID owner) {
            this.id = id;
            this.owner = owner;
            savedId = NbtUtils.createUUID(id);
            savedOwner = NbtUtils.createUUID(owner);
        }

        private void onSave(ChunkDataEvent.Save event) {
            if (closed || saved || id == null || event.getLevel() != level
                    || event.getChunk().getPos().toLong() != chunk) {
                return;
            }
            if (!(event.getData().get("block_entities") instanceof ListTag list)
                    || list.getElementType() != Tag.TAG_COMPOUND || list.size() > MAX_CHUNK_BLOCK_ENTITIES) {
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.contains("id", Tag.TAG_STRING) || !types.contains(entry.getString("id"))
                        || !entry.contains("x", Tag.TAG_INT) || entry.getInt("x") != pos.getX()
                        || !entry.contains("y", Tag.TAG_INT) || entry.getInt("y") != pos.getY()
                        || !entry.contains("z", Tag.TAG_INT) || entry.getInt("z") != pos.getZ()
                        || !(entry.get(EndgameDeviceTags.ROOT) instanceof CompoundTag root)
                        || !savedId.equals(root.get(EndgameDeviceTags.DEVICE_ID))
                        || !savedOwner.equals(root.get(EndgameDeviceTags.OWNER_ID))
                        || !root.contains(EndgameDeviceTags.FROZEN, Tag.TAG_BYTE)
                        || root.getByte(EndgameDeviceTags.FROZEN) != 0) {
                    continue;
                }
                if (EndpointObservations.persisted(event.getData(), types, id, pos.asLong())
                        .filter(frozen -> !frozen).isPresent()) {
                    saved = true;
                    firstSavedTick = level.getGameTime();
                }
                return;
            }
        }

        private void assertRegistration(GameTestHelper helper, EndpointRecord record) {
            helper.assertTrue(saved && firstSavedTick >= 0L && firstSavedTick <= level.getGameTime(),
                    "Registered without the exact target save observation");
            helper.assertTrue(record.id().equals(id) && record.kind().equals(LaserTargetBlockEntity.KIND)
                            && record.owner().equals(owner) && record.level().equals(level.dimension().location())
                            && record.pos() == pos.asLong() && record.state() == EndpointRecord.State.ACTIVE,
                    "The registered record does not match the observed target");
        }

        private List<ItemEntity> nearbyItems() {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D));
            if (items.size() > MAX_NEARBY_ITEMS) {
                throw new IllegalStateException("The target fixture has too many nearby item entities");
            }
            return items;
        }

        private void removeWithDrops() {
            List<ItemEntity> before = nearbyItems();
            try {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            } finally {
                // No tick interleaves the owned target's native removal and this identity delta.
                drops = nearbyItems().stream().filter(item -> !before.contains(item)
                        && item.getItem().is(Items.COBBLESTONE)).toList();
            }
        }

        private void onStopping(ServerStoppingEvent event) {
            if (event.getServer() == level.getServer()) {
                close();
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                try {
                    if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                            && target != null && level.getBlockEntity(pos) == target) {
                        for (int slot = 0; slot < target.buffer().getSlots(); slot++) {
                            target.buffer().setStackInSlot(slot, ItemStack.EMPTY);
                        }
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                } finally {
                    drops.forEach(ItemEntity::discard);
                    drops = List.of();
                }
            } finally {
                try {
                    try {
                        MinecraftForge.EVENT_BUS.unregister(saveListener);
                    } finally {
                        MinecraftForge.EVENT_BUS.unregister(stoppingListener);
                    }
                } finally {
                    if (registrationFixture == this) {
                        registrationFixture = null;
                    }
                }
            }
        }
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
        // Vanilla saves dirty chunks on its own between ticks, so the dirty-chunk check runs here, in the placement
        // tick, and every later save of the chunk is recorded.
        LevelChunk chunk = level.getChunkAt(pos);
        target.onLoad(); // The first status check is due now.
        chunk.setUnsaved(false);
        LaserTargetBlockEntity.serverTick(level, pos, level.getBlockState(pos), target);
        helper.assertTrue(target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE && root().endpoint(id).isEmpty(),
                "Not awaiting a save: " + target.describe());
        helper.assertTrue(chunk.isUnsaved(), "An awaiting marker left its chunk clean");
        ChunkSaveWatcher saves = ChunkSaveWatcher.start(level, pos);
        helper.startSequence()
                .thenExecuteAfter(25, () -> helper.assertTrue(saves.any(pos)
                                || target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE && root().endpoint(id).isEmpty(),
                        "Registered without any save: " + target.describe()))
                .thenWaitUntil(() -> {
                    chunkSaved(level, pos);
                    helper.assertTrue(target.endpointActive(), "Not registered: " + target.describe());
                })
                .thenExecute(() -> {
                    saves.stop();
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                })
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
        helper.assertTrue(waitingOwnerFixture == null, "A previous waiting-owner fixture was not closed");
        helper.assertTrue(level.getBlockState(pos).isAir(), "The waiting-owner fixture position is not empty");
        WaitingOwnerFixture fixture = new WaitingOwnerFixture(level, pos);
        waitingOwnerFixture = fixture;
        fixture.run(() -> {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, fixture.stoppingListener);
            try {
                level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
            } finally {
                if (level.getBlockEntity(pos) instanceof LaserTargetBlockEntity target) {
                    fixture.target = target;
                }
            }
            helper.assertTrue(fixture.target != null, "No native target was placed: " + fixture.describe());
            LaserTargetBlockEntity target = fixture.target;
            helper.assertTrue(target.assignOwner(fixture.originalOwner), "Owner assignment failed: " + fixture.describe());
            fixture.id = target.deviceId().orElseThrow();
            helper.assertTrue(server.getPlayerList().getPlayer(fixture.newOwner) == null,
                    "The fixture player UUID is already connected: " + fixture.describe());
            try {
                fixture.player = ConnectedTestPlayers.join(server, fixture.newOwner, "waitingNewOwner", level,
                        pos.east(3), new ArrayList<>());
            } finally {
                fixture.player = server.getPlayerList().getPlayer(fixture.newOwner);
            }
            // Establish the waiting candidate and change its owner before this initiating call yields to native saves.
            LaserTargetBlockEntity.serverTick(level, pos, level.getBlockState(pos), target);
            fixture.unregisteredBeforeCommand = root().endpoint(fixture.id).isEmpty();
            helper.assertTrue(fixture.unregisteredBeforeCommand && !target.endpointActive()
                            && target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE
                            && target.ownerId().filter(fixture.originalOwner::equals).isPresent()
                            && level.getBlockEntity(pos) == target
                            && server.getPlayerList().getPlayer(fixture.newOwner) == fixture.player,
                    "The unregistered command prerequisite failed: " + fixture.describe());
            fixture.commandTick = level.getGameTime();
            fixture.commandAttempted = true;
            try {
                fixture.result = server.getCommands().getDispatcher().execute("arce endgame device owner "
                        + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " @a[name=waitingNewOwner]",
                        server.createCommandSourceStack().withLevel(level).withSource(fixture.capture));
            } catch (CommandSyntaxException exception) {
                fixture.result = -1;
                fixture.reply(exception.getMessage());
            }
            fixture.liveOwnerAfterCommand = target.ownerId().orElse(null);
            fixture.rootOwnerAfterCommand = root().endpoint(fixture.id).map(EndpointRecord::owner).orElse(null);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_OWNER_CHANGE_FIXTURE command {}", fixture.describe());
            helper.assertTrue(fixture.result == 1 && fixture.newOwner.equals(fixture.liveOwnerAfterCommand),
                    "The owner command did not assign the intended live owner: " + fixture.describe());
            fixture.injectedSaveTick = level.getGameTime();
            chunkSaved(level, pos); // Injected save event only; no native writer, disk or restart claim.
            helper.onEachTick(() -> fixture.run(() -> {
                var record = root().endpoint(fixture.id);
                if (record.isEmpty()) {
                    return;
                }
                EndpointRecord registered = record.orElseThrow();
                helper.assertTrue(registered.owner().equals(fixture.newOwner),
                        "The record did not register for the intended new owner: " + fixture.describe());
                helper.assertTrue(registered.id().equals(fixture.id) && registered.kind().equals(LaserTargetBlockEntity.KIND)
                                && registered.level().equals(level.dimension().location()) && registered.pos() == pos.asLong()
                                && registered.state() == EndpointRecord.State.ACTIVE && level.getBlockEntity(pos) == target
                                && target.deviceId().filter(fixture.id::equals).isPresent()
                                && target.ownerId().filter(fixture.newOwner::equals).isPresent(),
                        "The registered target identity changed: " + fixture.describe());
                AdvancedRocketryCommunity.LOGGER.info("ARCE_OWNER_CHANGE_FIXTURE registration {}", fixture.describe());
                fixture.close();
                helper.succeed();
            }));
        });
    }

    @AfterBatch(batch = "endgame_laser_target_owner_waiting")
    public static void closeWaitingOwnerFixture(ServerLevel level) {
        WaitingOwnerFixture fixture = waitingOwnerFixture;
        if (fixture != null && fixture.level == level) {
            try {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_OWNER_CHANGE_FIXTURE batch_cleanup {}", fixture.describe());
            } finally {
                fixture.close();
            }
        }
    }

    /** One owned player/target; bounded command receipts, no save observer or shared-root cleanup. */
    private static final class WaitingOwnerFixture {
        private final ServerLevel level;
        private final BlockPos pos;
        private final UUID originalOwner = UUID.randomUUID();
        private final UUID newOwner = UUID.randomUUID();
        private final long placementTick;
        private final List<String> replies = new ArrayList<>();
        private final CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { reply(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        private final Consumer<ServerStoppingEvent> stoppingListener = this::onStopping;
        private LaserTargetBlockEntity target;
        private ServerPlayer player;
        private UUID id;
        private UUID liveOwnerAfterCommand;
        private UUID rootOwnerAfterCommand;
        private long commandTick = -1L;
        private long injectedSaveTick = -1L;
        private int result = -1;
        private int replyCount;
        private boolean unregisteredBeforeCommand;
        private boolean commandAttempted;
        private boolean closed;
        private Throwable firstFailure;

        private WaitingOwnerFixture(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
            placementTick = level.getGameTime();
        }

        private void reply(String message) {
            replyCount++;
            if (replies.size() < 4) {
                String text = String.valueOf(message).replace('\n', ' ').replace('\r', ' ');
                replies.add(text.substring(0, Math.min(text.length(), 256)));
            }
        }

        private String describe() {
            var currentRoot = EndgameRuntime.operational().flatMap(EndgameService::root);
            var record = currentRoot.flatMap(root -> id == null ? java.util.Optional.<EndpointRecord>empty() : root.endpoint(id));
            return "level=" + level.dimension().location() + " pos=" + pos.toShortString() + " id=" + id
                    + " target_identity=" + System.identityHashCode(target) + " original_owner=" + originalOwner
                    + " new_owner=" + newOwner + " placement_tick=" + placementTick + " tick=" + level.getGameTime()
                    + " unregistered_before_command=" + unregisteredBeforeCommand + " command_attempted=" + commandAttempted
                    + " command_tick=" + commandTick + " result=" + result + " reply_count=" + replyCount + " replies=" + replies
                    + " live_after_command=" + liveOwnerAfterCommand + " root_after_command=" + rootOwnerAfterCommand
                    + " live_owner=" + (target == null ? null : target.ownerId().orElse(null))
                    + " root_available=" + currentRoot.isPresent() + " root_owner=" + record.map(EndpointRecord::owner).orElse(null)
                    + " injected_save_tick=" + injectedSaveTick;
        }

        private void run(Runnable action) {
            if (closed) { return; }
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                firstFailure = failure;
                try {
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_OWNER_CHANGE_FIXTURE failure {}", describe());
                } catch (RuntimeException | Error diagnosticFailure) {
                    firstFailure.addSuppressed(diagnosticFailure);
                }
                try { close(); } catch (RuntimeException | Error cleanupFailure) { firstFailure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        private void close() {
            if (closed) { return; }
            closed = true; // Later scheduled callbacks cannot replace a failed prerequisite or succeed after it.
            try {
                if (player != null && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                    level.getServer().getPlayerList().remove(player);
                }
            } finally {
                try {
                    if (target != null && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                            && level.getBlockEntity(pos) == target) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                } finally {
                    try {
                        MinecraftForge.EVENT_BUS.unregister(stoppingListener);
                    } finally {
                        if (waitingOwnerFixture == this) { waitingOwnerFixture = null; }
                    }
                }
            }
        }

        private void onStopping(ServerStoppingEvent event) {
            if (event.getServer() == level.getServer()) { close(); }
        }
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
