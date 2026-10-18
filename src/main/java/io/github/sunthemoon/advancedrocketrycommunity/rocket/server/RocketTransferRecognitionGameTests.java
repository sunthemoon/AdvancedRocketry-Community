package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRecoveryFence;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferRecognitionFixtures.Change;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W11c leaf C-I (W11CD-DESIGN section 2.5), A1: the production recognition sites over real entities, the real escrow
 * ledger read and the real fence state adapter, with explicit before and after snapshot fixtures. No cargo device, ledger
 * write, journal entry or recovery action is involved, and nothing here logs a warning.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketTransferRecognitionGameTests {
    private RocketTransferRecognitionGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_transfer_recognition")
    public static void cargoChangedLandedRocketIsRecognizedOnlyUnderTheGroupedRule(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(level.dimension() == Level.OVERWORLD, "The fixtures record an overworld destination");
        var data = ResourceEscrowSavedData.forTests(level.getServer());
        helper.assertTrue(ResourceEscrowSavedData.existing(level.getServer()) == data, "Recognition must read the test ledger");
        var published = new ArrayList<RocketEntity>();
        try {
            legacy(helper, data.ledger(), origin(helper, 1), published);
            governed(helper, data.ledger().id(), origin(helper, 4), published);
        } finally {
            published.forEach(Entity::discard);
        }
        helper.succeed();
    }

    private static void legacy(GameTestHelper helper, EscrowLedger ledger, RocketPosition origin, List<RocketEntity> published) {
        var level = helper.getLevel(); var server = level.getServer();
        var record = RocketTransferRecognitionFixtures.legacy(origin);
        var recorded = record.destinationSnapshot();
        var after = RocketTransferRecognitionFixtures.changed(recorded, Change.CARGO);
        UUID named = record.destinationEntityId().orElseThrow();
        var neutral = RocketEscrowSection.neutral();
        var bound = new RocketEscrowSection(ledger.id(), 1, List.of());

        // isCommittedAuthority, unpublished: owner, logical and physical identity hold; only the snapshot and section vary.
        authority(helper, record, rocket(level, named, recorded, record, neutral), true, "the exact recorded snapshot");
        authority(helper, record, rocket(level, named, after, record, bound), true, "cargo bound to the current ledger");
        authority(helper, record, rocket(level, named, after, record, neutral), false, "neutral cargo");
        authority(helper, record, rocket(level, named, after, record, new RocketEscrowSection(ledger.id(), 0, List.of())),
                false, "cargo at epoch 0");
        authority(helper, record, rocket(level, named, after, record, new RocketEscrowSection(UUID.randomUUID(), 1, List.of())),
                false, "cargo bound to a foreign ledger");
        authority(helper, record, rocket(level, named, RocketTransferRecognitionFixtures.reissued(recorded), record, bound),
                false, "the same content under another snapshot ID");
        authority(helper, record, rocket(level, named, RocketTransferRecognitionFixtures.reissued(after), record, bound),
                false, "the same structure under another snapshot ID");
        for (var change : List.of(Change.GEOMETRY, Change.STATE, Change.ADAPTER)) {
            authority(helper, record, rocket(level, named, RocketTransferRecognitionFixtures.changed(recorded, change), record, bound),
                    false, change.name());
        }

        // Recovery's matches path over the real entity index, then the unchanged fence on the real state adapter.
        var changed = publish(level, named, after, record, new RocketEscrowSection(ledger.id(), 2, List.of()), published);
        var staleNeutral = publish(level, UUID.randomUUID(), recorded, record, neutral, published);
        publish(level, UUID.randomUUID(), after, record, neutral, published);
        publish(level, UUID.randomUUID(), after, record, new RocketEscrowSection(UUID.randomUUID(), 3, List.of()), published);
        var matches = RocketTransferEntities.loadedMatches(server, record, true);
        helper.assertTrue(matches.equals(List.of(changed, staleNeutral)),
                "Election must list the changed named authority, then the stale copy, and no neutral or foreign copy: " + matches);
        helper.assertTrue(RocketTransferEntities.loadedMatches(server, record, false).isEmpty(), "The unloaded source end matched");
        helper.assertTrue(reason(ledger, record, RocketTransferRecoveryAction.KEEP_DESTINATION, matches) == null,
                "The neutral stale copy must stay discardable behind the changed authority");
        helper.assertTrue("FLOOR_CONFLICT".equals(reason(ledger, record, RocketTransferRecoveryAction.KEEP_SOURCE, matches)),
                "A recognized protected destination became a discarded candidate");
        var preferringStale = record.rebindDestination(staleNeutral.getUUID());
        var stale = RocketTransferEntities.loadedMatches(server, preferringStale, true);
        helper.assertTrue(stale.equals(List.of(staleNeutral, changed)), "Election did not prefer the named stale copy: " + stale);
        helper.assertTrue("FLOOR_CONFLICT".equals(reason(ledger, preferringStale, RocketTransferRecoveryAction.KEEP_DESTINATION, stale)),
                "A stale exact-hash copy displaced the changed protected copy");
        var staleProtected = publish(level, UUID.randomUUID(), recorded, record, bound, published);
        var three = RocketTransferEntities.loadedMatches(server, record, true);
        helper.assertTrue(three.size() == 3 && three.get(0) == changed && three.contains(staleProtected),
                "The protected stale copy was not listed: " + three);
        helper.assertTrue("FLOOR_CONFLICT".equals(reason(ledger, record, RocketTransferRecoveryAction.KEEP_DESTINATION, three)),
                "A protected non-first keeper did not veto duplicate removal");
    }

    private static void governed(GameTestHelper helper, UUID ledger, RocketPosition origin, List<RocketEntity> published) {
        var level = helper.getLevel(); var server = level.getServer();
        var record = RocketTransferRecognitionFixtures.governed(origin);
        var after = RocketTransferRecognitionFixtures.changed(record.destinationSnapshot(), Change.CARGO);
        var destination = publish(level, record.destinationEntityId().orElseThrow(), after, record,
                new RocketEscrowSection(ledger, 1, List.of()), published);
        helper.assertTrue(RocketTransferEntities.isCommittedAuthority(destination, record), "The governed fixture was not recognized");
        var matches = RocketTransferEntities.loadedMatches(server, record, true);
        helper.assertTrue(matches.equals(List.of(destination)), "Governed election differs: " + matches);
        // Its ARMED entry is absent from the live ledger: still routed and held, whatever matches.
        var journal = new RocketTransferSavedData(); journal.put(record);
        var fences = new RocketTransferFences();
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        helper.assertTrue(!fences.permit(server, journal, record, live, settled), "A governed record was permitted legacy recovery");
        for (var action : RocketTransferRecoveryAction.values()) {
            helper.assertTrue(!fences.actionAllowed(server, journal, record, action, List.of(), matches, live, settled),
                    "A governed record allowed " + action);
        }
        helper.assertTrue(RocketTransferRecoveryService.step(List.of(record), live, settled, fences.ids(), 0, candidate -> {
            throw new IllegalStateException("A governed record entered legacy readiness");
        }).candidate() == null, "A governed record was offered to legacy recovery");
    }

    private static void authority(GameTestHelper helper, RocketTransferRecord record, RocketEntity rocket, boolean expected,
                                  String fixture) {
        helper.assertTrue(RocketTransferEntities.isCommittedAuthority(rocket, record) == expected
                && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record) == expected,
                "Committed authority for " + fixture + " must be " + expected);
    }

    private static String reason(EscrowLedger ledger, RocketTransferRecord record, RocketTransferRecoveryAction action,
                                 List<RocketEntity> destinations) {
        return EscrowRecoveryFence.reason(ledger, record.logicalRocketId(), record.transferId(), action, List.of(),
                destinations.stream().map(RocketTransferFences::state).toList());
    }

    private static RocketPosition origin(GameTestHelper helper, int offset) {
        var position = helper.absolutePos(new BlockPos(offset, 2, offset));
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }

    private static RocketEntity publish(ServerLevel level, UUID id, RocketStructureSnapshot snapshot, RocketTransferRecord record,
                                        RocketEscrowSection section, List<RocketEntity> published) {
        var rocket = rocket(level, id, snapshot, record, section);
        if (!level.addFreshEntity(rocket)) { throw new IllegalStateException("Recognition fixture was not published"); }
        published.add(rocket);
        return rocket;
    }

    /** A landed rocket of the record's owner and logical rocket, with the given snapshot and escrow section. */
    private static RocketEntity rocket(ServerLevel level, UUID id, RocketStructureSnapshot snapshot, RocketTransferRecord record,
                                       RocketEscrowSection section) {
        var rocket = ModEntities.ROCKET.get().create(level);
        if (rocket == null) { throw new IllegalStateException("No recognition fixture entity"); }
        rocket.setUUID(id);
        rocket.initializeTransferred(snapshot, record.logicalRocketId(), record.ownerId(), record.destinationFlightData().land(170));
        var tag = rocket.saveWithoutId(new CompoundTag());
        tag.getCompound("RocketEntityData").put("resource_escrow", section.encode());
        rocket.load(tag);
        if (!rocket.operational() || !rocket.escrowSection().equals(section)) {
            throw new IllegalStateException("Recognition fixture section was not installed");
        }
        return rocket;
    }
}
