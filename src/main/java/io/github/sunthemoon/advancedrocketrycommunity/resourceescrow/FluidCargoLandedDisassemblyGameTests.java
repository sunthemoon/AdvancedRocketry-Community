package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W131-R2 A1: a real cargo result through P1-F8's checked retained-record removal, not the plain path or an S2 cut. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidCargoLandedDisassemblyGameTests {
    private static Scenario running;
    private FluidCargoLandedDisassemblyGameTests() { }

    @GameTest(template = "empty", batch = "fluid_cargo_landed_disassembly", timeoutTicks = 1_600)
    public static void retainedLegacyRecordDisassemblesExactlyTheLatestCargoOnce(GameTestHelper helper) {
        running = new Scenario(helper); helper.onEachTick(running::tick);
    }

    @AfterBatch(batch = "fluid_cargo_landed_disassembly")
    public static void cleanup(ServerLevel level) { if (running != null) { running.close(); running = null; } }

    private static final class Scenario implements AutoCloseable {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final BlockPos origin = new BlockPos(636_008 + 512 * 5, 100, 636_008);
        final EscrowCargoGameTestWorld world;
        RocketEntity rocket;
        RocketFluidCargoBlockEntity device;
        RocketStructureSnapshot before;
        RocketTransferRecord retained;
        EscrowOperation operation;
        RocketLandedDisassemblyGameTestSupport support;
        RocketLandedDisassemblyGameTestSupport.Requester requester;
        UUID token;
        String latestHash;
        int step, since;
        boolean done;

        Scenario(GameTestHelper helper) {
            this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer());
            world = new EscrowCargoGameTestWorld(helper, List.of(origin, origin.offset(8, 0, 0)));
        }

        void tick() {
            if (done) { return; }
            pacer.pace();
            switch (step) {
                case 0 -> {
                    if (!world.ready()) { return; }
                    world.isolate();
                    rocket = world.rocket(origin, new FluidStack(Fluids.WATER, 5_000)); before = rocket.snapshot().orElseThrow();
                    device = world.device(origin.offset(2, 0, 0), rocket, RocketCargoRole.UNLOAD, 0);
                    world.enable(device, true); step = 1;
                }
                case 1 -> {
                    operation = world.member(device); if (operation == null) { return; }
                    world.enable(device, false);
                    check(operation.guard().transfer() == null && operation.phase() == EscrowOperation.Phase.PREPARED,
                            "The cargo mutation must precede installation of the legacy record");
                    step = 2;
                }
                case 2 -> {
                    if (world.member(device) != null || !rocket.escrowSection().markers().isEmpty()
                            || !EscrowCargoGameTestWorld.section(device).markers().isEmpty()) { return; }
                    latestHash = rocket.snapshot().orElseThrow().contentHash();
                    check(!latestHash.equals(before.contentHash()) && EscrowCargoGameTestWorld.cargo(rocket) == 4_000
                            && EscrowCargoGameTestWorld.tank(device) == 1_000 && rocket.escrowSection().epoch() == 1
                            && world.data().ledger().tombstones().isEmpty(), "The real unload did not retire with its latest cargo");
                    retainedRecordDisassembly(); since = world.server.getTickCount(); step = 3;
                }
                case 3 -> {
                    assertRestored();
                    if (world.server.getTickCount() - since < 20) { return; }
                    check(device.displayStatus() == RocketCargoStatus.STALE_BINDING, "Disassembly did not stale the device binding");
                    var lines = EscrowOperatorDiagnostics.status(world.server);
                    check(lines.get(0).startsWith("escrow admission=disabled operations=0/64 held=0 settling_members=0 released=0 pruning_groups=0 ")
                            && lines.get(1).startsWith("receipts=0/64 receipts_reserved=0 accept_current_members=0 abandon_members=0 floor_members=0 ")
                            && lines.get(1).endsWith(" ready_heads=0/64 armed=0/64"), "Disassembly changed the escrow inspector: " + lines);
                    close(); done = true; helper.succeed();
                }
                default -> { }
            }
        }

        private void retainedRecordDisassembly() {
            var journal = RocketTransferSavedData.get(world.server);
            // Gated setup only, after the real unguarded mutation. No named-cargo operation is advanced or released here.
            retained = EscrowCargoTransferFixtures.record(rocket, before, false);
            world.transfers.add(retained.transferId());
            check(retained.phase() == RocketTransferPhase.COMMITTED && retained.nativeTransfer().isEmpty()
                    && retained.destinationSnapshot().contentHash().equals(before.contentHash())
                    && !retained.destinationSnapshot().contentHash().equals(latestHash), "Legacy fixture did not retain the pre-cargo snapshot");
            journal.put(retained); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            check(journal.acknowledgedCurrent() && diskRecord() != null
                    && diskRecord().getString("checksum").equals(retained.checksum()), "Legacy COMMITTED record was not checked on disk");
            support = RocketLandedDisassemblyGameTestSupport.create(helper);
            check(support.recover(retained.transferId()).status() == RocketTransferRecoveryReport.Status.RECOVERED
                    && journal.find(retained.transferId()).isPresent() && support.resourceMutationAllowed(rocket)
                    && rocket.snapshot().orElseThrow().contentHash().equals(latestHash), "Checked legacy authority was not settled without replacing cargo");
            check(support.admission(rocket, true) == RocketValidationCode.DISASSEMBLY_PENDING,
                    "The retained record did not select P1-F8 instead of plain disassembly");
            requester = new RocketLandedDisassemblyGameTestSupport.Requester(world.level, rocket.ownerId().orElseThrow());
            requester.setPos(rocket.position()); support.requestDisassembly(requester, rocket);
            check(requester.offers == 1 && requester.token != null, "No fuel-disposal consent was offered");
            token = requester.token;
            check(support.confirmDisassembly(requester, token) && support.pending(rocket) && rocket.isAlive()
                    && "RELEASED".equals(support.stage(retained.logicalRocketId()))
                    && RocketValidationCode.DISASSEMBLY_PENDING.translationKey().equals(requester.lastActionBar())
                    && journal.find(retained.transferId()).isEmpty() && !journal.acknowledgedCurrent()
                    && journal.resourceMutationUnproved(retained.logicalRocketId()) && !support.resourceMutationAllowed(rocket)
                    && world.level.getBlockState(origin).isAir() && world.level.getBlockState(origin.above()).isAir(),
                    "Disassembly restored before checked removal acknowledgement or never entered pending");
            support.requestDisassembly(requester, rocket);
            check(requester.offers == 1 && RocketValidationCode.DISASSEMBLY_PENDING.translationKey().equals(requester.lastActionBar())
                    && rocket.isAlive() && world.level.getBlockState(origin).isAir(), "A repeat pending request started another restoration");
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            check(journal.acknowledgedCurrent() && diskRecord() == null && !support.pending(rocket)
                    && "NONE".equals(support.stage(retained.logicalRocketId())) && requester.discarded == 1_000,
                    "The checked removal did not finish P1-F8 and report exactly the consented fuel disposal");
            assertRestored();
            check(!support.confirmDisassembly(requester, token)
                    && requester.messages.contains("message.advancedrocketrycommunity.rocket.disassembly_confirmation_invalid"),
                    "A spent token was accepted after restoration");
            assertRestored();
        }

        private CompoundTag diskRecord() {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            var path = world.server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(type.fileName());
            var root = CheckedSavedDataFile.readPayload(path, type).orElseThrow();
            return root.getList("transfers", 10).stream().map(CompoundTag.class::cast)
                    .filter(tag -> tag.getUUID("transfer_id").equals(retained.transferId())).findFirst().orElse(null);
        }

        private void assertRestored() {
            check(rocket.isRemoved() && world.level.getBlockEntity(origin) instanceof PressurizedTankBlockEntity tank
                    && tank.fluidState().getFluid() == Fluids.WATER && tank.fluidState().getAmount() == 4_000
                    && world.level.getBlockState(origin.above()).is(Blocks.IRON_BLOCK)
                    && EscrowCargoGameTestWorld.tank(device) == 1_000
                    && world.level.getEntities(ModEntities.ROCKET.get(), new AABB(origin).inflate(8), entity -> true).isEmpty()
                    && RocketTransferSavedData.get(world.server).find(retained.transferId()).isEmpty()
                    && RocketTransactionSavedData.get(world.server).entries().isEmpty(),
                    "Restoration duplicated cargo, used the recorded 5,000 mB instead of the latest 4,000 mB, or retained recovery work");
        }

        private void check(boolean value, String message) { helper.assertTrue(value, message); }
        @Override public void close() { world.close(); }
    }
}
