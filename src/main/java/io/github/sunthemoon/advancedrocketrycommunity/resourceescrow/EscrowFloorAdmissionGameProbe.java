package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** R244 command regression, run on the existing native floor-retirement fixture before its ordinary preview. */
final class EscrowFloorAdmissionGameProbe {
    private EscrowFloorAdmissionGameProbe() { }

    static void verify(MinecraftServer server, UUID transfer) {
        EscrowTestEndpoints.requireEnabled(server);
        var runtime = EscrowRuntime.of(server); var data = ResourceEscrowSavedData.existing(server);
        var coordinator = data.bind(server); var authority = coordinator.admissionAuthority();
        var record = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server)
                .find(transfer).orElseThrow();
        var before = EscrowCodec.encode(data.ledger()); long sequence = coordinator.nextSequence();
        var previews = runtime.settlement().previewSnapshot();
        var rows = new ArrayList<EscrowAdmissionAuthority.Pending>();
        var rockets = new UUID[] { UUID.randomUUID(), UUID.randomUUID() };
        for (int n = 0; n < EscrowLimits.PER_OWNER; n++) {
            rows.add(new EscrowAdmissionAuthority.Pending(UUID.randomUUID(), record.ownerId(), rockets[n / 8], EscrowAdmissionAuthority.Consumer.BAY));
        }
        var index = new EscrowAdmissionAuthority.Store() {
            @Override public EscrowAdmissionAuthority.Index index() { return new EscrowAdmissionAuthority.Index(true, rows); }
            @Override public boolean holdsWork() { return false; }
        };
        EscrowTags.require(runtime.bayIndexForTests(server, index), "Floor command fixture could not install its complete BAY index");
        try {
            int count = authority.pendingCount(); int chunks = server.overworld().getChunkSource().getLoadedChunksCount();
            EscrowTags.require(runtime.settlement().floorAdmission(server, transfer) == EscrowAdmissionAuthority.Result.OWNER_PENDING_FULL,
                    "Floor preview did not charge the stored journal owner");
            int result = server.getCommands().getDispatcher().execute("arce escrow settle-floor " + transfer,
                    server.createCommandSourceStack().withPermission(2));
            EscrowTags.require(result == 0 && previews.equals(runtime.settlement().previewSnapshot())
                    && before.equals(EscrowCodec.encode(data.ledger())) && sequence == coordinator.nextSequence()
                    && coordinator.writerIdle() && authority.pendingCount() == count
                    && chunks == server.overworld().getChunkSource().getLoadedChunksCount(),
                    "Floor command refusal changed a preview, resource, count, writer or loaded chunk");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalStateException("Floor command fixture could not dispatch the preview", invalid);
        } finally {
            EscrowTags.require(runtime.bayIndexForTests(server, null), "Floor command fixture could not restore the BAY index");
        }
    }
}
