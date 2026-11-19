package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
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
        var bounded = root.copy(); var census = bounded.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 1; i < EscrowSettlementObserver.ENTITIES_PER_SLOT; i++) { census.add(new CompoundTag()); }
        assertTrue(validator.test(bounded), "Exact slot-census limit must remain readable");
        census.add(new CompoundTag()); assertFalse(validator.test(bounded), "One extra entity must refuse the proof");
        var deep = root.copy(); var branch = new CompoundTag(); deep.getList("Entities", Tag.TAG_COMPOUND).add(branch);
        for (int depth = 1; depth <= 16; depth++) {
            var nested = new ListTag(); var child = new CompoundTag(); nested.add(child); branch.put("Passengers", nested); branch = child;
        }
        assertTrue(validator.test(deep), "Exact passenger-depth limit must remain readable");
        var tooDeep = new ListTag(); tooDeep.add(new CompoundTag()); branch.put("Passengers", tooDeep);
        assertFalse(validator.test(deep), "One extra passenger nesting level must refuse the proof");
        envelope.getCompound("flight").putInt("external_mutation", 1); assertTrue(validator.test(root), "Predicate must retain an immutable copy");
    }

    @Test void transientDeltaMovementIsNotNowWithoutTheStickyFlagButPersistentChangesStaySticky() {
        var unchanged = new java.util.concurrent.atomic.AtomicBoolean(true);
        var compared = new java.util.concurrent.atomic.AtomicInteger();
        // An impulse after the entity tick is visible in END; the rocket's next tick zeroes it.
        for (var motion : List.of(new Vec3(0.125, 0, 0), new Vec3(0, -0.4, 0), new Vec3(0, 0, 1e-9), new Vec3(-0.0, 0, 0))) {
            for (int poll = 0; poll < 3; poll++) {
                assertFalse(EscrowFlightSource.observe(unchanged, motion, () -> { compared.incrementAndGet(); return true; }));
                assertTrue(unchanged.get(), "Transient motion " + motion + " set the sticky flag");
            }
        }
        assertEquals(0, compared.get(), "Transient motion must answer before comparing the capture");
        assertTrue(EscrowFlightSource.observe(unchanged, Vec3.ZERO, () -> { compared.incrementAndGet(); return true; }));
        assertEquals(1, compared.get());
        // A persistent change of position or any other field invalidates the capture exactly as before.
        assertFalse(EscrowFlightSource.observe(unchanged, Vec3.ZERO, () -> false));
        assertFalse(unchanged.get());
        assertFalse(EscrowFlightSource.observe(unchanged, Vec3.ZERO, () -> true), "A later match cleared a persistent change");
        assertFalse(EscrowFlightSource.observe(unchanged, new Vec3(0.125, 0, 0), () -> true));
        assertEquals(1, compared.get(), "A sticky capture compared again");
        // Explicit departure invalidation is the same sticky flag.
        var departed = new java.util.concurrent.atomic.AtomicBoolean(false);
        assertFalse(EscrowFlightSource.observe(departed, Vec3.ZERO, () -> true));
    }

    @Test void aSaveTakenWithTransientMotionStillRefusesTheDiskProof() {
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
        entity.put("RocketEntityData", data); entity.put("Pos", envelope.get("pos").copy()); entity.put("Motion", vector(0.125, 0, 0));
        var rotation = new ListTag(); rotation.add(FloatTag.valueOf(0)); rotation.add(FloatTag.valueOf(0)); entity.put("Rotation", rotation);
        entity.putBoolean("NoGravity", true); entity.putBoolean("OnGround", false); entity.putBoolean("Invulnerable", false);
        var root = new CompoundTag(); root.putIntArray("Position", new int[] {4, 4}); var entities = new ListTag(); entities.add(entity); root.put("Entities", entities);
        assertFalse(EscrowFlightSource.validator(endpoint, envelope).test(root), "A save with transient motion proved the stationary capture");
        entity.put("Motion", vector(0, 0, 0));
        assertTrue(EscrowFlightSource.validator(endpoint, envelope).test(root));
    }

    @Test void launchBoundaryRefusalIsTransientAndDoesNotComparePersistentFields() {
        var boundary = new java.util.concurrent.atomic.AtomicBoolean(false);
        var source = liveSource(boundary::get);
        long captures = source.rocket.governedCaptures();
        for (int visit = 0; visit < 2_000; visit++) {
            assertFalse(source.observe());
            assertTrue(source.unchanged.get(), "A launch-boundary refusal poisoned the capture");
        }
        boundary.set(true);
        assertTrue(source.observe(), "The same capture did not recover after the boundary opened");
        assertEquals(captures, source.rocket.governedCaptures(), "Boundary polling serialized the rocket again");
    }

    @Test void aRefusedBoundarySkipsPersistentComparisonsAndNeverClearsAnExistingStickyFlag() {
        var unchanged = new java.util.concurrent.atomic.AtomicBoolean(true);
        var boundary = new java.util.concurrent.atomic.AtomicBoolean(false);
        var comparisons = new java.util.concurrent.atomic.AtomicInteger();
        for (int visit = 0; visit < 2_000; visit++) {
            assertFalse(EscrowFlightSource.observe(unchanged, Vec3.ZERO, boundary::get,
                    () -> { comparisons.incrementAndGet(); return true; }));
        }
        assertTrue(unchanged.get()); assertEquals(0, comparisons.get());
        boundary.set(true);
        assertTrue(EscrowFlightSource.observe(unchanged, Vec3.ZERO, boundary::get,
                () -> { comparisons.incrementAndGet(); return true; }));
        assertEquals(1, comparisons.get());
        assertFalse(EscrowFlightSource.observe(unchanged, Vec3.ZERO, boundary::get, () -> false));
        boundary.set(false);
        assertFalse(EscrowFlightSource.observe(unchanged, Vec3.ZERO, boundary::get, () -> true));
        boundary.set(true);
        assertFalse(EscrowFlightSource.observe(unchanged, Vec3.ZERO, boundary::get, () -> true));
        assertFalse(unchanged.get());
    }

    // Exercise the real observer and native getters without constructing a Level or publishing an entity.
    static EscrowFlightSource liveSource(java.util.function.BooleanSupplier boundary) {
        var fixture = io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create();
        var rocket = io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.allocate(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class);
        var level = io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.allocate(net.minecraft.server.level.ServerLevel.class);
        set(net.minecraft.server.level.ServerLevel.class, level, "server",
                io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.owned());
        set(net.minecraft.world.level.Level.class, level, "dimension", net.minecraft.world.level.Level.OVERWORLD);
        set(net.minecraft.world.entity.Entity.class, rocket, "level", level);
        set(net.minecraft.world.entity.Entity.class, rocket, "type", net.minecraft.world.entity.EntityType.ARMOR_STAND);
        set(net.minecraft.world.entity.Entity.class, rocket, "chunkPosition", new net.minecraft.world.level.ChunkPos(0, 0));
        set(net.minecraft.world.entity.Entity.class, rocket, "uuid", fixture.record().sourceEntityId());
        position(rocket, new Vec3(8.5, 80, 8.5));
        set(net.minecraft.world.entity.Entity.class, rocket, "deltaMovement", Vec3.ZERO);
        set(net.minecraft.world.entity.Entity.class, rocket, "isAddedToWorld", true);
        set(net.minecraft.world.entity.Entity.class, rocket, "passengers", com.google.common.collect.ImmutableList.of());
        var data = new net.minecraft.network.syncher.SynchedEntityData(rocket);
        try {
            var field = net.minecraft.world.entity.Entity.class.getDeclaredField("DATA_NO_GRAVITY"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var key = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) field.get(null);
            data.define(key, true);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        set(net.minecraft.world.entity.Entity.class, rocket, "entityData", data);
        var flight = EscrowGovernedReturnAction.flight(fixture.source());
        var wrapper = RocketEscrowSection.decode(fixture.source().getCompound("wrapper"));
        set(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class, rocket, "flightData", flight);
        set(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class, rocket, "snapshot", fixture.record().sourceSnapshot());
        set(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class, rocket, "ownerId", fixture.record().ownerId());
        set(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class, rocket, "assemblyTransactionId", fixture.record().logicalRocketId());
        set(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class, rocket, "escrowSection", wrapper);
        rocket.noPhysics = true;
        return new EscrowFlightSource(rocket, boundary);
    }

    static void position(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket, Vec3 position) {
        set(net.minecraft.world.entity.Entity.class, rocket, "position", position);
    }

    private static void set(Class<?> owner, Object target, String field, Object value) {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.set(owner, target, field, value);
    }


    private static ListTag vector(double x, double y, double z) {
        var list = new ListTag(); list.add(DoubleTag.valueOf(x)); list.add(DoubleTag.valueOf(y)); list.add(DoubleTag.valueOf(z)); return list;
    }
}
