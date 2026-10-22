package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowSettlementFixturePruningTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ordinaryReplacementWithoutFixtureIsSkippedAndKeepsItsMarker() {
        var fixture = new Fixture();
        fixture.chest.getPersistentData().remove("arce_escrow_fixture");
        var before = fixture.chest.getPersistentData().copy();
        assertNull(assertDoesNotThrow(fixture::prune));
        assertEquals(before, fixture.chest.getPersistentData());
    }

    @Test void missingOrMalformedFixtureIdentityIsSkippedWithoutChangingTheRoot() {
        for (var invalid : List.of(new CompoundTag(), StringTag.valueOf("not-a-uuid"), new IntArrayTag(new int[] {1, 2, 3}))) {
            var fixture = new Fixture();
            var tag = fixture.chest.getPersistentData().getCompound("arce_escrow_fixture");
            tag.put("identity", invalid);
            var before = fixture.chest.getPersistentData().copy();
            assertNull(assertDoesNotThrow(fixture::prune));
            assertEquals(before, fixture.chest.getPersistentData());
            tag.remove("identity"); before = fixture.chest.getPersistentData().copy();
            assertNull(assertDoesNotThrow(fixture::prune));
            assertEquals(before, fixture.chest.getPersistentData());
        }
    }

    @Test void validButDifferentIdentityStillRefusesWithoutPruning() {
        var fixture = new Fixture();
        fixture.chest.getPersistentData().getCompound("arce_escrow_fixture").putUUID("identity", EscrowTestFixtures.id(999));
        var before = fixture.chest.getPersistentData().copy();
        assertEquals(EscrowSettlementPruning.State.refused("source identity or type changed"), fixture.prune());
        assertEquals(before, fixture.chest.getPersistentData());
    }

    @Test void validFixtureStillPrunesOnlyItsReleasedMarkerAndIsIdempotent() {
        var fixture = new Fixture();
        var before = EscrowTestEndpoints.state(fixture.chest.getPersistentData(), fixture.root.id(), false);
        assertEquals(1, before.markers().size());
        assertEquals(EscrowSettlementPruning.State.DONE, fixture.prune());
        var after = EscrowTestEndpoints.state(fixture.chest.getPersistentData(), fixture.root.id(), false);
        assertEquals(new EscrowPrefix.State(before.ledger(), before.epoch(), before.signature(), List.of()), after);
        assertEquals(4_000, EscrowTestEndpoints.amount(fixture.chest.getPersistentData()));
        assertEquals(EscrowSettlementPruning.State.DONE, fixture.prune());
    }

    private static final class Fixture {
        final ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        final EscrowLedger root;
        final List<EscrowSettlementPruning.Group> groups;
        final EscrowSettlementPruning.Visit visit;

        Fixture() {
            var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
            var ledger = EscrowTestFixtures.ledger(op);
            var bank = new CompoundTag(); bank.putUUID("identity", op.source().identity());
            bank.putInt("amount", 4_000); bank.putInt("capacity", 16_000);
            chest.getPersistentData().put("arce_escrow_fixture", bank);
            EscrowTestEndpoints.putState(chest.getPersistentData(), new EscrowPrefix.State(ledger.id(), 1, op.source().afterSignature(),
                    List.of(new EscrowPrefix.Marker(op.id(), op.sequence(), "SOURCE", 1))), false);
            var state = EscrowTestEndpoints.state(chest.getPersistentData(), ledger.id(), false);
            var observations = List.of(new EscrowSettlementIntent.Observation(op.source(), state, true, 4_000),
                    EscrowSettlementIntentTest.observations(ledger, false).get(1));
            var preview = EscrowSettlementIntent.preview(ledger, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    EscrowTestFixtures.id(800), "operator", 10, observations);
            var intent = preview.confirm(preview.actor(), preview.digest(), 11, observations);
            root = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(ledger), intent), intent);
            groups = new EscrowSettlementPruning.Cursor().released(root);
            var group = groups.get(0);
            visit = new EscrowSettlementPruning.Visit(group, group.endpoints().stream()
                    .filter(endpoint -> endpoint.reference().key().equals(op.source().key())).findFirst().orElseThrow());
        }

        EscrowSettlementPruning.State prune() { return EscrowSettlementPruning.fixture(chest, root, visit, Set.of(), groups); }
    }
}
