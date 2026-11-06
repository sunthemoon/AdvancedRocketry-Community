package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketSeatRosterGameTests.replaceField;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketSourceRetirementFixtures.field;

/** W300 review regressions. The fenced journal record has its own batch and is removed on every exit. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSeatReviewGameTests {
    private RocketSeatReviewGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_seat_review", timeoutTicks = 240)
    public static void ridingAnotherLoadedCopyResetsLogicalAbsence(GameTestHelper helper) { new Observation(helper, Mode.COPY).start(); }

    @GameTest(template = "rocket_test", batch = "rocket_seat_review_fence", timeoutTicks = 240)
    public static void matchLimitFenceResetsAbsenceWithoutEscrow(GameTestHelper helper) { new Observation(helper, Mode.FENCE).start(); }

    @GameTest(template = "rocket_test", batch = "rocket_seat_review", timeoutTicks = 240)
    public static void recordlessLoginKeepsOfflineSeatThenReleasesOnlineAbsence(GameTestHelper helper) { new Observation(helper, Mode.RECORDLESS).start(); }

    @GameTest(template = "rocket_test", batch = "rocket_seat_review", timeoutTicks = 240)
    public static void newPlayerSessionStartsFreshGraceWithoutStrongReference(GameTestHelper helper) { new Observation(helper, Mode.SESSION).start(); }

    @GameTest(template = "rocket_test", batch = "rocket_seat_review_journal", timeoutTicks = 240)
    public static void groundSneakStillReleasesWhenJournalIsUnavailable(GameTestHelper helper) { new Observation(helper, Mode.SNEAK).start(); }

    @GameTest(template = "rocket_test", batch = "rocket_seat_review", timeoutTicks = 240)
    public static void operatorReplyExplainsOnlineRiderDismount(GameTestHelper helper) { new Observation(helper, Mode.OPERATOR).start(); }

    @AfterBatch(batch = "rocket_seat_review")
    public static void closeMain(ServerLevel level) { closeOpen(level); }
    @AfterBatch(batch = "rocket_seat_review_fence")
    public static void closeFence(ServerLevel level) { closeOpen(level); }
    @AfterBatch(batch = "rocket_seat_review_journal")
    public static void closeJournal(ServerLevel level) { closeOpen(level); }

    private enum Mode { COPY, FENCE, RECORDLESS, SESSION, SNEAK, OPERATOR }
    private static final Set<Observation> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static void closeOpen(ServerLevel level) {
        for (var observation : List.copyOf(OPEN)) { if (observation.helper.getLevel() == level) { observation.close(); } }
    }

    private static final class Observation implements AutoCloseable {
        private final GameTestHelper helper;
        private final Mode mode;
        private RocketSeatRosterGameTests.Fixture f;
        private RocketEntity copy;
        private RocketTransferSavedData fencedJournal, blockedJournal;
        private Object previousBlocked;
        private Map<UUID, String> fenceReasons;
        private Map<UUID, Long> fenceAttempts;
        private int started, releaseAt;
        private boolean closed;

        Observation(GameTestHelper helper, Mode mode) { this.helper = helper; this.mode = mode; }
        void start() { OPEN.add(this); helper.onEachTick(this::tick); }
        private boolean assigned() { return f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isPresent(); }
        private void initialize() throws Exception {
            f = new RocketSeatRosterGameTests.Fixture(helper);
            f.player.setInvulnerable(true);
            started = helper.getLevel().getServer().getTickCount();
            releaseAt = mode == Mode.SESSION ? 20 : 80;
            var floor = helper.absolutePos(new BlockPos(9, 1, 3));
            for (int x = -2; x <= 3; x++) { for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlockAndUpdate(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            } }
            if (mode == Mode.OPERATOR) {
                var replies = new java.util.ArrayList<String>();
                var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2)
                        .withSource(new net.minecraft.commands.CommandSource() {
                            public void sendSystemMessage(net.minecraft.network.chat.Component message) { replies.add(message.getString()); }
                            public boolean acceptsSuccess() { return true; }
                            public boolean acceptsFailure() { return true; }
                            public boolean shouldInformAdmins() { return false; }
                        });
                int result = source.getServer().getCommands().performPrefixedCommand(source,
                        "arce rocket release-seat " + f.rocket.getStringUUID() + " " + f.playerId);
                helper.assertTrue(result == 1 && !assigned() && !f.player.isPassenger(), "Operator did not release and dismount the rider");
                helper.assertTrue(replies.stream().anyMatch(reply -> reply.contains("dismounts the target if riding this rocket")),
                        "Command reply did not explain physical dismount");
                close(); helper.succeed(); return;
            }
            if (mode == Mode.SNEAK) {
                blockedJournal = RocketTransferSavedData.get(helper.getLevel().getServer());
                previousBlocked = replaceField(blockedJournal, "preservedBlockedData", new net.minecraft.nbt.CompoundTag());
                helper.assertTrue(!blockedJournal.operational() && RocketRuntime.seatReconnectPending(f.player, f.rocket),
                        "Unavailable journal did not hold absence");
                f.sneakTick();
                helper.assertTrue(!assigned() && !f.player.isPassenger(), "Ground sneak did not release with unavailable journal");
                close(); helper.succeed(); return;
            }
            if (mode == Mode.RECORDLESS) {
                f.journal.remove(f.record.transferId()); f.confirm(); f.disconnect();
                helper.assertTrue(f.journal.entries().isEmpty()
                        && RocketTransferSavedData.get(helper.getLevel().getServer())
                            .findByLogicalRocket(f.record.logicalRocketId()).isEmpty(), "Recordless control still has a transfer record");
            } else { f.player.stopRiding(); }
        }
        private void beginFence() {
            var server = helper.getLevel().getServer();
            // Keep the world's journal and first-event outbox intact; replacing the root simulates rollback.
            fencedJournal = RocketTransferSavedData.get(server);
            fencedJournal.put(f.record);
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            var recovery = RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers());
            var fences = field(recovery, "fences");
            fenceReasons = field(fences, "reasons"); fenceAttempts = field(fences, "attempts");
            // Seed the non-escrow conflict and its normal retry interval without manufacturing 64 duplicate entities.
            fenceReasons.put(f.record.transferId(), "MATCH_LIMIT");
            fenceAttempts.put(f.record.transferId(), (long) server.getTickCount());
            helper.assertTrue(RocketSeatRelease.mutationAllowed(f.rocket, f.playerId), "Fence control was already mutation-held");
            recovery.onPlayerLoggedIn(f.player, fencedJournal);
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerReconnectQueue reconnects = field(recovery, "reconnects");
            helper.assertTrue(!reconnects.pending(f.playerId) && !f.player.isPassenger()
                    && "MATCH_LIMIT".equals(fenceReasons.get(f.record.transferId())), "Fenced login did not skip seating");
        }
        private void endFence() {
            if (fenceReasons != null) {
                fenceReasons.remove(f.record.transferId()); fenceAttempts.remove(f.record.transferId());
                fenceReasons = null; fenceAttempts = null;
            }
            if (fencedJournal != null) {
                fencedJournal.remove(f.record.transferId());
                RocketJournalGameTestSupport.acknowledgeFixture(helper);
                fencedJournal = null;
            }
        }
        private void tick() {
            if (closed) { return; }
            try {
                if (f == null) {
                    for (var relative : List.of(new BlockPos(3, 2, 3), new BlockPos(9, 2, 3))) {
                        if (!helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(helper.absolutePos(relative)).toLong())) { return; }
                    }
                    initialize(); if (closed) { return; }
                }
                int age = helper.getLevel().getServer().getTickCount() - started;
                if (age == 20) {
                    if (mode == Mode.COPY) {
                        copy = ModEntities.ROCKET.get().create(helper.getLevel());
                        copy.initializeTransferred(f.record.destinationSnapshot(), f.record.logicalRocketId(), f.record.ownerId(),
                                f.rocket.flightData().orElseThrow());
                        copy.setPos(f.rocket.position().add(2, 0, 0));
                        helper.assertTrue(helper.getLevel().addFreshEntity(copy) && f.player.startRiding(copy, true)
                                && !copy.getUUID().equals(f.rocket.getUUID()), "Second loaded copy did not seat the rider");
                    }
                    if (mode == Mode.FENCE) { beginFence(); }
                    if (mode == Mode.SESSION) {
                        Map<UUID, ?> absent = field(f.rocket.seatInput(), "absent");
                        var observation = absent.get(f.playerId);
                        helper.assertTrue(observation != null, "No absence observation before replacing the session");
                        WeakReference<?> reference = field(observation, "player");
                        helper.assertTrue(reference.get() == f.player, "Observation does not weakly identify this session");
                        var old = f.player;
                        f.disconnect(); f.player = f.join(); f.player.setInvulnerable(true);
                        helper.assertTrue(f.player != old, "Login reused the old ServerPlayer instance");
                    }
                }
                if (age == releaseAt) {
                    if (mode == Mode.COPY) { f.player.stopRiding(); copy.discard(); copy = null; }
                    if (mode == Mode.FENCE) { endFence(); }
                    if (mode == Mode.RECORDLESS) {
                        helper.assertTrue(assigned(), "Offline recordless seat was released");
                        f.login(); f.player.setInvulnerable(true);
                        helper.assertTrue(!f.player.isPassenger(), "Recordless login unexpectedly reseated the player");
                    }
                }
                helper.getLevel().tickNonPassenger(f.rocket);
                if (age < releaseAt + 39) { helper.assertTrue(assigned(), mode + ": seat lost before fresh eligible grace"); }
                if (mode == Mode.COPY && age == 60) {
                    // A sneak held on B must not let A consume B's input or remove A's logical assignment.
                    f.player.setShiftKeyDown(true); f.rocket.seatInput().tick(f.rocket);
                    helper.assertTrue(assigned() && f.player.getVehicle() == copy && f.player.isShiftKeyDown(),
                            "Other copy consumed sneak or released logical membership"); f.player.setShiftKeyDown(false);
                }
                if (age >= releaseAt + 41) {
                    helper.assertTrue(!assigned(), mode + ": online absence was not released after grace");
                    if (mode == Mode.RECORDLESS) {
                        f.player.teleportTo(helper.getLevel(), f.rocket.getX() + 2, f.rocket.getY(), f.rocket.getZ(), 0, 0);
                        f.player.containerMenu = new io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu(
                                1, f.player.getInventory(), f.rocket);
                        RocketRuntime.requestFlightIntent(f.player, f.rocket.getId(), RocketFlightAction.BOARD,
                                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination.MOON, UUID.randomUUID());
                        helper.assertTrue(f.player.getVehicle() == f.rocket && assigned(), "Recordless player could not BOARD again");
                    }
                    close(); helper.succeed();
                }
            } catch (Throwable failure) { close(); helper.fail(mode + ": " + failure); }
        }
        @Override public void close() {
            if (closed) { return; } closed = true; OPEN.remove(this);
            if (blockedJournal != null) { replaceField(blockedJournal, "preservedBlockedData", previousBlocked); }
            endFence();
            if (copy != null) { copy.discard(); }
            if (f != null) { f.close(); }
        }
    }
}
