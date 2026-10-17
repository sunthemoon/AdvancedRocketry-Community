package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R18's pre-F2 rider normalization retains assignments and the checked no-T boundary. */
class EscrowPreF2RiderBoundaryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void acceptedRiderSubsetsAndOrdersShareTheSameSourceDiskImageAndArmDigest() {
        var fixture = GovernedTransferFixture.create(1, 3, false);
        var source = fixture.source();
        var riders = source.getList("passengers", Tag.TAG_COMPOUND);
        var empty = new ListTag();
        var subset = new ListTag(); subset.add(riders.get(1).copy());
        var reverse = new ListTag(); reverse.add(riders.get(2).copy()); reverse.add(riders.get(0).copy());
        var ordered = new ListTag(); ordered.add(riders.get(0).copy()); ordered.add(riders.get(2).copy());
        var disk = sourceSlot(source);
        var hashes = new HashSet<String>();
        for (var physicalRiders : List.of(empty, subset, reverse, ordered, riders)) {
            var observed = source.copy(); observed.put("passengers", physicalRiders.copy());
            var preparation = prepare(fixture, observed);
            var root = preparation.preflight(EscrowLedger.empty(preparation.launch().wrapper().ledger()), 1);
            var arm = root.armedFlights().get(0);
            var record = preparation.record(arm);
            assertEquals(EscrowGovernedEnvelope.comparison(observed), record.nativeTransfer().orElseThrow().sourceEnvelope(record));
            assertEquals(source.getCompound("flight"), observed.getCompound("flight"), "Assignments do not change");
            assertTrue(EscrowFlightSource.validator(endpoint(observed), observed).test(disk),
                    "F1's existing native validator accepts the same saved source for each rider projection");
            assertEquals(32, arm.encode().getByteArray("source").length);
            assertEquals(arm, EscrowFlightTables.Armed.decode(arm.encode()));
            hashes.add(arm.sourceEnvelope());
        }
        assertEquals(1, hashes.size());
        assertFalse(disk.getList("Entities", Tag.TAG_COMPOUND).getCompound(0).contains("Passengers"));
    }

    @Test void checkedNoTAndPersistedAssignmentsPermitDismountedPreF2Cancellation() throws Exception {
        var fixture = GovernedTransferFixture.create(1, 3, false);
        var source = fixture.source(); var riders = source.getList("passengers", Tag.TAG_COMPOUND);
        var subset = new ListTag(); subset.add(riders.get(2).copy()); subset.add(riders.get(0).copy());
        source.put("passengers", subset);
        var preparation = prepare(fixture, source);
        var root = preparation.preflight(EscrowLedger.empty(preparation.launch().wrapper().ledger()), 1);
        root = EscrowCodec.decode(EscrowCodec.encode(root));
        var arm = root.armedFlights().get(0);
        var restored = source.copy(); restored.put("passengers", new ListTag());
        assertTrue(EscrowFlightSource.validator(endpoint(source), source).test(sourceSlot(restored)));
        assertEquals(arm.sourceEnvelope(), EscrowGovernedEnvelope.digest(restored));
        assertNotEquals(arm.sourceEnvelope(), EscrowNativeEnvelope.digest(source), "Earlier crewed development arms remain incompatible");
        assertEquals(source.getCompound("flight"), restored.getCompound("flight"));

        var journal = new RocketTransferSavedData();
        try {
            EscrowArmedRestartBoundaryTest.acknowledge(journal,
                    directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            var noTransfer = EscrowFlightJournal.noTransfer(journal, arm.logical(), arm.transfer(), arm.serial());
            assertTrue(noTransfer.getAsBoolean()); assertTrue(journal.entries().isEmpty());
            assertTrue(EscrowPreF2Cancellation.eligible(root, arm, EscrowGovernedEnvelope.digest(source), noTransfer));
            assertTrue(EscrowPreF2Cancellation.eligible(root, arm, EscrowGovernedEnvelope.digest(restored), noTransfer));

            // The same normalized comparison also applies after F2 without substituting recorded riders.
            var notYetPersisted = preparation.record(arm);
            var comparison = EscrowGovernedPhaseAction.sourceComparison(notYetPersisted, restored);
            assertEquals(restored, comparison);
            assertEquals(arm.sourceEnvelope(), EscrowNativeEnvelope.digest(comparison));
            assertTrue(journal.entries().isEmpty(), "No synthetic F2 record grants cancellation authority");
        } finally { journal.stop(); }
    }

    private static EscrowGovernedLaunch prepare(GovernedTransferFixture.Fixture fixture, CompoundTag source) {
        var record = fixture.record();
        var originalArm = record.nativeTransfer().orElseThrow().armed();
        var idle = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow();
        var countdown = idle.withPlan(record.sourceFlightData().plan().orElseThrow()).startCountdown(record.createdAtGameTime());
        return EscrowGovernedLaunch.prepare(EscrowLedger.empty(originalArm.ledger()), source, record.sourceSnapshot(),
                countdown, record.destinationSnapshot(), originalArm.destination(), originalArm.journal());
    }

    private static EscrowEndpoint endpoint(CompoundTag source) {
        var chunk = source.getIntArray("chunk");
        var wrapper = RocketEscrowSection.decode(source.getCompound("wrapper"));
        var fuel = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow().fuel();
        return new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, source.getString("level"), source.getUUID("physical"),
                source.getUUID("logical"), source.getString("type"), chunk[0], 0, chunk[1], EscrowEndpoint.Unit.PRIMARY,
                wrapper.epoch(), fuel.resourceSignature(), fuel.resourceSignature());
    }

    private static CompoundTag sourceSlot(CompoundTag source) {
        var entity = new CompoundTag(); entity.putUUID("UUID", source.getUUID("physical"));
        entity.putString("id", source.getString("type"));
        var data = new CompoundTag(); data.putInt("schema_version", 3);
        data.putUUID("assembly_transaction_id", source.getUUID("logical")); data.putUUID("owner_id", source.getUUID("owner"));
        data.put("snapshot", source.getCompound("snapshot").copy()); data.put("flight_data", source.getCompound("flight").copy());
        data.put("resource_escrow", source.getCompound("wrapper").copy()); entity.put("RocketEntityData", data);
        entity.put("Pos", source.get("pos").copy()); entity.put("Motion", source.get("motion").copy());
        var rotation = new ListTag(); rotation.add(FloatTag.valueOf(source.getFloat("yaw")));
        rotation.add(FloatTag.valueOf(source.getFloat("pitch"))); entity.put("Rotation", rotation);
        entity.putBoolean("NoGravity", source.getBoolean("no_gravity")); entity.putBoolean("OnGround", source.getBoolean("on_ground"));
        entity.putBoolean("Invulnerable", source.getBoolean("invulnerable"));
        var entities = new ListTag(); entities.add(entity);
        var root = new CompoundTag(); root.putIntArray("Position", source.getIntArray("chunk")); root.put("Entities", entities);
        return root;
    }
}
