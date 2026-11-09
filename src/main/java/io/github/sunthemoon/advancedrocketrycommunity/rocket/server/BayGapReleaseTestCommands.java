package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * W331's gated native probes for the bay's remaining native rows (W331-DESIGN sections 3.1 to 3.4), registered with the
 * other bay release-test commands only when the release-test property is set. Each entry passes the complete
 * {@link EscrowTestEndpoints} gate before it reads anything. None of them is called by production code, and none
 * changes a cut, an admission decision, a budget or a deadline:
 * <ul>
 *   <li>{@code decommission <satellite>}: the operator decommission that the first-tick hook already makes, then the
 *       release-test registry flush, so the decommission is durable before the harness ends the start (B4);</li>
 *   <li>{@code deployment-status <satellite>}: reads {@link BayReceipts#deploymentStatus}, the value the bay menu shows
 *       (B6.8);</li>
 *   <li>{@code stale-assembly <logical>}: one ASSEMBLY record whose transaction ID is L, in a dimension that never loads,
 *       so restart recovery never touches it (as {@code transaction-writer add} does for an unrelated record) (B14.4).</li>
 * </ul>
 */
final class BayGapReleaseTestCommands {
    /** A dimension that never loads, so restart recovery never acts on the stale record and no chunk is loaded. */
    static final ResourceLocation ABSENT = ModIdentity.id("w331_absent");
    private static final Logger LOGGER = LogUtils.getLogger();

    private BayGapReleaseTestCommands() { }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(Commands.literal("release-test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("decommission").then(Commands.argument("satellite", UuidArgument.uuid())
                        .executes(context -> decommission(context.getSource(), UuidArgument.getUuid(context, "satellite")))))
                .then(Commands.literal("deployment-status").then(Commands.argument("satellite", UuidArgument.uuid())
                        .executes(context -> deploymentStatus(context.getSource(), UuidArgument.getUuid(context, "satellite")))))
                .then(Commands.literal("stale-assembly").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> staleAssembly(context.getSource(), UuidArgument.getUuid(context, "logical"))))))));
    }

    /** B4: decommission X as the operator, then make the registry durable on the owner thread before the harness's end. */
    static int decommission(CommandSourceStack source, UUID satellite) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var registry = SatelliteMissionSavedData.get(server);
        var code = registry.decommission(satellite, BayReleaseTestCommands.OWNER, true, true).code();
        boolean success = code == io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.SUCCESS;
        if (success) { registry.flush(server); }
        LOGGER.info("ARCE_RELEASE_BAY_DECOMMISSIONED satellite={} result={} present={} flushed={} sequence={}", satellite, code,
                registry.satellite(satellite).isPresent(), success, registry.mutationSequence());
        source.sendSuccess(() -> Component.literal("Bay decommission reported"), false);
        return success ? 1 : 0;
    }

    /** B6.8: the deployment status of one satellite, which the bay menu's phase and reason show. */
    static int deploymentStatus(CommandSourceStack source, UUID satellite) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var status = BayReceipts.deploymentStatus(server, satellite);
        LOGGER.info("ARCE_RELEASE_BAY_DEPLOYMENT_STATUS satellite={} phase={} reason={}", satellite,
                status.map(value -> value.phase()).orElse("NONE"), status.map(value -> value.reason()).orElse("NONE"));
        source.sendSuccess(() -> Component.literal("Bay deployment status reported"), false);
        return status.isPresent() ? 1 : 0;
    }

    /** B14.4: an unresolved ASSEMBLY record naming L, in the absent dimension; D0 must refuse with ASSEMBLY_JOURNAL_PENDING. */
    static int staleAssembly(CommandSourceStack source, UUID logical) {
        MinecraftServer server = source.getServer();
        EscrowTestEndpoints.requireEnabled(server);
        var journal = RocketTransactionSavedData.get(server);
        UUID snapshotId = UUID.randomUUID();
        var origin = new RocketPosition(2_000_000 + (int) (snapshotId.getMostSignificantBits() & 0xFFFF) * 64, 64, 2_000_000);
        var snapshot = RocketStructureSnapshot.create(snapshotId, ABSENT, origin, List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                new RocketBlockState(new ResourceLocation("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 0);
        journal.journalFor(snapshot, BayReleaseTestCommands.OWNER).write(new RocketTransactionRecord(logical, RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot), 0, null));
        LOGGER.info("ARCE_RELEASE_BAY_STALE_ASSEMBLY logical={} snapshot={} records={} sequence={}", logical, snapshotId,
                journal.entries().size(), journal.mutationSequence());
        source.sendSuccess(() -> Component.literal("Bay stale assembly record added"), false);
        return 1;
    }
}
