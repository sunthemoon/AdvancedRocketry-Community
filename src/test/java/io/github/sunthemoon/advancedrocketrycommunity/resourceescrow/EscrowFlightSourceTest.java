package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightSourceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void unsupportedScalarSourceIsARecoverableValidationRefusal() {
        var scalar = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(1_000);
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightSource.signature(scalar));
        var descriptor = new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Mode.ABSTRACT,
                1_000, 0, 1, 1, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Settings.DEFAULTS);
        var nativeFuel = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(descriptor);
        assertEquals(nativeFuel.resourceSignature(), EscrowFlightSource.signature(nativeFuel));
    }

    @Test void sourcePredicateChecksEveryPersistedEnvelopeFieldAndSlotCensus() {
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", EscrowTestFixtures.id(1),
                EscrowTestFixtures.id(2), "advancedrocketrycommunity:rocket", 4, 0, 4, EscrowEndpoint.Unit.PRIMARY, 2,
                EscrowTestFixtures.hash(1), EscrowTestFixtures.hash(1));
        var envelope = new CompoundTag(); envelope.putUUID("owner", EscrowTestFixtures.id(3));
        for (String key : List.of("snapshot", "flight", "wrapper")) { var tag = new CompoundTag(); tag.putString("fixture", key); envelope.put(key, tag); }
        envelope.put("pos", vector(64.5, 100, 64.5)); envelope.put("motion", vector(0, 0, 0));
        envelope.putBoolean("no_gravity", true); envelope.putBoolean("no_physics", true);
        envelope.putBoolean("on_ground", false); envelope.putBoolean("invulnerable", false);
        envelope.putFloat("yaw", 0); envelope.putFloat("pitch", 0);
        var entity = new CompoundTag(); entity.putUUID("UUID", endpoint.identity()); entity.putString("id", endpoint.type());
        var data = new CompoundTag(); data.putInt("schema_version", 3); data.putUUID("assembly_transaction_id", endpoint.logicalRocket());
        data.putUUID("owner_id", envelope.getUUID("owner")); data.put("snapshot", envelope.getCompound("snapshot").copy());
        data.put("flight_data", envelope.getCompound("flight").copy()); data.put("resource_escrow", envelope.getCompound("wrapper").copy());
        entity.put("RocketEntityData", data); entity.put("Pos", envelope.get("pos").copy()); entity.put("Motion", envelope.get("motion").copy());
        var rotation = new ListTag(); rotation.add(FloatTag.valueOf(0)); rotation.add(FloatTag.valueOf(0)); entity.put("Rotation", rotation);
        entity.putBoolean("NoGravity", true); entity.putBoolean("OnGround", false); entity.putBoolean("Invulnerable", false);
        var root = new CompoundTag(); root.putIntArray("Position", new int[] {4, 4}); var entities = new ListTag(); entities.add(entity); root.put("Entities", entities);
        var validator = EscrowFlightSource.validator(endpoint, envelope);
        assertTrue(validator.test(root));
        List<Consumer<CompoundTag>> changes = List.of(
                e -> e.putUUID("UUID", EscrowTestFixtures.id(9)), e -> e.putString("id", "minecraft:pig"),
                e -> e.put("Pos", vector(65.5, 100, 64.5)), e -> e.put("Motion", vector(1, 0, 0)),
                e -> e.putBoolean("NoGravity", false), e -> e.putBoolean("OnGround", true), e -> e.putBoolean("Invulnerable", true),
                e -> e.getList("Rotation", Tag.TAG_FLOAT).set(0, FloatTag.valueOf(1)),
                e -> e.getCompound("RocketEntityData").putUUID("owner_id", EscrowTestFixtures.id(8)),
                e -> e.getCompound("RocketEntityData").putUUID("assembly_transaction_id", EscrowTestFixtures.id(8)),
                e -> e.getCompound("RocketEntityData").putInt("schema_version", 2),
                e -> e.getCompound("RocketEntityData").getCompound("snapshot").putInt("changed", 1),
                e -> e.getCompound("RocketEntityData").getCompound("flight_data").putInt("changed", 1),
                e -> e.getCompound("RocketEntityData").getCompound("resource_escrow").putInt("changed", 1));
        for (var change : changes) {
            var altered = root.copy(); change.accept(altered.getList("Entities", Tag.TAG_COMPOUND).getCompound(0));
            assertFalse(validator.test(altered));
        }
        var otherSlot = root.copy(); otherSlot.putIntArray("Position", new int[] {4, 5}); assertFalse(validator.test(otherSlot));
        var duplicate = root.copy(); duplicate.getList("Entities", Tag.TAG_COMPOUND).add(entity.copy()); assertFalse(validator.test(duplicate));
        var otherPhysical = entity.copy(); otherPhysical.putUUID("UUID", EscrowTestFixtures.id(8));
        duplicate = root.copy(); duplicate.getList("Entities", Tag.TAG_COMPOUND).add(otherPhysical); assertFalse(validator.test(duplicate));
        var missing = root.copy(); missing.put("Entities", new ListTag()); assertFalse(validator.test(missing));
        envelope.getCompound("flight").putInt("external_mutation", 1); assertTrue(validator.test(root), "Predicate must retain an immutable copy");
    }

    private static ListTag vector(double x, double y, double z) {
        var list = new ListTag(); list.add(DoubleTag.valueOf(x)); list.add(DoubleTag.valueOf(y)); list.add(DoubleTag.valueOf(z)); return list;
    }
}
