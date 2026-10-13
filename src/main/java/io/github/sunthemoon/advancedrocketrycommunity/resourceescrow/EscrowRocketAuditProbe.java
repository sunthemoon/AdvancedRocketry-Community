package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Empty-server-only native relocation cuts. A pending real rocket is never credited or retired here. */
final class EscrowRocketAuditProbe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String cut;
    private final boolean recovery;
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private RocketEntity rocket;
    private EscrowTestEndpoints.Fixture source;
    private volatile boolean repairing;
    private volatile boolean saved;
    private int step;
    private boolean waiting;
    private boolean done;

    EscrowRocketAuditProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(cut.equals("RA_BEFORE_REPAIR") || cut.equals("RA_AFTER_REPAIR") || cut.equals("RA_SCHEMA_TWO"), "Unknown rocket audit cut");
        recovery = System.getProperty("advancedrocketrycommunity.escrowScenario").equals("recover");
    }
    void checkpoint(String name) {
        if (!recovery && repairing && cut.equals("RA_BEFORE_REPAIR") && name.equals("K1")) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!saved && System.nanoTime() < deadline) {
                try { TimeUnit.MILLISECONDS.sleep(5); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
            }
            EscrowTags.require(saved, "Relocated entity save did not finish"); pause();
        }
    }
    private void pause() {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ROCKET_AUDIT_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Rocket audit hard stop did not arrive within 30 seconds");
    }
    void tick() {
        if (done || waiting || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong())) { return; }
        if (cut.equals("RA_SCHEMA_TWO")) { migration(); return; }
        if (data == null) { data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server); }
        if (recovery) {
            if (data.ledger().records().size() != 1) { throw new IllegalStateException("Lost pending rocket operation"); }
            var op = data.ledger().records().get(0);
            if (!(level.getEntity(op.destination().identity()) instanceof RocketEntity found)) { return; }
            rocket = found; RocketEscrowAudit.observe(rocket);
            if (op.reason() != EscrowReason.RELOCATED_ENDPOINT || !coordinator.writerIdle()) { return; }
            EscrowTags.require(rocket.operational() && !RocketEscrowGate.idle(rocket)
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == 0 && rocket.getX() == 66.5D,
                    "Relocation recovery changed resources or unlocked the endpoint");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ROCKET_AUDIT_RESULT records=1 reason=RELOCATED_ENDPOINT fuel=0 x=66.5");
            done = true; return;
        }
        switch (step) {
            case 0 -> {
                var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
                source = EscrowTestEndpoints.create(level, POSITION, data.ledger().id());
                rocket = createRocket(level, source.logical(), true); EscrowTags.require(level.addFreshEntity(rocket), "Rocket publication failed");
                EscrowTags.require(rocket.startRiding(source.bank(), true), "Neutral forced-mount control failed"); rocket.stopRiding();
                commandTeleport(65.5D); EscrowTags.require(rocket.getX() == 65.5D, "Neutral command-teleport control failed"); commandTeleport(64.5D);
                EscrowTags.require(!travelCanceled(), "Neutral dimension-event control failed");
                var sample = source.operation(coordinator.nextSequence()); var fuel = rocket.flightData().orElseThrow().fuel();
                var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(), source.logical(),
                        ModIdentity.id("rocket").toString(), 4, 0, 4, EscrowEndpoint.Unit.PRIMARY, 0, fuel.resourceSignature(),
                        fuel.withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1_000).resourceSignature());
                var guard = journal.acknowledgedGuard(source.logical()).orElseThrow();
                var op = new EscrowOperation(sample.id(), sample.sequence(), sample.owner(), sample.createdTick(), sample.phase(), sample.reason(),
                        sample.payload(), sample.payloadHash(), sample.source(), destination, new EscrowGuard(source.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
                coordinator.prepareTest(op, committed -> { EscrowTags.require(committed, "Rocket fixture L1 failed"); waiting = false; step = 2; }); step = 1;
            }
            case 1 -> waiting = coordinator.commitPreparations();
            case 2 -> {
                commandTeleport(65.5D); EscrowTags.require(rocket.getX() == 64.5D, "Pending command teleport moved the rocket");
                EscrowTags.require(!rocket.startRiding(source.bank(), true) && !rocket.isPassenger(), "Pending forced mount succeeded");
                EscrowTags.require(travelCanceled(), "Pending dimension travel was not canceled"); source.bank().discard();
                var parent = rocket.saveWithoutId(new CompoundTag()); var protectedBefore = parent.getCompound("RocketEntityData").copy();
                ListTag pos = new ListTag(); pos.add(DoubleTag.valueOf(66.5D)); pos.add(DoubleTag.valueOf(100D)); pos.add(DoubleTag.valueOf(64.5D)); parent.put("Pos", pos);
                parent.getCompound("RocketEntityData").getCompound("flight_data").getCompound("fuel").putLong("amount", 1_000);
                parent.getCompound("RocketEntityData").getCompound("resource_escrow").putLong("resource_epoch", 9);
                repairing = true; rocket.load(parent); // Same Entity.load boundary used by live /data.
                EscrowTags.require(protectedBefore.equals(rocket.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData")), "Live load changed the protected payload");
                EscrowTags.require(server.saveEverything(false, true, true), "Moved fixture save refused");
                level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
                saved = true; step = 3;
            }
            case 3 -> {
                if (data.ledger().records().get(0).reason() == EscrowReason.RELOCATED_ENDPOINT && coordinator.writerIdle()) {
                    if (cut.equals("RA_AFTER_REPAIR")) { pause(); }
                }
            }
            default -> throw new IllegalStateException("Unknown rocket audit stage");
        }
    }

    private void commandTeleport(double x) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp " + rocket.getUUID() + " " + x + " 100 64.5");
    }
    private boolean travelCanceled() {
        var event = new net.minecraftforge.event.entity.EntityTravelToDimensionEvent(rocket, net.minecraft.world.level.Level.NETHER);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event); return event.isCanceled();
    }

    static RocketEntity createRocket(ServerLevel level, UUID logical, boolean typed) {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1,
                RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(Map.of(RocketPropulsion.Role.MONO, List.of("minecraft:water"),
                        RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(), RocketPropulsion.Role.OXIDIZER, List.of(), RocketPropulsion.Role.WORKING_FLUID, List.of())));
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), new RocketPosition(64, 100, 64),
                blocks, List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 0), 0, typed ? descriptor : null);
        var created = ModEntities.ROCKET.get().create(level); EscrowTags.require(created != null, "Missing rocket type");
        created.initialize(snapshot, logical, UUID.randomUUID()); return created;
    }
    private void migration() {
        EscrowTags.require(ResourceEscrowSavedData.existing(server) == null, "Neutral migration created a ledger");
        if (!recovery) {
            rocket = createRocket(level, UUID.randomUUID(), false);
            var legacy = rocket.saveWithoutId(new CompoundTag()); var wrapper = legacy.getCompound("RocketEntityData");
            var expected = wrapper.copy();
            wrapper.putInt("schema_version", 2); wrapper.remove("resource_escrow"); rocket.load(legacy);
            EscrowTags.require(expected.equals(rocket.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData")),
                    "Schema-2 migration changed resources, snapshot or flight authority");
            EscrowTags.require(rocket.operational() && level.addFreshEntity(rocket), "Schema-2 rocket migration failed");
            EscrowTags.require(server.saveEverything(false, true, true), "Migrated entity save refused");
            level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join(); pause();
        } else {
            var loaded = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(POSITION).inflate(4));
            if (loaded.isEmpty()) { return; }
            EscrowTags.require(loaded.size() == 1 && loaded.get(0).operational()
                    && loaded.get(0).escrowSection().equals(RocketEscrowSection.neutral()), "Neutral rocket restart changed identity or envelope");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ROCKET_UPGRADE_RESULT schema=3 ledger=absent fuel=0"); done = true;
        }
    }
}
