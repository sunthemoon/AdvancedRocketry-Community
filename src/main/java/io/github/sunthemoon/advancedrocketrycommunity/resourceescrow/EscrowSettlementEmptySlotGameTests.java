package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowGameTestProof;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Isolated entity slots exercise absence without a saveable padding entity. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowSettlementEmptySlotGameTests {
    // Two sequential native captures each need the Level's next 20-tick force; allow one more period for
    // callback delivery and the joint-validity assertion. Both slots must be ready before the first capture.
    private static final int PROOF_PAIR_WINDOW_TICKS = 60;

    private EscrowSettlementEmptySlotGameTests() { }
    @GameTest(template = "empty", batch = "escrow_settlement_empty_slots", timeoutTicks = 1_200)
    public static void twoTrulyEmptySlotsReceiveIndependentOwnStoreAbsenceProofs(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var chunks = List.of(new ChunkPos(54_000, 54_000), new ChunkPos(54_001, 54_000));
        for (var chunk : chunks) {
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
        }
        var runtime = EscrowRuntime.of(server); var pacer = new GameTestTickPacer(server);
        var ledger = UUID.randomUUID(); var identity = UUID.randomUUID(); var logical = UUID.randomUUID();
        class Scenario {
            int index; boolean done;
            EscrowGameTestProof<EscrowBarrier.JointResult> pending;
            final List<EscrowBarrier.Proof> proofs = new ArrayList<>();
            void tick() {
                if (done) { return; }
                pacer.pace();
                if (pending != null) { pending.tick(); return; }
                if (index == chunks.size()) {
                    helper.assertTrue(proofs.size() == 2 && proofs.stream().allMatch(EscrowBarrier.Proof::valid), "Empty-slot proofs were not retained together");
                    proofs.forEach(EscrowBarrier.Proof::release);
                    for (var chunk : chunks) { level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); }
                    done = true; helper.succeed(); return;
                }
                var chunk = chunks.get(index);
                for (var required : chunks) {
                    var position = new BlockPos(required.getMinBlockX(), 100, required.getMinBlockZ());
                    if (!level.areEntitiesLoaded(required.toLong()) || !level.isPositionEntityTicking(position)) { return; }
                }
                var bounds = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
                helper.assertTrue(level.getEntities((net.minecraft.world.entity.Entity) null, bounds).isEmpty(), "Absence slot contains a padding entity");
                var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), identity,
                        logical, ModIdentity.id("rocket").toString(), chunk.x, 0, chunk.z, EscrowEndpoint.Unit.PRIMARY,
                        1, "a".repeat(64), "a".repeat(64));
                var observation = new EscrowSettlementIntent.Observation(endpoint, null, false, 0);
                var frontier = new EscrowPrefix.State(ledger, 0, EscrowSettlementTombstones.observationDigest(observation), List.of());
                var target = new EscrowBarrier.Target(endpoint, frontier, EscrowSettlementDisk.validator(observation, ledger));
                pending = new EscrowGameTestProof<>(server::getTickCount,
                        // Recheck on every admission attempt, but never wait across an autosave holding the first proof.
                        (tick, completed) -> (index != 0 || !GameTestAutosave.within(server, PROOF_PAIR_WINDOW_TICKS))
                                && runtime.barrier().beginJoint(runtime.store(level), List.of(target), tick, completed),
                        EscrowBarrier.JointResult::confirmed, EscrowBarrier.JointResult::reason, result -> {
                            pending = null;
                            helper.assertTrue(result.confirmed(), "Empty-slot own-store refused: " + result.reason());
                            var proof = result.proofs().get(endpoint.key());
                            helper.assertTrue(proof != null && proof.valid() && proof.capture().bytes() > 0, "Empty-slot result lacks a stored payload proof");
                            proofs.add(proof); index++;
                        });
                pending.tick();
            }
        }
        var scenario = new Scenario(); helper.onEachTick(scenario::tick);
    }
}
