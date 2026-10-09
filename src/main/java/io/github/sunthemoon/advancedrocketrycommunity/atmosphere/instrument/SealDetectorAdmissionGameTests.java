package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryCatalog;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Internal guard fixtures; local handles and accessor callbacks are not installed lifecycle/client proof. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealDetectorAdmissionGameTests {
    private SealDetectorAdmissionGameTests() { }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsTwoHeldItemsBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.actor.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.item, 2));
            f.reject(f.probe(f.actor.player, f.level, false), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsCapturedStackAfterActualHandReplacement(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            Probe context = f.probe(f.actor.player, f.level, false);
            f.actor.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.item));
            helper.assertTrue(context.getItemInHand() != f.actor.player.getMainHandItem(), "Context did not capture the old identity");
            f.reject(context, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsSpectatorBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.actor.player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
            helper.assertTrue(f.actor.player.isSpectator(), "Spectator setup failed");
            f.reject(f.probe(f.actor.player, f.level, false), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsDeadActorBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.actor.player.setHealth(0);
            helper.assertTrue(!f.actor.player.isAlive(), "Dead actor setup failed");
            f.reject(f.probe(f.actor.player, f.level, false), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsRemovedActorBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            f.actor.player.discard();
            helper.assertTrue(f.actor.player.isRemoved(), "Removed actor setup failed");
            f.reject(f.probe(f.actor.player, f.level, false), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsMissingConnectionBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            var connection = f.actor.player.connection;
            try {
                f.actor.player.connection = null;
                f.reject(f.probe(f.actor.player, f.level, false), false);
            } finally { f.actor.player.connection = connection; }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsForeignLevelBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            ServerLevel other = f.level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(other != null && other != f.level, "Foreign level fixture is unavailable");
            f.reject(f.probe(f.actor.player, other, false), false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsStalePlayerObjectWithCurrentUuid(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) {
            List<Reply> staleReplies = new ArrayList<>();
            ServerPlayer stale = player(f.level, f.actor.player.getGameProfile(), staleReplies);
            stale.connection = f.actor.player.connection; // Borrowed identity input, never an owned connection.
            stale.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.item));
            helper.assertTrue(stale.isAlive() && !stale.isRemoved() && !stale.hasDisconnected() && !stale.isSpectator()
                    && stale.getServer() == f.level.getServer() && stale.serverLevel() == f.level
                    && f.level.getServer().getPlayerList().getPlayer(stale.getUUID()) == f.actor.player,
                    "Stale object must differ from the live PlayerList object, not basic actor state");
            try {
                f.reject(f.probe(stale, f.level, false), false);
                helper.assertTrue(staleReplies.isEmpty(), "Stale object received feedback");
            } finally { stale.connection = null; }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void itemRejectsOffThreadBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper)) { f.reject(f.probe(f.actor.player, f.level, false), true); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void closedLocalHandleRejectsBeforeAllContextAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); LocalReader local = new LocalReader(f)) {
            local.control(f);
            helper.assertTrue(local.service.owns(f.level.getServer()), "Live local handle lost its owner");
            local.service.close();
            local.service.close();
            Probe context = f.probe(f.actor.player, f.level, true);
            helper.assertTrue(!local.service.owns(f.level.getServer())
                    && local.service.read(context).equals(SealDetectorReading.unavailable())
                    && local.service.read(null).equals(SealDetectorReading.unavailable()), "Closed handle responded or rebound");
            context.untouched(helper, true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void localHandleRejectsOffThreadBeforeAllContextAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); LocalReader local = new LocalReader(f)) {
            local.control(f);
            Probe context = f.probe(f.actor.player, f.level, true);
            helper.assertTrue(offThread(f.workers, () -> local.service.read(context)).equals(SealDetectorReading.unavailable()),
                    "Off-thread local reader responded");
            context.untouched(helper, true);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void postQueryActualHandChangeCannotPublishPositiveLocalReading(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); LocalReader control = new LocalReader(f); LocalReader local = new LocalReader(f)) {
            control.control(f);
            helper.assertTrue(local.manager.metrics(f.level.dimension()).isEmpty(), "Local query precondition is not fresh");
            boolean[] changed = {false};
            UseOnContext context = new UseOnContext(f.actor.player, InteractionHand.MAIN_HAND, f.hit()) {
                @Override public Player getPlayer() {
                    if (!changed[0] && local.manager.metrics(f.level.dimension()).isPresent()) {
                        changed[0] = true; // Controlled accessor callback after the local supply query, not a client/provider event.
                        f.actor.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.item));
                    }
                    return super.getPlayer();
                }
            };
            ItemStack captured = context.getItemInHand();
            helper.assertTrue(local.service.read(context).equals(SealDetectorReading.unavailable())
                    && changed[0] && local.manager.metrics(f.level.dimension()).isPresent()
                    && captured != f.actor.player.getMainHandItem(), "Post-query stale held identity published a positive reading");
            helper.assertTrue(f.actor.replies.isEmpty() && f.peer.replies.isEmpty()
                    && !f.actor.player.getCooldowns().isOnCooldown(f.item), "Direct local read sent feedback or changed cooldown");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void localHandleRejectsForeignLevelBeforeSelectedCellAccess(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); LocalReader control = new LocalReader(f); LocalReader local = new LocalReader(f)) {
            control.control(f);
            ServerLevel other = f.level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(other != null && other != f.level, "Foreign actual Level is unavailable");
            Probe context = f.probe(f.actor.player, other, false);
            helper.assertTrue(local.service.owns(f.level.getServer())
                    && local.service.read(context).equals(SealDetectorReading.unavailable()), "Foreign Level local read responded");
            context.untouched(helper, false);
            helper.assertTrue(local.manager.metrics(f.level.dimension()).isEmpty()
                    && local.manager.metrics(other.dimension()).isEmpty(), "Foreign Level refusal created a local atmosphere service");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void nearbyHitCannotAuthorizeAnUnloadedFarSelectedCell(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); LocalReader control = new LocalReader(f); LocalReader local = new LocalReader(f)) {
            control.control(f);
            BlockPos far = new BlockPos(25_000_000, f.target.getY(), 25_000_000);
            helper.assertTrue(f.level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                    "Far selected chunk was already loaded");
            int loaded = f.level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(f.level.getForcedChunks().size() <= 4096, "Forced-mark snapshot exceeds the fixture bound");
            var forced = java.util.Set.copyOf(f.level.getForcedChunks());
            Vec3 eye = f.actor.player.getEyePosition();
            UseOnContext context = new UseOnContext(f.actor.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(eye, Direction.UP, far, false));
            helper.assertTrue(eye.distanceToSqr(context.getClickLocation()) == 0D
                    && SealDetectorService.distanceToCellSquared(eye, far) > 36D, "Inconsistent near-hit fixture differs");
            helper.assertTrue(local.service.read(context).equals(SealDetectorReading.unavailable())
                    && local.manager.metrics(f.level.dimension()).isEmpty(), "Far cell was authorized by a near hit");
            helper.assertTrue(f.level.getChunkSource().getLoadedChunksCount() == loaded
                    && f.level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null
                    && forced.equals(f.level.getForcedChunks()), "Refusal loaded the selected chunk or changed forced marks");
            // A loaded counterexample prevents later chunk refusal from masking the reach guard.
            f.actor.player.teleportTo(f.level, f.target.getX() - 9.5D, f.target.getY(), f.target.getZ() + 0.5D, 0, 0);
            Vec3 distantEye = f.actor.player.getEyePosition();
            UseOnContext loadedTarget = new UseOnContext(f.actor.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(distantEye, Direction.WEST, f.target, false));
            helper.assertTrue(SealDetectorService.validEye(distantEye, f.level.getMinBuildHeight(), f.level.getMaxBuildHeight())
                    && distantEye.distanceToSqr(loadedTarget.getClickLocation()) == 0D
                    && SealDetectorService.distanceToCellSquared(distantEye, f.target) > 36D
                    && f.level.getChunkSource().getChunkNow(f.target.getX() >> 4, f.target.getZ() >> 4) != null
                    && f.level.getChunkSource().getChunkNow(f.target.west().getX() >> 4, f.target.west().getZ() >> 4) != null
                    && f.level.mayInteract(f.actor.player, f.target), "Loaded target must isolate the target-AABB reach guard");
            helper.assertTrue(local.service.read(loadedTarget).equals(SealDetectorReading.unavailable())
                    && local.manager.metrics(f.level.dimension()).isEmpty(), "Loaded far target was authorized by a near hit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "seal_detector_admission", timeoutTicks = 20)
    public static void outOfBuildTargetRefusesBeforeTheLegacyOpenFallback(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper); LocalReader control = new LocalReader(f); LocalReader local = new LocalReader(f)) {
            control.control(f);
            BlockPos target = new BlockPos(f.target.getX(), f.level.getMaxBuildHeight(), f.target.getZ());
            // Setup moves an owned connected player within the already-loaded fixture chunk.
            f.actor.player.teleportTo(f.level, target.getX() - 1.5D, target.getY() - 2D, target.getZ() + 0.5D, 0, 0);
            UseOnContext context = new UseOnContext(f.actor.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target).add(-0.5D, 0, 0), Direction.WEST, target, false));
            helper.assertTrue(SealDetectorService.validEye(f.actor.player.getEyePosition(), f.level.getMinBuildHeight(), f.level.getMaxBuildHeight())
                    && SealDetectorService.inReach(f.actor.player.getEyePosition(), context.getClickLocation(), target)
                    && f.level.isOutsideBuildHeight(target)
                    && f.level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) != null,
                    "Height refusal must have a valid eye/reach and loaded horizontal chunk");
            helper.assertTrue(local.service.read(context).equals(SealDetectorReading.unavailable())
                    && local.manager.metrics(f.level.dimension()).isEmpty(), "Invalid height became a measured OPEN boundary");
        }
        helper.succeed();
    }

    private static final class LocalReader implements AutoCloseable {
        private final AtmosphereManager manager = new AtmosphereManager(new CelestialEnvironmentService(new CelestialCatalogManager()));
        private final SealDetectorService service;
        private final WorkerGate workers;

        private LocalReader(Fixture f) {
            workers = f.workers;
            service = new SealDetectorService(f.level.getServer(), manager, AtmosphereBoundaryCatalog.empty());
        }
        private void control(Fixture f) {
            f.helper.assertTrue(service.read(new UseOnContext(f.actor.player, InteractionHand.MAIN_HAND, f.hit()))
                    .equals(SealDetectorReading.measured(SealDetectorReading.Boundary.SEALED,
                            SealDetectorReading.Supply.NOT_KNOWN_SUPPLIED)), "Admitted local control did not measure");
        }
        @Override public void close() {
            workers.requireStopped();
            try { service.close(); } finally { manager.clear(); }
        }
    }

    /** A refusal must stop before selected position/face/hit access; local handle guards also stop actor/Level access. */
    private static final class Probe extends UseOnContext {
        private final boolean forbidActor;
        private int actorReads;
        private int levelReads;
        private int selectedReads;

        private Probe(ServerPlayer player, ServerLevel level, BlockHitResult hit, boolean forbidActor) {
            super(level, player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
            this.forbidActor = forbidActor;
        }
        @Override public Player getPlayer() {
            actorReads++;
            if (forbidActor) { throw new AssertionError("Refused local handle accessed its actor"); }
            return super.getPlayer();
        }
        @Override public Level getLevel() {
            levelReads++;
            if (forbidActor) { throw new AssertionError("Refused local handle accessed its Level"); }
            return super.getLevel();
        }
        private AssertionError selected() { selectedReads++; return new AssertionError("Refused request accessed its selected cell"); }
        @Override public BlockPos getClickedPos() { throw selected(); }
        @Override public Direction getClickedFace() { throw selected(); }
        @Override public Vec3 getClickLocation() { throw selected(); }
        private void untouched(GameTestHelper helper, boolean allContext) {
            helper.assertTrue(selectedReads == 0 && (!allContext || actorReads == 0 && levelReads == 0),
                    "Refusal did not precede the required context accesses");
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos target;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private final SealDetectorItem item;
        private final WorkerGate workers = new WorkerGate();
        private Actor actor;
        private Actor peer;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos allocation = helper.absolutePos(new BlockPos(2, 2, 2));
            target = new BlockPos(allocation.getX(), 180, allocation.getZ());
            var registered = ForgeRegistries.ITEMS.getValue(new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "seal_detector"));
            helper.assertTrue(registered instanceof SealDetectorItem, "Registered detector is unavailable");
            item = (SealDetectorItem) registered;
            try {
                for (BlockPos cell : new BlockPos[] {target, target.west()}) {
                    helper.assertTrue(level.hasChunkAt(cell) && !level.isOutsideBuildHeight(cell) && level.getBlockEntity(cell) == null,
                            "Fixture requires two loaded BE-free cells");
                    before.put(cell, level.getBlockState(cell));
                }
                level.setBlock(target, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(target.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                actor = new Actor(level, target, "sealAdmission");
                peer = new Actor(level, target, "sealAdmPeer");
                ItemStack main = new ItemStack(item), off = new ItemStack(item);
                main.getOrCreateTag().putString("fixture_note", "guard main");
                off.getOrCreateTag().putLong("fixture_note", Long.MIN_VALUE);
                actor.player.setItemInHand(InteractionHand.MAIN_HAND, main);
                actor.player.setItemInHand(InteractionHand.OFF_HAND, off);
                actor.replies.clear(); peer.replies.clear();
                helper.assertTrue(actor.player.gameMode.useItemOn(actor.player, level, main, InteractionHand.MAIN_HAND, hit()).consumesAction(),
                        "Admitted native control did not respond");
                Component expected = Component.translatable("message.advancedrocketrycommunity.seal_detector.reading",
                        Component.translatable("message.advancedrocketrycommunity.seal_detector.boundary.sealed"),
                        Component.translatable("message.advancedrocketrycommunity.seal_detector.supply.not_known_supplied"));
                helper.assertTrue(actor.replies.equals(List.of(new Reply(Component.Serializer.toJson(expected), false)))
                        && peer.replies.isEmpty() && actor.player.getCooldowns().isOnCooldown(item), "Admitted native control feedback differs");
                actor.player.getCooldowns().removeCooldown(item); // Refusal cannot be masked by the preceding control's cooldown.
                actor.replies.clear(); peer.replies.clear();
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        private BlockHitResult hit() { return new BlockHitResult(Vec3.atCenterOf(target).add(-0.5D, 0, 0), Direction.WEST, target, false); }
        private Probe probe(ServerPlayer subject, ServerLevel contextLevel, boolean forbidActor) { return new Probe(subject, contextLevel, hit(), forbidActor); }
        private void reject(Probe context, boolean worker) {
            ServerPlayer subject = (ServerPlayer) context.getPlayer();
            ItemStack main = subject.getMainHandItem(), off = subject.getOffhandItem();
            CompoundTag mainData = main.save(new CompoundTag()), offData = off.save(new CompoundTag());
            var metrics = AtmosphereRuntime.metrics(level);
            helper.assertTrue(!subject.getCooldowns().isOnCooldown(item), "Refusal precondition is already on cooldown");
            InteractionResult result = worker ? offThread(workers, () -> item.useOn(context)) : item.useOn(context);
            helper.assertTrue(result == InteractionResult.FAIL && actor.replies.isEmpty() && peer.replies.isEmpty()
                    && !subject.getCooldowns().isOnCooldown(item), "Refused item request responded or set cooldown");
            context.untouched(helper, false);
            helper.assertTrue(main == subject.getMainHandItem() && off == subject.getOffhandItem()
                    && mainData.equals(main.save(new CompoundTag())) && offData.equals(off.save(new CompoundTag()))
                    && metrics.equals(AtmosphereRuntime.metrics(level)), "Refusal changed held data or atmosphere metrics");
        }
        @Override public void close() {
            workers.requireStopped();
            Throwable first = null;
            for (Actor owned : new Actor[] {actor, peer}) {
                if (owned == null) { continue; }
                try { owned.close(); } catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            for (var entry : before.entrySet()) {
                try {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
                    helper.assertTrue(level.getBlockState(entry.getKey()) == entry.getValue() && level.getBlockEntity(entry.getKey()) == null,
                            "Owned guard fixture cell was not restored");
                } catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            if (first instanceof RuntimeException failure) { throw failure; }
            if (first instanceof Error failure) { throw failure; }
        }
    }

    private record Reply(String json, boolean overlay) { }
    private static ServerPlayer player(ServerLevel level, GameProfile profile, List<Reply> replies) {
        return new ServerPlayer(level.getServer(), level, profile) {
            @Override public void sendSystemMessage(Component message) { replies.add(new Reply(Component.Serializer.toJson(message), false)); }
            @Override public void sendSystemMessage(Component message, boolean overlay) { replies.add(new Reply(Component.Serializer.toJson(message), overlay)); }
        };
    }
    private static final class Actor implements AutoCloseable {
        private final ServerLevel level;
        private final List<Reply> replies = new ArrayList<>();
        private final ServerPlayer player;
        private final EmbeddedChannel channel;

        private Actor(ServerLevel level, BlockPos target, String name) {
            this.level = level;
            player = player(level, new GameProfile(UUID.randomUUID(), name), replies);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            try {
                level.getServer().getPlayerList().placeNewPlayer(connection, player);
                player.teleportTo(level, target.getX() - 1.5D, target.getY(), target.getZ() + 0.5D, 0, 0);
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        @Override public void close() {
            try {
                if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) { level.getServer().getPlayerList().remove(player); }
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    /** An unresolved owned worker retains its fixture; cleanup is not safe until termination is established. */
    static final class WorkerGate {
        private Thread worker;

        void start(Thread next) {
            requireStopped();
            worker = next;
            try { next.start(); }
            catch (RuntimeException | Error failure) { worker = null; throw failure; }
        }

        void requireStopped() {
            if (worker != null && worker.getState() != Thread.State.TERMINATED) {
                throw new IllegalStateException("Guard worker has not terminated");
            }
        }
    }

    static <T> T offThread(WorkerGate workers, Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        Thread worker = new Thread(task, "arce-seal-guard-check");
        worker.setDaemon(true);
        workers.start(worker);
        try { return task.get(2, TimeUnit.SECONDS); }
        catch (Exception failure) { throw new IllegalStateException("Owned guard worker failed", failure); }
        finally {
            task.cancel(true); worker.interrupt();
            try { worker.join(2_000); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("Guard worker join interrupted", failure); }
            if (worker.isAlive()) { throw new IllegalStateException("Guard worker outlived its fixture"); }
        }
    }
    private static Throwable retain(Throwable first, Throwable failure) {
        if (first == null) { return failure; }
        if (first != failure) { first.addSuppressed(failure); }
        return first;
    }
}
