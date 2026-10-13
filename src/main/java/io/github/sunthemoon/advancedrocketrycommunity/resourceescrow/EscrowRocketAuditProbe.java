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
        EscrowTags.require(cut.equals("RA_BEFORE_REPAIR") || cut.equals("RA_AFTER_REPAIR"), "Unknown rocket audit cut");
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
                source = EscrowTestEndpoints.create(level, POSITION, data.ledger().id()); source.bank().discard();
                var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1,
                        RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(Map.of(RocketPropulsion.Role.MONO, List.of("minecraft:water"),
                                RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(), RocketPropulsion.Role.OXIDIZER, List.of(), RocketPropulsion.Role.WORKING_FLUID, List.of())));
                var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                        new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
                var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), new RocketPosition(64, 100, 64),
                        blocks, List.of(new RocketPosition(0, 1, 0)), new RocketStats(2, 100, 1_000, 2_000, 1, 1, 1, 0), 0, descriptor);
                rocket = ModEntities.ROCKET.get().create(level); EscrowTags.require(rocket != null, "Missing rocket type");
                rocket.initialize(snapshot, source.logical(), UUID.randomUUID()); EscrowTags.require(level.addFreshEntity(rocket), "Rocket publication failed");
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
                var parent = rocket.saveWithoutId(new CompoundTag()); var protectedBefore = parent.getCompound("RocketEntityData").copy();
                ListTag pos = new ListTag(); pos.add(DoubleTag.valueOf(66.5D)); pos.add(DoubleTag.valueOf(100D)); pos.add(DoubleTag.valueOf(64.5D)); parent.put("Pos", pos);
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
}
