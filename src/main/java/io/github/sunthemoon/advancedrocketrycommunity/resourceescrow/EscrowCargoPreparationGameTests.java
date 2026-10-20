package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** C-P fixtures, not production cargo admission or native restart/proof evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowCargoPreparationGameTests {
    @GameTest(template = "rocket_test", batch = "cargo_preparation", timeoutTicks = 200)
    public static void largePreparationLocksAndPublishesOnlyWithAnExactOneUsePermit(GameTestHelper helper) {
        var scenario = new Scenario(helper);
        helper.onEachTick(scenario::tick);
    }

    @GameTest(template = "rocket_test", batch = "cargo_preparation", timeoutTicks = 200)
    public static void abandonedAndUnpublishedTicketsExpireAtFortyServerTicks(GameTestHelper helper) {
        var scenario = new ExpiryScenario(helper);
        helper.onEachTick(scenario::tick);
    }

    private static final class ExpiryScenario {
        private final GameTestHelper helper;
        private RocketEntity rocket;
        private RocketCargoPreparations.Ticket ticket;
        private RocketStructureSnapshot before;
        private int phase, since;
        ExpiryScenario(GameTestHelper helper) { this.helper = helper; }

        void tick() {
            if (phase == 4) { return; }
            try {
                var server = helper.getLevel().getServer();
                if (rocket == null) {
                    var origin = helper.absolutePos(new BlockPos(2, 2, 2));
                    if (!helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())) { return; }
                    var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server);
                    if (!journal.retryChecked(server)) { return; }
                    rocket = create(helper, origin, false); before = rocket.snapshot().orElseThrow();
                }
                var preparations = RocketRuntime.cargoPreparations(server);
                if (ticket == null) {
                    ticket = preparations.begin(rocket, delta(before), true).orElse(null);
                    if (ticket == null) { return; }
                    since = server.getTickCount();
                }
                if (phase == 2) {
                    if (!ticket.advance()) { return; }
                    since = server.getTickCount(); phase = 3;
                }
                int elapsed = server.getTickCount() - since;
                if (elapsed < 40) {
                    helper.assertTrue(ticket.valid() && RocketRuntime.cargoPreparing(rocket), "Ticket expired before forty ticks");
                } else {
                    helper.assertTrue(!RocketRuntime.cargoPreparing(rocket)
                            && ticket.cursor().state() == RocketCargoPreparation.State.DISCARDED, "Abandoned ticket retained lock");
                    helper.assertTrue(rocket.snapshot().orElseThrow() == before, "Expiry changed authoritative snapshot");
                    if (phase == 3) { rocket.discard(); phase = 4; helper.succeed(); }
                    else { ticket = null; phase = 2; }
                }
            } catch (Throwable failure) {
                if (ticket != null) { ticket.close(); }
                if (rocket != null && rocket.isAlive()) { rocket.discard(); }
                throw failure;
            }
        }
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private RocketEntity rocket, small;
        private RocketCargoPreparations.Ticket ticket;
        private EscrowCargoPublication.Permit permit;
        private EscrowCargoPublication.Preparation publication;
        private EscrowCargoPayload delta, smallDelta;
        private EscrowOperation operation;
        private EscrowLedger root;
        private RocketStructureSnapshot before;
        private int phase, preparations, deferrals;
        Scenario(GameTestHelper helper) { this.helper = helper; }

        void tick() {
            if (phase == 9) { return; }
            try {
                if (phase == 0) {
                    var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(2, 2, 2));
                    if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())) { return; }
                    var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(level.getServer());
                    if (!journal.retryChecked(level.getServer())) { return; }
                    rocket = create(helper, origin, true); small = create(helper, origin.offset(0, 0, 1), false);
                    before = rocket.snapshot().orElseThrow(); delta = delta(before); smallDelta = delta(small.snapshot().orElseThrow());
                    var oldSession = new RocketCargoPreparations();
                    var cancelled = oldSession.begin(rocket, delta, true).orElseThrow();
                    oldSession.close();
                    check(cancelled.cursor().state() == RocketCargoPreparation.State.DISCARDED, "Server-session stop retained cursor");
                    var newSession = new RocketCargoPreparations();
                    check(!newSession.locked(rocket.assemblyTransactionId().orElseThrow()), "Restart retained preparation lock");
                    newSession.close();
                    root = new EscrowLedger(UUID.randomUUID(), 0, 0, List.of(), List.of(), List.of());
                    operation = capture(rocket, delta, root); root = root.append(List.of(operation));
                    phase = 1;
                }
                if (phase == 1 || phase == 4 || phase == 6) {
                    var started = RocketRuntime.cargoPreparations(rocket.getServer()).begin(rocket, delta, true);
                    if (started.isEmpty()) { return; }
                    ticket = started.orElseThrow(); publication = null; preparations++; phase++;
                }
                if (phase == 2 || phase == 5 || phase == 7) {
                    check(!RocketRuntime.resourceMutationAllowed(rocket), "Preparation did not lock resources");
                    check(RocketRuntime.cargoPreparations(rocket.getServer()).begin(small, smallDelta, true).isEmpty(), "Small request displaced active preparation");
                    deferrals++;
                    check(rocket.snapshot().orElseThrow() == before, "Before snapshot stopped being authoritative");
                    check(RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, UUID.randomUUID()).code()
                            == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                            "Launch ignored preparation lock");
                    check(RocketLandedDisassemblyGameTestSupport.create(helper).disassembleForReleaseTest(rocket, -1)
                            == RocketValidationCode.REGION_BUSY, "Disassembly ignored preparation lock");
                    var flight = rocket.flightData().orElseThrow();
                    boolean refused = false;
                    try { rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                            ResourceLocation.tryParse("minecraft:water"), null, 999), helper.getLevel().getGameTime())); }
                    catch (IllegalStateException expected) { refused = true; }
                    check(refused, "Resource mutation ignored preparation lock");
                    if (!ticket.advance()) { return; }
                    var budget = RocketRuntime.cargoPreparations(rocket.getServer()).budget();
                    check(budget.chargedBytes() <= RocketCargoWorkBudget.MAX_BYTES && budget.chargedVisits() <= RocketCargoWorkBudget.MAX_VISITS,
                            "Preparation exceeded tick budget");
                    if (publication == null) { publication = new EscrowCargoPublication.Preparation(root, operation, ticket, () -> true); }
                    permit = publication.issue();
                    if (permit == null) { return; }
                    if (phase == 2) { phase = 3; return; }
                    if (phase == 5) {
                        float yaw = rocket.getYRot(); rocket.setYRot(yaw + 1);
                        rejects(() -> rocket.applyEscrowCargo(permit), "Changed before frame was accepted");
                        rocket.setYRot(yaw); phase = 6; return;
                    }
                    rocket.applyEscrowCargo(permit);
                    rejects(() -> rocket.applyEscrowCargo(permit), "Permit could be reused");
                    check(rocket.snapshot().orElseThrow().contentHash().equals(operation.nativeBinding().snapshotHash()), "Publication hash differs");
                    check(rocket.escrowSection().epoch() == 1 && rocket.escrowSection().markers().size() == 1, "Publication lost revision/marker");
                    check(rocket.flightData().orElseThrow().fuel().totalAmount() == 1000, "Cargo changed propulsion");
                    check(preparations == 3 && deferrals > 3, "Large request did not span fair deferrals");
                    phase = 8;
                }
                if (phase == 3) {
                    rejects(() -> rocket.applyEscrowCargo(permit), "Stale-tick permit was accepted");
                    check(!RocketRuntime.cargoPreparing(rocket), "Stale permit retained preparation"); phase = 4;
                } else if (phase == 8) {
                    var other = RocketRuntime.cargoPreparations(rocket.getServer()).begin(small, smallDelta, true).orElseThrow();
                    other.close(); check(!RocketRuntime.cargoPreparing(small), "Cancellation retained lock");
                    other = RocketRuntime.cargoPreparations(rocket.getServer()).begin(small, smallDelta, true).orElseThrow();
                    var saved = small.saveWithoutId(new CompoundTag());
                    var logical = small.assemblyTransactionId().orElseThrow();
                    small.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    check(other.cursor().state() == RocketCargoPreparation.State.DISCARDED && !RocketRuntime.cargoPreparing(small), "Unload retained cursor");
                    small = Objects.requireNonNull(ModEntities.ROCKET.get().create(helper.getLevel()));
                    small.load(saved);
                    check(small.assemblyTransactionId().filter(logical::equals).isPresent(), "Reload lost logical identity");
                    check(helper.getLevel().addFreshEntity(small), "Reloaded rocket fixture failed");
                    check(!RocketRuntime.cargoPreparing(small), "Reloaded logical rocket retained old lock");
                    other = RocketRuntime.cargoPreparations(rocket.getServer()).begin(small, smallDelta, true).orElseThrow();
                    small.setPos(small.position().add(0, 0, 1));
                    check(!RocketRuntime.cargoPreparing(small) && other.cursor().state() == RocketCargoPreparation.State.DISCARDED,
                            "Position change retained invalid cursor");
                    small.discard();
                    rocket.discard(); phase = 9; helper.succeed();
                }
            } catch (Throwable failure) {
                if (ticket != null) { ticket.close(); }
                if (rocket != null && rocket.isAlive()) { rocket.discard(); }
                if (small != null && small.isAlive()) { small.discard(); }
                throw failure;
            }
        }
        private void check(boolean value, String message) { helper.assertTrue(value, message); }
        private void rejects(Runnable action, String message) {
            boolean refused = false;
            try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { refused = true; }
            check(refused, message);
        }
    }

    private static RocketEntity create(GameTestHelper helper, BlockPos origin, boolean large) {
        var settings = RocketPropulsion.Settings.DEFAULTS;
        var fluids = new EnumMap<>(settings.allowedFluids()); fluids.put(RocketPropulsion.Role.MONO, List.of("minecraft:water"));
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2000, 0, 1, 1, settings.withAllowedFluids(fluids));
        var properties = new TreeMap<String, String>();
        for (int i = 0; i < 32; i++) { properties.put(String.format("%02d", i) + "k".repeat(62), "v".repeat(64)); }
        var state = new RocketBlockState(ResourceLocation.tryParse("fixture:palette_shared"), properties);
        var blocks = new ArrayList<RocketBlock>();
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                PressurizedTankRocketAdapter.payload(FluidStack.EMPTY)));
        for (int i = 1; i < (large ? 2048 : 2); i++) {
            blocks.add(new RocketBlock(new RocketPosition(i % 16, i / 256, i / 16 % 16), state));
        }
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), blocks, List.of(),
                new RocketStats(blocks.size(), 100, 1000, 2000, 1, 0, 1, 1), 25, propulsion);
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(helper.getLevel()));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1000), 25));
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Rocket fixture failed"); return rocket;
    }
    private static EscrowCargoPayload delta(RocketStructureSnapshot snapshot) {
        return new EscrowCargoPayload(new EscrowCargoPayload.Cursor(snapshot.snapshotId(), 0, 0, 0, PressurizedTankRocketAdapter.ID.toString()),
                new FluidStack(Fluids.WATER, 1).writeToNBT(new CompoundTag()));
    }
    private static EscrowOperation capture(RocketEntity rocket, EscrowCargoPayload delta, EscrowLedger root) {
        var before = rocket.snapshot().orElseThrow(); var after = RocketCargoMutation.apply(before, delta, true);
        var logical = rocket.assemblyTransactionId().orElseThrow(); var chunk = rocket.chunkPosition();
        var flight = rocket.flightData().orElseThrow(); var deviceId = UUID.randomUUID();
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, rocket.level().dimension().location().toString(), rocket.getUUID(), logical,
                EscrowAdapterIds.ROCKET, chunk.x, 0, chunk.z, EscrowEndpoint.Unit.TANK, rocket.escrowSection().epoch(),
                EscrowSignatureKind.composite(logical, before.contentHash(), flight.fuel().resourceSignature()),
                EscrowSignatureKind.composite(logical, after.contentHash(), flight.fuel().resourceSignature()));
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, endpoint.level(), deviceId, null,
                "advancedrocketrycommunity:rocket_fluid_loader", 0, 64, 0, EscrowEndpoint.Unit.TANK, 0,
                LoaderTank.of(new FluidStack(Fluids.WATER, 1)).signature(deviceId), LoaderTank.EMPTY.signature(deviceId));
        var payload = delta.encode();
        var op = new EscrowOperation(UUID.randomUUID(), 1, rocket.ownerId().orElseThrow(), 0, EscrowOperation.Phase.PREPARED,
                EscrowReason.NONE, payload, RocketNativeResources.signature(payload), source, endpoint,
                new EscrowGuard(logical, null, 1, "0".repeat(64)), EscrowOperation.Kind.FLUID_CARGO_LOAD, new CompoundTag());
        var next = new RocketEscrowSection(root.id(), endpoint.afterEpoch(), List.of(new EscrowPrefix.Link(op, endpoint, "DESTINATION").marker()));
        // Fixture-only capture. No device balance is exposed and no native proof is claimed.
        return EscrowCargoCapture.capture(root.id(), op, EscrowNativeEnvelope.rocket(rocket),
                EscrowNativeEnvelope.rocket(rocket, after, flight, next), new CompoundTag(), new CompoundTag()).operation();
    }
}
