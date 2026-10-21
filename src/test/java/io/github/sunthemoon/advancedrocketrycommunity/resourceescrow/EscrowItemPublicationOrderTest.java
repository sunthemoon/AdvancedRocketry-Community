package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The real C2 driver order with detached native captures; no world or disk proof is simulated. */
class EscrowItemPublicationOrderTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void replayMismatchLeavesBothResourceEnvelopesEpochsAndMarkersUnchanged(boolean load) {
        var f = EscrowItemReconciliationFixture.create(load); var old = f.op(); var b = old.nativeBinding();
        var mismatched = new EscrowNativeBinding(b.kind(), b.physical(), b.logical(), b.owner(), b.snapshot(),
                EscrowTestFixtures.hash(987654), b.invariant(), b.sourceReference(), b.destinationReference(),
                b.beforeWrapper(), b.afterWrapper(), b.beforeFrontier(), b.afterFrontier(),
                b.sourceBefore(), b.sourceAfter(), b.destinationBefore(), b.destinationAfter(), b.complete());
        var op = new EscrowOperation(old.id(), old.sequence(), old.owner(), old.createdTick(), old.phase(), old.reason(),
                old.payload(), old.payloadHash(), old.source(), old.destination(), old.guard(), old.kind(), old.settlement(), mismatched);
        var ledger = new EscrowLedger(f.ledger().id(), f.ledger().issuedThrough(), f.ledger().writerGeneration(),
                List.of(op), List.of(), List.of());
        assertEquals(ledger, EscrowCodec.decode(EscrowCodec.encode(ledger)));
        assertEquals(EscrowPrefix.Classification.BEFORE, EscrowReconciliationValidation.rocket(ledger.id(),
                EscrowPrefix.chains(ledger.records()).get(op.rocket().key()), f.before()));
        var before = RocketSnapshotNbtCodec.decode(f.before().getCompound("snapshot")).snapshot().orElseThrow();
        var replay = RocketItemCargoMutation.apply(before, EscrowItemCargoPayload.decode(op.payload()), load);
        assertNotEquals(op.nativeBinding().snapshotHash(), replay.contentHash());
        var device = new AtomicReference<>(f.deviceBefore()); var rocket = new AtomicReference<>(f.before());
        var refused = new AtomicReference<EscrowReason>();
        try {
            EscrowCargoReconciliation.applyNext(op, endpoint -> false, endpoint -> false, () -> {
                if (!op.nativeBinding().snapshotHash().equals(replay.contentHash())) {
                    throw new EscrowReconciliationView.Refusal(EscrowReason.OTHER_STATE);
                }
                return true;
            }, endpoint -> {
                if (endpoint.equals(op.device())) {
                    var permit = EscrowItemCargoPublication.issue(ledger, op, device.get(), 10, () -> true);
                    device.set(envelope(device.get(), permit.consume(10, device::get).encode()));
                } else { rocket.set(f.after()); }
            });
        } catch (EscrowReconciliationView.Refusal failure) { refused.set(failure.reason); }
        assertAll(() -> assertEquals(EscrowReason.OTHER_STATE, refused.get()),
                () -> assertEquals(f.deviceBefore(), device.get(), "Device resources, epoch and markers must stay BEFORE"),
                () -> assertEquals(f.before(), rocket.get(), "Rocket resources, epoch and markers must stay BEFORE"));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void budgetedReplayMustFinishBeforeEitherPublicationAndSourceOrderIsPreserved(boolean load) {
        var op = EscrowItemReconciliationFixture.create(load).op(); var publications = new ArrayList<EscrowEndpoint>();
        for (int tick = 0; tick < 3; tick++) {
            assertEquals(EscrowCargoReconciliation.Step.WAITING,
                    EscrowCargoReconciliation.applyNext(op, publications::contains, endpoint -> false, () -> false, publications::add));
            assertTrue(publications.isEmpty());
        }
        assertEquals(EscrowCargoReconciliation.Step.PUBLISHED,
                EscrowCargoReconciliation.applyNext(op, publications::contains, endpoint -> false, () -> true, publications::add));
        assertEquals(List.of(op.source()), publications, "Do not reverse the risk by publishing the rocket first");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void afterRocketIsNeverReplayedOrRolledBack(boolean load) {
        var op = EscrowItemReconciliationFixture.create(load).op(); var publications = new ArrayList<EscrowEndpoint>();
        publications.add(op.rocket()); var replays = new AtomicInteger();
        assertEquals(EscrowCargoReconciliation.Step.PUBLISHED, EscrowCargoReconciliation.applyNext(op,
                publications::contains, endpoint -> false, () -> { replays.incrementAndGet(); return false; }, publications::add));
        assertEquals(List.of(op.rocket(), op.device()), publications); assertEquals(0, replays.get());
        assertEquals(EscrowCargoReconciliation.Step.DONE, EscrowCargoReconciliation.applyNext(op,
                publications::contains, endpoint -> false, () -> fail("Both AFTER must not replay"), endpoint -> fail("Both AFTER must not publish")));
    }

    private static CompoundTag envelope(CompoundTag before, CompoundTag root) {
        var result = before.copy(); result.put("arce_rocket_item_cargo", root); return result;
    }
}
