package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** F3 uses the actual O1 -> private normalization -> joint O2 -> O4 publication path. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSettlementNormalizationGameTests {
    private RocketSettlementNormalizationGameTests() { }
    @GameTest(template = "empty", batch = "rocket_settlement_normalization", timeoutTicks = 1_200)
    public static void neutralSurvivorBindsBeforeProofAndResourceChangesAdvanceItsOwnEpoch(GameTestHelper helper) {
        var level = helper.getLevel(); var position = new BlockPos(550_033, 100, 550_033); var chunk = new ChunkPos(position);
        level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z);
        var data = ResourceEscrowSavedData.forTests(level.getServer()); var coordinator = data.bind(level.getServer());
        var pacer = new GameTestTickPacer(level.getServer());
        class Scenario {
            int step; boolean writing; EscrowTestEndpoints.Fixture fixture; RocketEntity rocket; EscrowOperation operation;
            UUID physical; UUID owner; String snapshot;
            void tick() {
                pacer.pace(); if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) { return; }
                if (step == 0) {
                    fixture = EscrowTestEndpoints.create(level, position, data.ledger().id());
                    rocket = RocketSettlementIdleGameTests.createRocket(level, position, fixture.logical());
                    physical = rocket.getUUID(); owner = rocket.ownerId().orElseThrow(); snapshot = rocket.snapshot().orElseThrow().contentHash();
                    var sample = fixture.operation(coordinator.nextSequence()); var fuel = rocket.flightData().orElseThrow().fuel();
                    var after = fuel.withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000);
                    var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), physical, fixture.logical(),
                            ModIdentity.id("rocket").toString(), chunk.x, 0, chunk.z, EscrowEndpoint.Unit.PRIMARY, 0, fuel.resourceSignature(), after.resourceSignature());
                    var payload = sample.payload(); payload.putString("FluidName", ModIdentity.id("rocket_fuel").toString());
                    var journal = RocketTransferSavedData.get(level.getServer()); helper.assertTrue(journal.retryChecked(level.getServer()), "Idle journal is unproved");
                    var guard = journal.acknowledgedGuard(fixture.logical()).orElseThrow();
                    var model = new EscrowOperation(sample.id(), sample.sequence(), owner, sample.createdTick(), sample.phase(), sample.reason(), payload,
                            RocketNativeResources.signature(payload), sample.source(), endpoint, new EscrowGuard(fixture.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
                    var capture = EscrowNativeBinding.captureTest(model, rocket, fixture.source(), fuel, after, data.ledger());
                    operation = capture.operation();
                    helper.assertTrue(level.addFreshEntity(rocket), "Neutral survivor publication failed");
                    coordinator.prepareTest(operation, capture.before(), acknowledged -> helper.assertTrue(acknowledged, "Native L1 failed")); step = 1;
                } else if (step == 1) { if (coordinator.commitPreparations()) { step = 2; } }
                else if (step == 2) {
                    if (!coordinator.writerIdle()) { return; }
                    fixture.applySource(operation); coordinator.transition(operation.id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 3;
                } else if (step == 3) {
                    if (!coordinator.writerIdle() || writing) { return; }
                    if (data.ledger().records().stream().noneMatch(op -> op.id().equals(operation.id())
                            && op.reason() == EscrowReason.OTHER_STATE)) { return; }
                    var operator = EscrowRuntime.of(level.getServer()).settlement();
                    var preview = operator.preview(level.getServer(), operation.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, owner, "NormalizationGameTest");
                    helper.assertTrue(preview.observations().stream().anyMatch(observation -> observation.endpoint().identity().equals(physical)
                            && !observation.bound() && observation.state().epoch() == 0), "O1 lost the neutral audit observation");
                    writing = operator.confirm(level.getServer(), operation.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, owner, preview.digest(),
                            acknowledged -> { helper.assertTrue(acknowledged, "Normalization O1 failed"); step = 4; });
                } else if (step == 4) {
                    if (data.ledger().records().stream().anyMatch(op -> op.id().equals(operation.id()))) { return; }
                    helper.assertTrue(new RocketEscrowSection(data.ledger().id(), 0, List.of()).equals(rocket.escrowSection())
                            && rocket.flightData().orElseThrow().fuel().totalAmount() == 0 && RocketEscrowGate.idle(rocket), "O4 did not prove the prescribed neutral normalization");
                    for (int epoch = 1; epoch <= 2; epoch++) {
                        var flight = rocket.flightData().orElseThrow(); rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                                ModIdentity.id("rocket_fuel"), null, epoch * 1_000L), level.getGameTime()));
                        helper.assertTrue(rocket.escrowSection().epoch() == epoch && rocket.getUUID().equals(physical)
                                && rocket.ownerId().orElseThrow().equals(owner) && rocket.snapshot().orElseThrow().contentHash().equals(snapshot), "Native mutation lost the normalized identity or monotonic epoch");
                    }
                    var encoded = rocket.saveWithoutId(new CompoundTag()).getCompound("RocketEntityData");
                    helper.assertTrue(RocketEscrowSection.decode(encoded.getCompound("resource_escrow")).equals(rocket.escrowSection()), "Normalization did not survive native serialization");
                    rocket.discard(); fixture.bank().discard(); level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); step = 5; helper.succeed();
                }
            }
        }
        var scenario = new Scenario(); helper.onEachTick(scenario::tick);
    }
}
