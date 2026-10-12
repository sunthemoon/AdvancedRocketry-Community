package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native A1 proof and adversarial writer/event cases, without production IDs/admission. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowCoreGameTests {
    private EscrowCoreGameTests() { }
    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 200)
    public static void jointTerrainSlotProofRetainsTheOtherEndpointLease(GameTestHelper helper) {
        var level = helper.getLevel(); BlockPos position = new BlockPos(530_001, 100, 530_001);
        var chunk = new net.minecraft.world.level.ChunkPos(position);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.START, chunk, 2, net.minecraft.util.Unit.INSTANCE);
        level.getChunk(chunk.x, chunk.z);
        var first = EscrowTestEndpoints.create(level, position, UUID.randomUUID());
        var op1 = first.operation(1); first.apply(op1);
        var second = EscrowTestEndpoints.chainSource(first, position.offset(4, 0, 0));
        var op2 = second.operation(2); second.apply(op2);
        var runtime = EscrowRuntime.of(level.getServer()); var barrier = new EscrowBarrier(); var started = new AtomicBoolean();
        helper.onEachTick(() -> {
            barrier.tick(level.getServer().getTickCount()); if (started.get()) { return; }
            started.set(barrier.beginJoint(runtime.store(level), List.of(
                    new EscrowBarrier.Target(op1.source(), first.sourceState(), EscrowTestEndpoints.validator(op1.source(), first.sourceState())),
                    new EscrowBarrier.Target(op2.source(), second.sourceState(), EscrowTestEndpoints.validator(op2.source(), second.sourceState()))),
                    level.getServer().getTickCount(), result -> {
                        helper.assertTrue(result.confirmed() && result.proofs().size() == 2, "Joint terrain proof did not confirm both endpoints");
                        var a = result.proofs().get(op1.source().key()); var b = result.proofs().get(op2.source().key());
                        helper.assertTrue(a.capture() == b.capture(), "Joint endpoints did not share their own store/readback");
                        a.release(); helper.assertTrue(b.valid(), "Releasing one joint endpoint invalidated the other");
                        runtime.monitor().observed(op2.source().level(), false, chunk);
                        helper.assertTrue(!b.valid(), "A newer save did not invalidate all surviving slot tokens");
                        b.release(); barrier.close(); first.bank().discard();
                        level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.START, chunk, 2, net.minecraft.util.Unit.INSTANCE);
                        helper.succeed();
                    }));
        });
    }
    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 200)
    public static void ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier(GameTestHelper helper) {
        var level = helper.getLevel();
        // The framework's template cleanup and the other adversarial fixture must not save this slot.
        BlockPos position = new BlockPos(520_001, 100, 520_001);
        var chunk = new net.minecraft.world.level.ChunkPos(position);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.START, chunk, 2, net.minecraft.util.Unit.INSTANCE);
        level.getChunk(chunk.x, chunk.z); // Explicit fixed test setup, never endpoint-request loading.
        var fixture = EscrowTestEndpoints.create(level, position, UUID.randomUUID());
        var op = fixture.operation(1); fixture.apply(op); var monitor = EscrowRuntime.of(level.getServer()).monitor();
        var store = EscrowRuntime.of(level.getServer()).store(level); var barrier = new EscrowBarrier();
        AtomicInteger phase = new AtomicInteger(); AtomicBoolean pending = new AtomicBoolean();
        helper.onEachTick(() -> {
            barrier.tick(level.getServer().getTickCount()); if (pending.get() || !level.areEntitiesLoaded(fixture.bank().chunkPosition().toLong())) { return; }
            int index = phase.get(); if (index >= 2) {
                helper.assertTrue(EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000
                        && EscrowTestEndpoints.amount(fixture.bank().getPersistentData()) == 1_000, "Native conservation failed");
                barrier.close(); fixture.bank().discard();
                level.getChunkSource().removeRegionTicket(net.minecraft.server.level.TicketType.START, chunk, 2, net.minecraft.util.Unit.INSTANCE);
                helper.succeed(); return;
            }
            var endpoint = index == 0 ? op.source() : op.destination(); var frontier = index == 0 ? fixture.sourceState() : fixture.bankState();
            boolean started = barrier.begin(store, endpoint, frontier, EscrowTestEndpoints.validator(endpoint, frontier),
                    level.getServer().getTickCount(), result -> {
                        helper.assertTrue(result.confirmed(), "Own-store/force/stable readback did not confirm: " + result.reason());
                        helper.assertTrue(result.proof().capture().stored().isDone()
                                && !result.proof().capture().stored().isCompletedExceptionally(), "Own store was not acknowledged");
                        result.proof().release();
                        phase.incrementAndGet(); pending.set(false);
                    });
            pending.set(started);
        });
    }
    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 100)
    public static void aNewerVanillaSaveDuringCapturePreventsTheBarrierStore(GameTestHelper helper) {
        var level = helper.getLevel(); var fixture = EscrowTestEndpoints.create(level, helper.absolutePos(new BlockPos(1, 1, 1)), UUID.randomUUID());
        var op = fixture.operation(1); fixture.apply(op); var monitor = EscrowRuntime.of(level.getServer()).monitor();
        var chunk = level.getChunkAt(fixture.source().getBlockPos()); AtomicBoolean superseded = new AtomicBoolean();
        Consumer<ChunkDataEvent.Save> listener = event -> {
            if (event.getChunk() == chunk && superseded.compareAndSet(false, true)) {
                CompoundTag newer = ChunkSerializer.write(level, chunk);
                MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, newer));
                level.getChunkSource().chunkMap.write(chunk.getPos(), newer);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            boolean refused = false;
            var store = EscrowRuntime.of(level.getServer()).store(level);
            try { store.captureAndStore(op.source(), EscrowTestEndpoints.validator(op.source(), fixture.sourceState())); }
            catch (IllegalArgumentException expected) { refused = true; }
            helper.assertTrue(refused && superseded.get(), "Barrier stored an older capture after a newer vanilla Save");
            helper.assertTrue(chunk.isUnsaved(), "Capture rejection did not restore dirty state");
            superseded.set(false);
            try (var barrier = new EscrowBarrier()) {
                var result = new java.util.concurrent.atomic.AtomicReference<EscrowBarrier.Result>();
                helper.assertTrue(barrier.begin(store, op.source(), fixture.sourceState(), EscrowTestEndpoints.validator(op.source(), fixture.sourceState()),
                        level.getServer().getTickCount(), result::set), "Superseding capture was not processed");
                helper.assertTrue(result.get() != null && !result.get().confirmed() && result.get().reason() == EscrowReason.SUPERSEDING_SAVE
                        && barrier.supersedingSaves() == 1 && barrier.ioFailures() == 0, "Capture supersession was counted as an IO failure");
            }
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); fixture.bank().discard(); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 100)
    public static void aNestedVanillaEntityStoreInvalidatesTheOwnCapture(GameTestHelper helper) {
        var level = helper.getLevel(); var armed = new AtomicBoolean(); var injected = new AtomicBoolean();
        Consumer<net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.entity.Entity>> listener = event -> {
            if (!(event.getObject() instanceof net.minecraft.world.entity.decoration.ArmorStand bank)) { return; }
            event.addCapability(ModIdentity.id("escrow_nested_save_fixture"), new net.minecraftforge.common.capabilities.ICapabilitySerializable<CompoundTag>() {
                @Override public CompoundTag serializeNBT() {
                    if (armed.compareAndSet(true, false)) {
                        helper.assertTrue(level.entityManager.storeChunkSections(bank.chunkPosition().toLong(), entity -> { }), "Nested entity store refused loaded sections");
                        injected.set(true);
                    }
                    return new CompoundTag();
                }
                @Override public void deserializeNBT(CompoundTag tag) { }
                @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                        net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
                    return net.minecraftforge.common.util.LazyOptional.empty();
                }
            });
        };
        MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.entity.Entity.class, listener);
        EscrowTestEndpoints.Fixture fixture;
        try { fixture = EscrowTestEndpoints.create(level, helper.absolutePos(new BlockPos(1, 1, 1)), UUID.randomUUID()); }
        finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        var op = fixture.operation(1); fixture.apply(op); var barrier = new EscrowBarrier();
        AtomicBoolean ran = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (!level.areEntitiesLoaded(fixture.bank().chunkPosition().toLong()) || !ran.compareAndSet(false, true)) { return; }
            armed.set(true); var result = new java.util.concurrent.atomic.AtomicReference<EscrowBarrier.Result>();
            try {
                helper.assertTrue(barrier.begin(EscrowRuntime.of(level.getServer()).store(level), op.destination(), fixture.bankState(),
                        EscrowTestEndpoints.validator(op.destination(), fixture.bankState()), level.getServer().getTickCount(), result::set), "Entity capture was not processed");
                helper.assertTrue(injected.get() && result.get() != null && result.get().reason() == EscrowReason.SUPERSEDING_SAVE
                        && !result.get().confirmed() && barrier.supersedingSaves() == 1 && barrier.ioFailures() == 0,
                        "A nested vanilla entity store was mistaken for the own capture");
            } finally { barrier.close(); fixture.bank().discard(); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 200)
    public static void failedPredecessorKeepsTheAdmissionInItsCapturedSuccessor(GameTestHelper helper) throws Exception {
        var fixture = EscrowTestEndpoints.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), UUID.randomUUID());
        var op = fixture.operation(1); var ledger = new EscrowLedger(fixture.ledger(), 1, 0, List.of(op), List.of(), List.of());
        var directory = Files.createTempDirectory("arce-w11b1-gametest-"); var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger stage = new AtomicInteger();
        AtomicInteger replies = new AtomicInteger(); AtomicBoolean queued = new AtomicBoolean();
        var writer = new EscrowLedgerWriter(file, 1, CheckedSavedDataFile::atomicMove, cut -> {
            if (cut.equals("K1") && stage.incrementAndGet() == 1) {
                held.countDown(); try {
                    if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Native writer gate timed out"); }
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                throw new IllegalStateException("Injected pre-replace failure");
            }
        });
        writer.request(() -> EscrowLedgerWriter.Snapshot.of(ledger.generation(2), Set.of(op.id())), result -> {
            helper.assertTrue(result.outcome() == EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE
                    && writer.containsCaptured(op.id()), "Definite predecessor failure cancelled a captured admission"); replies.incrementAndGet();
        });
        helper.onEachTick(() -> {
            if (held.getCount() == 0 && queued.compareAndSet(false, true)) {
                writer.request(() -> EscrowLedgerWriter.Snapshot.of(ledger.generation(3), Set.of(op.id())), result -> {
                    helper.assertTrue(result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED, "Captured successor did not commit"); replies.incrementAndGet();
                }); release.countDown();
            }
            writer.tick(helper.getLevel().getGameTime());
            if (replies.get() == 2) {
                writer.close(); fixture.bank().discard();
                try { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                helper.succeed();
            }
        });
    }
}
