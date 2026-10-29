package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaimBook;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** R170: the re-review's PROVE/RETIRE dispatch probes, with retained resources and queued claims. */
final class BayHeldProofCleanupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest
    @EnumSource(value = BayClaim.Stage.class, names = {"PROVE", "RETIRE"})
    void coordinatorReleasesTheHeldClaimsTurnAndProofResources(BayClaim.Stage stage) throws Exception {
        var f = new Fixture(stage);
        assertTrue(f.proof.turnsForTests().take(f.claim.logical()));
        var next = UUID.randomUUID();
        assertFalse(f.proof.turnsForTests().take(next));
        var released = new AtomicInteger();
        var handle = handle(released);
        BaySlotProofs attempt = null;
        if (stage == BayClaim.Stage.PROVE) {
            attempt = new BaySlotProofs(BayAbsenceCensus.slots(record(1)), f.claim.logical(), f.claim.source().orElseThrow(),
                    new CompoundTag(), () -> true, () -> fail("Closed capture woke its claim"), result -> fail("Closed capture completed"));
            BayHeldProofCleanupTest.<List<EscrowBarrier.Proof>>field(attempt, "proofs").add(handle);
            f.map("capturing").put(f.claim.logical(), attempt);
        } else {
            var type = Class.forName(BaySourceProof.class.getName() + "$Proved");
            var constructor = type.getDeclaredConstructor(BaySlotProofs.Proof.class, BooleanSupplier.class,
                    BooleanSupplier.class, CompoundTag.class);
            constructor.setAccessible(true);
            f.map("proofs").put(f.claim.logical(), constructor.newInstance(new BaySlotProofs.Proof(List.of(handle)),
                    (BooleanSupplier) () -> true, (BooleanSupplier) () -> true, new CompoundTag()));
        }
        f.map("watches").put(f.claim.logical(), null);
        f.map("retryAt").put(f.claim.logical(), 100L);
        f.claim.repair("UNEXPECTED_COPY");
        var receipts = f.claim.members().stream().map(member -> member.receipt().orElseThrow()).toList();
        assertEquals(BayStepQueue.Result.IDLE, f.step());
        assertFalse(f.proof.turnsForTests().holds(f.claim.logical()), "Physical hold retained the server-wide D6 turn");
        assertTrue(f.proof.turnsForTests().take(next), "Unrelated claim could not take D6");
        for (var name : List.of("capturing", "proofs", "watches", "retryAt")) { assertTrue(f.map(name).isEmpty(), name); }
        assertEquals(1, released.get());
        assertFalse(handle.valid());
        assertEquals(stage, f.claim.stage());
        assertEquals("UNEXPECTED_COPY", f.claim.releaseRefusal().orElseThrow());
        assertEquals(receipts, f.claim.members().stream().map(member -> member.receipt().orElseThrow()).toList());
        assertSame(f.claim, f.book.get(f.claim.logical()).orElseThrow());
        assertEquals(BayStepQueue.Result.IDLE, f.step());
        assertTrue(f.proof.turnsForTests().holds(next), "Repeated cleanup took another claim's turn");
        assertEquals(1, released.get(), "Proof released more than once");
        if (attempt != null) {
            var late = handle(released);
            var captured = BaySlotProofs.class.getDeclaredMethod("captured", EscrowBarrier.Result.class);
            captured.setAccessible(true);
            captured.invoke(attempt, new EscrowBarrier.Result(late, EscrowReason.NONE));
            assertFalse(late.valid(), "Late capture resurrected a closed attempt");
            assertEquals(2, released.get());
        }
    }

    @ParameterizedTest
    @EnumSource(value = BayClaim.Stage.class, names = {"PROVE", "RETIRE"})
    void coordinatorRemovesAConflictedWaiterWithoutDisturbingTheHolder(BayClaim.Stage stage) throws Exception {
        var f = new Fixture(stage);
        var holder = UUID.randomUUID();
        var next = UUID.randomUUID();
        assertTrue(f.proof.turnsForTests().take(holder));
        assertFalse(f.proof.turnsForTests().take(f.claim.logical()));
        assertFalse(f.proof.turnsForTests().take(next));
        f.claim.repair("ESCROW_AUTHORITY_CONFLICT");
        assertEquals(BayStepQueue.Result.IDLE, f.step());
        assertTrue(f.proof.turnsForTests().holds(holder));
        assertEquals(List.of(next), f.proof.turnsForTests().line());
        f.proof.turnsForTests().leave(holder);
        assertTrue(f.proof.turnsForTests().take(next));
        assertEquals("ESCROW_AUTHORITY_CONFLICT", f.claim.releaseRefusal().orElseThrow());
        assertEquals(stage, f.claim.stage());
    }

    @ParameterizedTest
    @EnumSource(value = BayClaim.Stage.class, names = {"PROVE", "RETIRE"})
    void directProofHoldCleanupControl(BayClaim.Stage stage) {
        var f = new Fixture(stage);
        assertTrue(f.proof.turnsForTests().take(f.claim.logical()));
        f.claim.repair("UNEXPECTED_COPY");
        assertEquals(BayStepQueue.Result.IDLE, stage == BayClaim.Stage.PROVE ? f.proof.prove(f.claim) : f.proof.retire(f.claim));
        assertFalse(f.proof.turnsForTests().holds(f.claim.logical()));
    }

    private static EscrowBarrier.Proof handle(AtomicInteger released) {
        var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."), new ChunkPos(0, 0),
                new AtomicLong(1), 1, 1, () -> {}, released::incrementAndGet);
        return new EscrowBarrier.Proof(null, capture, new AtomicBoolean(true), new AtomicBoolean(true));
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static final class Fixture {
        final BayClaim claim;
        final BayClaimBook book = new BayClaimBook();
        final BaySourceProof proof;
        final BayTransferRecovery coordinator;

        Fixture(BayClaim.Stage stage) {
            var record = record(1);
            claim = BayRestartRows.claims(List.of(registered(intent(record, 0), "a".repeat(64), "b".repeat(64)))).get(0);
            claim.establishSource(record.sourceEntityId());
            claim.advance(BayClaim.Stage.CONSUME);
            claim.advance(BayClaim.Stage.PROVE);
            if (stage == BayClaim.Stage.RETIRE) { claim.advance(stage); }
            var server = UnstartedServer.owned();
            UnstartedServer.set(MinecraftServer.class, server, "running", true);
            proof = new BaySourceProof(server, null, ignored -> {}, (c, op) -> {}, c -> false, (c, guard) -> guard);
            assertTrue(book.begin(claim));
            var released = UnstartedServer.allocate(BayReleasedFlight.class);
            UnstartedServer.set(BayReleasedFlight.class, released, "states", new HashMap<>());
            coordinator = UnstartedServer.allocate(BayTransferRecovery.class);
            UnstartedServer.set(BayTransferRecovery.class, coordinator, "server", server);
            UnstartedServer.set(BayTransferRecovery.class, coordinator, "claims", book);
            UnstartedServer.set(BayTransferRecovery.class, coordinator, "released", released);
            UnstartedServer.set(BayTransferRecovery.class, coordinator, "proof", proof);
            UnstartedServer.set(BayTransferRecovery.class, coordinator, "audited", new HashMap<>());
            // No store, registration, recovery or delegation dependencies: the held dispatch must not touch them.
        }

        Map<UUID, Object> map(String name) throws ReflectiveOperationException { return field(proof, name); }
        Object step() throws ReflectiveOperationException {
            var step = BayTransferRecovery.class.getDeclaredMethod("step", UUID.class);
            step.setAccessible(true);
            return step.invoke(coordinator, claim.logical());
        }
    }
}
