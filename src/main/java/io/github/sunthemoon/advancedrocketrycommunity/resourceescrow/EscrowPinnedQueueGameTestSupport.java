package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** Detached checked-ARM consumer over a real rocket source proof; not a complete launch/F2 fixture. */
public final class EscrowPinnedQueueGameTestSupport implements AutoCloseable {
    private final GameTestHelper helper;
    private final Path directory;
    private final ResourceEscrowSavedData data = ResourceEscrowSavedData.create();
    private final EscrowCoordinator coordinator;
    private final RocketEntity rocket;
    private final EscrowTestEndpoints.Fixture backlog;
    private final BlockPos chest;
    private final EscrowFlightSource frozen;
    private EscrowBarrier.Proof proof;
    private long queuedAt = -1;
    private boolean drained, ordinaryAcknowledged;
    private int attempts, captures;
    private Boolean acknowledged;

    public EscrowPinnedQueueGameTestSupport(GameTestHelper helper, BlockPos position) {
        this.helper = helper;
        EscrowTestEndpoints.requireEnabled(helper.getLevel().getServer());
        try { directory = Files.createTempDirectory("arce-w151-queue-"); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        var writer = new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0);
        coordinator = new EscrowCoordinator(data, writer);
        rocket = EscrowWarmupFixtures.governed(helper.getLevel(), position, data.ledger().id());
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Governed rocket was not published");
        frozen = new EscrowFlightSource(rocket);
        chest = position.offset(0, 8, 0);
        backlog = EscrowTestEndpoints.create(helper.getLevel(), chest, data.ledger().id());
        coordinator.prepareTest(backlog.operation(1), committed -> {
            helper.assertTrue(committed, "Ordinary ledger backlog was refused"); ordinaryAcknowledged = true;
        });
    }

    public boolean begin(long now) {
        var runtime = EscrowRuntime.of(helper.getLevel().getServer());
        boolean admitted = runtime.barrier().begin(runtime.store(helper.getLevel()), frozen.endpoint, frozen.frontier,
                frozen.validator(), now, result -> {
                    helper.assertTrue(result.confirmed(), "Governed source proof failed: " + result.reason());
                    proof = result.proof(); queuedAt = helper.getLevel().getServer().getTickCount();
                    UUID logical = rocket.assemblyTransactionId().orElseThrow();
                    var launch = new EscrowFlightAuthority.Launch(logical, rocket.ownerId().orElseThrow(), rocket.getUUID(), rocket.escrowSection(),
                            frozen.signature, frozen.invariant, UUID.randomUUID(), UUID.randomUUID(), frozen.digest, frozen.digest,
                            frozen.digest, frozen.digest, new EscrowGuard(logical, null, 1, frozen.digest), Set.of());
                    helper.assertTrue(coordinator.flightWrite(logical, (root, generation) -> {
                        helper.assertTrue(ordinaryAcknowledged, "Flight bypassed ordinary ledger admission");
                        helper.assertTrue(frozen.observe(), "Source changed before checked ARM capture");
                        captures++; return EscrowFlightAuthority.arm(root, launch, generation);
                    }, () -> proof.valid() && frozen.unchanged.get(), committed -> {
                        acknowledged = committed; proof.release();
                    }), "The checked ARM queue refused a valid source proof");
                });
        if (admitted) { attempts++; }
        return admitted;
    }

    public void tick(long now) {
        frozen.observe();
        coordinator.tick(now);
        if (!drained && queuedAt >= 0 && now - queuedAt >= 40) {
            helper.assertTrue(coordinator.commitPreparations(), "Ordinary ledger admission did not start"); drained = true;
        }
    }
    public boolean done() { return acknowledged != null; }
    public void assertAcknowledged() {
        helper.assertTrue(Boolean.TRUE.equals(acknowledged) && ordinaryAcknowledged && attempts == 1 && captures == 1,
                "Governed proof/checked ARM did not finish in one attempt");
        helper.assertTrue(data.ledger().armedFlights().size() == 1 && data.ledger().issuedLaunchSerial() == 1,
                "The exact checked ARM acknowledgement is missing");
    }
    public RocketEntity rocket() { return rocket; }
    @Override public void close() {
        coordinator.close(); if (proof != null) { proof.release(); }
        rocket.discard(); backlog.bank().discard(); helper.getLevel().setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
