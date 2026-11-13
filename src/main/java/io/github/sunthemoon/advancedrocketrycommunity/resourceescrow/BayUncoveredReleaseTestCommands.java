package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/** W358 native fixtures. No subscriber, static mutable state, production caller, or automatic admission. */
public final class BayUncoveredReleaseTestCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-000000128d01");
    private static final long FILL_NAMESPACE = 358;
    private BayUncoveredReleaseTestCommands() { }

    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) { return; }
        var root = Commands.literal("w358")
                .then(Commands.literal("owner-fill").then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .executes(c -> fill(c.getSource(), IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("unfill").executes(c -> unfill(c.getSource())))
                .then(Commands.literal("escrow-fill").then(Commands.argument("count", IntegerArgumentType.integer(1, 63))
                        .executes(c -> escrow(c.getSource(), IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("loader").then(Commands.argument("logical", UuidArgument.uuid())
                        .then(Commands.argument("action", StringArgumentType.word()).executes(c -> loader(c.getSource(),
                                UuidArgument.getUuid(c, "logical"), StringArgumentType.getString(c, "action"))))))
                .then(Commands.literal("launch").then(Commands.argument("logical", UuidArgument.uuid())
                        .then(Commands.argument("transfer", UuidArgument.uuid()).executes(c -> launch(c.getSource(),
                                UuidArgument.getUuid(c, "logical"), UuidArgument.getUuid(c, "transfer"))))))
                .then(Commands.literal("writer").then(Commands.argument("action", StringArgumentType.word())
                        .executes(c -> writer(c.getSource(), StringArgumentType.getString(c, "action")))));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(s -> s.hasPermission(2)).then(root))));
    }

    private static int fill(CommandSourceStack source, int count) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        String hash = RocketNativeResources.hash("W358 capacity");
        int queued = 0;
        for (int n = 0; n < count; n++) {
            var row = new BayReceipt(new UUID(FILL_NAMESPACE, n + 1), BayReceiptTestHooks.nextSequence(server),
                    new UUID(FILL_NAMESPACE, 100 + n / 8), new RocketPosition(0, n % 8, 0), new UUID(FILL_NAMESPACE, 200 + n),
                    OWNER, ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA, ModIdentity.id("earth").toString(),
                    hash, new UUID(FILL_NAMESPACE, 300 + n / 8), hash, null, hash, null, BayReceipt.Phase.INTENT, "NONE",
                    false, false, 0, server.getTickCount(), server.getTickCount(), 0);
            if (BayReceiptTestHooks.put(server, row, outcome -> { }).queued()) { queued++; }
        }
        LOGGER.info("ARCE_W358_FILL requested={} queued={}", count, queued);
        return queued;
    }

    private static int unfill(CommandSourceStack source) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var rows = BayReceiptTestHooks.store(server).orElseThrow().receipts().stream()
                .filter(r -> r.op().getMostSignificantBits() == FILL_NAMESPACE).toList();
        int queued = 0;
        for (var row : rows) { if (BayReceiptTestHooks.remove(server, row.op(), outcome -> { }).queued()) { queued++; } }
        LOGGER.info("ARCE_W358_UNFILL requested={} queued={}", rows.size(), queued);
        return queued;
    }

    /** Real ledger writes of typed fixture operations, with absent endpoints so no native resources are changed. */
    private static int escrow(CommandSourceStack source, int count) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var data = ResourceEscrowSavedData.forTests(server);
        var coordinator = data.bind(server);
        EscrowTags.require(data.ledger().records().isEmpty(), "W358 escrow fill requires an empty ledger");
        for (int n = 1; n <= count; n++) {
            CompoundTag payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1000);
            String hash = RocketNativeResources.hash("W358 escrow " + n);
            var logical = new UUID(35801, n);
            var op = new EscrowOperation(new UUID(35802, n), coordinator.nextSequence(), new UUID(35803, (n - 1) / 16), 1,
                    EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload, RocketNativeResources.signature(payload),
                    new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", new UUID(35804, n), null,
                            "minecraft:chest", 2_000_000 + n * 16, 64, 2_000_000, EscrowEndpoint.Unit.TANK, 0, hash, hash),
                    new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", new UUID(35805, n), logical,
                            "minecraft:armor_stand", 2_000_000 + n * 16, 64, 2_000_000, EscrowEndpoint.Unit.PRIMARY, 0, hash, hash),
                    new EscrowGuard(logical, null, 1, hash));
            coordinator.prepareTest(op, committed -> LOGGER.info("ARCE_W358_ESCROW_ACK op={} committed={}", op.id(), committed));
        }
        LOGGER.info("ARCE_W358_ESCROW requested={} pending={}", count, coordinator.admissionAuthority().pendingCount());
        return count;
    }

    private static RocketEntity rocket(MinecraftServer server, UUID logical) {
        return server.overworld().getEntities(ModEntities.ROCKET.get(), r -> r.assemblyTransactionId().filter(logical::equals).isPresent())
                .stream().findFirst().orElseThrow();
    }

    private static int loader(CommandSourceStack source, UUID logical, String action) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var rocket = rocket(server, logical); var level = (ServerLevel) rocket.level();
        var origin = rocket.flightData().orElseThrow().currentOrigin();
        var at = new BlockPos(origin.x() - 2, origin.y(), origin.z());
        if (action.equals("create")) {
            FluidFuelLoaderEscrowFixtures.loader(level, at, OWNER, tank(ROCKET_FUEL, 1000), binding(rocket));
        }
        var loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
        var before = counts(server, logical);
        var nativeBefore = rocket.saveWithoutId(new CompoundTag()); var deviceBefore = loader.saveWithFullMetadata();
        boolean captured = false;
        if (action.equals("visit")) {
            if (!RocketTransferSavedData.get(server).retryChecked(server)) {
                LOGGER.info("ARCE_W358_LOADER_WAIT logical={}", logical); return 0;
            }
            var runtime = EscrowRuntime.of(server);
            runtime.loaderAdmissionForTests(server, device(loader), true);
            try { captured = loader.admitFromQueue(); }
            finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
        } else { EscrowTags.require(action.equals("create") || action.equals("status"), "Unknown W358 loader action"); }
        boolean unchanged = before.equals(counts(server, logical)) && nativeBefore.equals(rocket.saveWithoutId(new CompoundTag()))
                && deviceBefore.equals(loader.saveWithFullMetadata());
        var data = ResourceEscrowSavedData.existing(server);
        LOGGER.info("ARCE_W358_LOADER logical={} action={} captured={} status={} unchanged={} tank={} fuel={} ledger={} epoch={} markers={} records={} idle={}",
                logical, action, captured, loader.lastAttempt(), unchanged, amount(loader), bank(rocket, false), rocket.escrowSection().ledger(),
                rocket.escrowSection().epoch(), rocket.escrowSection().markers().size(), data == null ? 0 : data.ledger().records().size(),
                data == null || data.bind(server).writerIdle());
        return 1;
    }

    private static int launch(CommandSourceStack source, UUID logical, UUID transfer) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var result = RocketRuntime.requestAdminFlight(rocket(server, logical), RocketDestination.MOON, transfer);
        LOGGER.info("ARCE_W358_LAUNCH logical={} transfer={} result={}", logical, transfer, result.code());
        return 1;
    }

    private static int writer(CommandSourceStack source, String action) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var store = BayReceiptTestHooks.store(server).orElseThrow();
        if (action.equals("arm")) {
            var row = store.receipts().stream().filter(r -> r.op().getMostSignificantBits() == FILL_NAMESPACE).findFirst().orElseThrow();
            EscrowRuntime.of(server).bayReceipts(server).commitCutForTests(server).orElseThrow()
                    .arm(BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.HOLD_UNKNOWN, "W358_UNKNOWN", row.op());
            EscrowTags.require(BayReceiptTestHooks.put(server, row.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", server.getTickCount()),
                    outcome -> { }).queued(), "W358 UNKNOWN update refused");
        } else { EscrowTags.require(action.equals("status"), "Unknown W358 writer action"); }
        var authority = BayReceiptTestHooks.authority(server);
        var writer = store.writer();
        var data = ResourceEscrowSavedData.existing(server);
        LOGGER.info("ARCE_W358_WRITER unknown={} captured={} successor={} pending={} retained={} escrow={} bay={} fluid={}", writer.unknown(),
                writer.captured() == null ? 0 : writer.captured().receipts().size(), writer.hasSuccessor(), authority.pendingCount(),
                authority.retainedCount(), data == null ? 0 : data.ledger().records().size(),
                authority.checkReserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EscrowAdmissionAuthority.Consumer.BAY),
                authority.checkReserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EscrowAdmissionAuthority.Consumer.PROPULSION));
        return 1;
    }
}
