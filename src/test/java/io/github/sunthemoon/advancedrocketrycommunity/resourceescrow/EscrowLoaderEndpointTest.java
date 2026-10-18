package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W11b3 B3-E: the registered loader endpoint adapter, its signature kind, the fixed debit, observations and pruning. */
class EscrowLoaderEndpointTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.tryParse("minecraft:lava");
    private static final UUID LEDGER = EscrowTestFixtures.id(1), DEVICE = EscrowTestFixtures.id(2), OWNER = EscrowTestFixtures.id(3);
    private static final UUID LOGICAL = EscrowTestFixtures.id(4), PHYSICAL = EscrowTestFixtures.id(5);
    private static final String LEVEL = "minecraft:overworld";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static LoaderRoot root(int amount, LoaderEscrowSection section) {
        var binding = new LoaderBinding(LOGICAL, ResourceLocation.tryParse(LEVEL), PHYSICAL, new BlockPos(12, 64, -3), null);
        return new LoaderRoot(OWNER, DEVICE, binding, amount == 0 ? LoaderTank.EMPTY : LoaderTank.of(WATER, null, amount), section);
    }
    /** What {@code saveWithFullMetadata} returns for a loader at (10, 64, -3). */
    static CompoundTag envelope(LoaderRoot root) {
        var tag = new CompoundTag(); tag.putString("id", EscrowAdapterIds.LOADER);
        tag.putInt("x", 10); tag.putInt("y", 64); tag.putInt("z", -3); tag.put(LoaderRoot.KEY, root.encode());
        return tag;
    }
    static EscrowEndpoint source(LoaderRoot root, String after) {
        return new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, LEVEL, DEVICE, null, EscrowAdapterIds.LOADER, 10, 64, -3,
                EscrowEndpoint.Unit.TANK, root.escrow().epoch(), root.signature(), after);
    }
    static EscrowEndpoint source(LoaderRoot root, int moved) {
        return source(root, root.tank().withAmount(root.tank().amount() - moved).signature(DEVICE));
    }
    static EscrowOperation op(EscrowEndpoint source, CompoundTag payload) {
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, LEVEL, PHYSICAL, LOGICAL, EscrowAdapterIds.ROCKET, 0, 0, -1,
                EscrowEndpoint.Unit.PRIMARY, 0, EscrowTestFixtures.hash(70), EscrowTestFixtures.hash(71));
        return new EscrowOperation(EscrowTestFixtures.id(10), 1, OWNER, 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload,
                RocketNativeResources.signature(payload), source, destination, new EscrowGuard(LOGICAL, null, 1, EscrowTestFixtures.hash(72)));
    }
    static EscrowOperation op(LoaderRoot root, int moved) { return op(source(root, moved), LoaderTank.of(WATER, null, moved).encode()); }

    @Test void theLoaderIsTheRegisteredFluidTankSourceAndNothingElseIs() {
        var loader = source(root(5_000, LoaderEscrowSection.neutral()), 1_000);
        assertTrue(EscrowLoaderEndpoint.is(loader));
        assertEquals(EscrowSignatureKind.FLUID_TANK_V1, EscrowSignatureKind.endpoint(loader));
        assertEquals(EscrowSignatureKind.FLUID_TANK_V1, EscrowSignatureKind.valueOf("FLUID_TANK_V1"), "The kind is persisted by name");
        var otherUnit = new EscrowEndpoint(loader.kind(), loader.level(), loader.identity(), null, loader.type(), loader.x(), loader.y(),
                loader.z(), EscrowEndpoint.Unit.PRIMARY, 0, loader.beforeSignature(), loader.afterSignature());
        var chest = new EscrowEndpoint(loader.kind(), loader.level(), loader.identity(), null, "minecraft:chest", loader.x(), loader.y(),
                loader.z(), EscrowEndpoint.Unit.TANK, 0, loader.beforeSignature(), loader.afterSignature());
        for (var other : List.of(otherUnit, chest)) {
            assertFalse(EscrowLoaderEndpoint.is(other));
            assertEquals(EscrowSignatureKind.FIXTURE_RESOURCE_V1, EscrowSignatureKind.endpoint(other));
        }
        // An observation of a loader endpoint must carry the loader's kind.
        assertThrows(IllegalArgumentException.class, () -> new EscrowSettlementIntent.Observation(loader,
                new EscrowPrefix.State(LEDGER, 0, loader.beforeSignature(), List.of()), false, 5_000,
                EscrowSignatureKind.FIXTURE_RESOURCE_V1, EscrowInvariant.reference(loader)));
    }

    @Test void theDebitMovesExactlyThePayloadAndBindsOneSourceMarker() {
        for (int[] amounts : new int[][] {{5_000, 1_000}, {1_000, 749}, {749, 749}, {1, 1}}) {
            var before = root(amounts[0], LoaderEscrowSection.neutral()); var op = op(before, amounts[1]);
            var envelope = envelope(before); var untouched = envelope.copy();
            var after = EscrowLoaderEndpoint.change(LEDGER, op, envelope);
            assertEquals(untouched, envelope, "The capture is never modified");
            var debited = LoaderRoot.decode(after.get(LoaderRoot.KEY));
            assertEquals(amounts[0] - amounts[1], debited.tank().amount());
            assertEquals(new LoaderEscrowSection(LEDGER, 1, List.of(new EscrowPrefix.Marker(op.id(), 1, "SOURCE", 1))), debited.escrow());
            assertEquals(before.owner(), debited.owner()); assertEquals(DEVICE, debited.device()); assertEquals(before.binding(), debited.binding());
            assertEquals(op.source().afterSignature(), debited.signature());
            var rest = after.copy(); rest.remove(LoaderRoot.KEY); var restBefore = untouched.copy(); restBefore.remove(LoaderRoot.KEY);
            assertEquals(restBefore, rest, "Only the loader root changes");
        }
        // A later fill after pruning starts from the bound section at its epoch.
        var bound = root(4_000, new LoaderEscrowSection(LEDGER, 1, List.of()));
        var next = EscrowLoaderEndpoint.change(LEDGER, op(bound, 1_000), envelope(bound));
        assertEquals(2, LoaderRoot.decode(next.get(LoaderRoot.KEY)).escrow().epoch());
    }

    @Test void theDebitRefusesAnythingButTheExactBeforeState() {
        var neutral = root(5_000, LoaderEscrowSection.neutral());
        var marker = new EscrowPrefix.Marker(EscrowTestFixtures.id(99), 1, "SOURCE", 1);
        var cases = List.of(
                // A section bound to another ledger, a held marker and a later epoch.
                List.of(root(5_000, new LoaderEscrowSection(EscrowTestFixtures.id(9), 0, List.of())), neutral),
                List.of(root(5_000, new LoaderEscrowSection(LEDGER, 1, List.of(marker))), root(5_000, new LoaderEscrowSection(LEDGER, 1, List.of()))),
                List.of(root(5_000, new LoaderEscrowSection(LEDGER, 3, List.of())), neutral),
                // A tank that changed after the capture.
                List.of(root(4_999, LoaderEscrowSection.neutral()), neutral));
        for (var pair : cases) {
            var op = op(pair.get(1), 1_000);
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.change(LEDGER, op, envelope(pair.get(0))));
        }
        // Another Fluid or native tag in the payload than in the tank.
        var tag = new CompoundTag(); tag.putInt("purity", 1);
        for (var payload : List.of(LoaderTank.of(LAVA, null, 1_000).encode(), LoaderTank.of(WATER, tag, 1_000).encode())) {
            var op = op(source(neutral, 1_000), payload);
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.change(LEDGER, op, envelope(neutral)));
        }
        // More than the tank holds, another device and a non-loader envelope.
        var small = root(500, LoaderEscrowSection.neutral());
        var over = op(source(small, EscrowTestFixtures.hash(80)), LoaderTank.of(WATER, null, 1_000).encode());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.change(LEDGER, over, envelope(small)));
        var other = new LoaderRoot(OWNER, EscrowTestFixtures.id(77), null, LoaderTank.of(WATER, null, 5_000), LoaderEscrowSection.neutral());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.change(LEDGER, op(neutral, 1_000), envelope(other)));
        var chest = envelope(neutral); chest.putString("id", "minecraft:chest");
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.change(LEDGER, op(neutral, 1_000), chest));
        // The recorded after-frontier must be the tank that the debit produces.
        var wrongAfter = op(source(neutral, EscrowTestFixtures.hash(81)), LoaderTank.of(WATER, null, 1_000).encode());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.change(LEDGER, wrongAfter, envelope(neutral)));
    }

    @Test void observationsReadPristineAndBoundSectionsAndRefuseForeignOrReplacedDevices() {
        var pristine = root(5_000, LoaderEscrowSection.neutral()); var endpoint = source(pristine, 1_000);
        var neutral = EscrowLoaderEndpoint.observation(endpoint, pristine, LEDGER);
        assertFalse(neutral.bound()); assertEquals(5_000, neutral.quantity());
        assertEquals(new EscrowPrefix.State(LEDGER, 0, pristine.signature(), List.of()), neutral.state());
        assertEquals(EscrowSignatureKind.FLUID_TANK_V1, neutral.signatureKind());
        assertEquals(EscrowInvariant.reference(endpoint), neutral.invariantDigest());
        var marker = new EscrowPrefix.Marker(EscrowTestFixtures.id(10), 1, "SOURCE", 1);
        var held = root(4_000, new LoaderEscrowSection(LEDGER, 1, List.of(marker)));
        var bound = EscrowLoaderEndpoint.observation(endpoint, held, LEDGER);
        assertTrue(bound.bound()); assertEquals(List.of(marker), bound.state().markers()); assertEquals(1, bound.state().epoch());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.observation(endpoint, held, EscrowTestFixtures.id(9)));
        var replaced = new LoaderRoot(OWNER, EscrowTestFixtures.id(77), null, LoaderTank.of(WATER, null, 5_000), LoaderEscrowSection.neutral());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.observation(endpoint, replaced, LEDGER));
    }

    @Test void theDiskObservationIsTheLiveOneAndRefusesARelocatedIdentity() {
        var held = root(4_000, new LoaderEscrowSection(LEDGER, 1, List.of(new EscrowPrefix.Marker(EscrowTestFixtures.id(10), 1, "SOURCE", 1))));
        var endpoint = source(root(5_000, LoaderEscrowSection.neutral()), 1_000);
        var exact = envelope(held);
        assertEquals(EscrowLoaderEndpoint.observation(endpoint, held, LEDGER), EscrowSettlementDisk.observe(chunk(exact), endpoint, LEDGER));
        // An unreadable loader elsewhere names no identity; the same device elsewhere is a relocation, never a survivor.
        var unreadable = envelope(held); unreadable.putInt("x", 11); unreadable.getCompound(LoaderRoot.KEY).putInt("schema_version", 0);
        assertNull(EscrowLoaderEndpoint.device(unreadable));
        assertEquals(EscrowLoaderEndpoint.observation(endpoint, held, LEDGER), EscrowSettlementDisk.observe(chunk(exact, unreadable), endpoint, LEDGER));
        var copy = envelope(held); copy.putInt("x", 11);
        assertEquals(DEVICE, EscrowLoaderEndpoint.device(copy));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(chunk(exact, copy), endpoint, LEDGER));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(chunk(copy), endpoint, LEDGER));
        // A missing device is missing; an invalid root at the exact position is refused, not projected.
        assertTrue(EscrowSettlementDisk.observe(chunk(), endpoint, LEDGER).missing());
        var invalid = envelope(held); invalid.getCompound(LoaderRoot.KEY).putInt("unknown", 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(chunk(invalid), endpoint, LEDGER));
        // The checkpoint's stable-readback predicate is this same disk observation.
        var observed = EscrowLoaderEndpoint.observation(endpoint, held, LEDGER);
        assertTrue(EscrowSettlementDisk.validator(observed, LEDGER).test(chunk(exact)));
        assertFalse(EscrowSettlementDisk.validator(observed, LEDGER).test(chunk(envelope(root(4_001, held.escrow())))));
    }

    @Test void pruningRemovesOnlyMarkersAndNeverTheTankOrEpoch() {
        var marker = new EscrowPrefix.Marker(EscrowTestFixtures.id(10), 1, "SOURCE", 1);
        var held = root(4_000, new LoaderEscrowSection(LEDGER, 1, List.of(marker)));
        assertEquals(new LoaderEscrowSection(LEDGER, 1, List.of()),
                EscrowLoaderEndpoint.pruned(held, new EscrowPrefix.State(LEDGER, 1, held.signature(), List.of())));
        for (var state : List.of(new EscrowPrefix.State(LEDGER, 0, held.signature(), List.of()),
                new EscrowPrefix.State(EscrowTestFixtures.id(9), 1, held.signature(), List.of()),
                new EscrowPrefix.State(LEDGER, 1, EscrowTestFixtures.hash(5), List.of()),
                new EscrowPrefix.State(LEDGER, 1, held.signature(), List.of(new EscrowPrefix.Marker(EscrowTestFixtures.id(11), 2, "SOURCE", 1))))) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderEndpoint.pruned(held, state));
        }
    }

    @Test void validationAcceptsOnlyTheFixedDebitAgainstTheCompleteBinding() {
        var before = root(5_000, LoaderEscrowSection.neutral()); var model = op(before, 1_000);
        var capture = envelope(before); var after = EscrowLoaderEndpoint.change(LEDGER, model, capture);
        var b = model.nativeBinding(); var d = model.destination();
        var binding = new EscrowNativeBinding(EscrowSignatureKind.FUEL_VECTOR_V1, PHYSICAL, LOGICAL, OWNER, EscrowTestFixtures.id(20),
                EscrowTestFixtures.hash(21), EscrowTestFixtures.hash(22), b.sourceReference(), b.destinationReference(), EscrowTestFixtures.hash(23),
                EscrowTestFixtures.hash(24), d.beforeSignature(), d.afterSignature(), EscrowNativeEnvelope.digest(capture),
                EscrowNativeEnvelope.digest(after), EscrowTestFixtures.hash(25), EscrowTestFixtures.hash(26), true);
        var op = new EscrowOperation(model.id(), model.sequence(), model.owner(), model.createdTick(), model.phase(), model.reason(),
                model.payload(), model.payloadHash(), model.source(), d, model.guard(), model.kind(), model.settlement(), binding);
        var root = EscrowLedger.empty(LEDGER).append(List.of(op));
        assertEquals(EscrowReason.NONE, EscrowReconciliationValidation.action(root, op, capture, after, false));
        assertEquals(after, EscrowReconciliationChange.source(LEDGER, op, capture), "Sources dispatch to the registered adapter");
        var tampered = after.copy(); tampered.put(LoaderRoot.KEY, root(4_001, LoaderRoot.decode(after.get(LoaderRoot.KEY)).escrow()).encode());
        assertEquals(EscrowReason.OTHER_STATE, EscrowReconciliationValidation.action(root, op, capture, tampered, false));
        var moved = capture.copy(); moved.put(LoaderRoot.KEY, root(5_001, LoaderEscrowSection.neutral()).encode());
        assertEquals(EscrowReason.OTHER_STATE, EscrowReconciliationValidation.action(root, op, moved, after, false));
        assertEquals(EscrowReason.OTHER_STATE, EscrowReconciliationValidation.action(root, model, capture, after, false),
                "A model binding without the native envelopes never validates");
    }

    /** A terrain record of chunk (0, -1) holding these block entities. */
    private static CompoundTag chunk(CompoundTag... entries) {
        var root = new CompoundTag(); root.putInt("xPos", 0); root.putInt("zPos", -1);
        var list = new ListTag(); for (var entry : entries) { list.add(entry); } root.put("block_entities", list);
        return root;
    }
}
