package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNotice;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeService;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeSummary;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-065 section 5.3 on the real server: warp notices as code tokens, sent to the online owner and members and to
 * visitors inside the station's region but to nobody elsewhere; the offline member summary shown once at login; a
 * removed member's acknowledgement gone from the saved root; and a refused root kept while live notices continue.
 * The countdown is counted in server ticks, as in StationWarpGameTests; nothing waits for loading.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationNoticeGameTests {
    private static final String WHILE_AWAY = StationWarpGameTests.NOTICE + "while_away";

    private StationNoticeGameTests() {
    }

    @GameTest(template = "empty", batch = "station_notice_delivery", timeoutTicks = 400)
    public static void warpNoticesReachMembersAndVisitorsInsideTheRegionOnly(GameTestHelper helper) {
        StationWarpGameTests.Fixture alpha = new StationWarpGameTests.Fixture(helper, "Notice alpha",
                CelestialIds.EARTH_ID, 2_500_000);
        StationWarpGameTests.Fixture beta = new StationWarpGameTests.Fixture(helper, "Notice beta",
                CelestialIds.EARTH_ID, 0);
        StationRocketAuthority previous = alpha.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        boolean scheduled = false;
        try {
            alpha.core(alpha.pad.east(2));
            alpha.join(alpha.ownerId, "noticeOwner");
            UUID memberId = UUID.randomUUID();
            alpha.data.invite(alpha.id(), memberId);
            alpha.data.acceptInvitation(alpha.id(), memberId);
            // A member elsewhere (inside the other station), a visitor without access inside this region, a stranger
            // inside the other region and a player in the gap between them.
            alpha.join(memberId, "noticeMember", beta.pad, false);
            UUID visitorId = UUID.randomUUID();
            alpha.join(visitorId, "noticeVisitor", alpha.pad.west(3), false);
            UUID strangerId = UUID.randomUUID();
            alpha.join(strangerId, "noticeStranger", beta.pad.east(3), false);
            UUID gapId = UUID.randomUUID();
            alpha.join(gapId, "noticeGap", alpha.pad.east(StationLimits.REGION_SIZE / 2 + 32), false);
            alpha.look(alpha.ownerId, alpha.pad.east(2));
            alpha.run(alpha.ownerId, "arce station warp " + StationWarpGameTests.MOON);
            alpha.run(alpha.ownerId, "arce station warp confirm " + alpha.id());
            String started = StationWarpGameTests.notice("warp_started", "Notice alpha",
                    StationWarpGameTests.code(StationManagementCode.WARP_STARTED), CelestialIds.EARTH_ID,
                    StationWarpGameTests.MOON, 10, StationWarpGameTests.warpClass("in_system"), 2_000_000);
            String committed = StationWarpGameTests.notice("warp_committed", "Notice alpha",
                    StationWarpGameTests.code(StationManagementCode.WARP_COMMITTED), CelestialIds.EARTH_ID,
                    StationWarpGameTests.MOON, StationWarpGameTests.warpClass("in_system"), 2_000_000, 500_000);
            List<String> expected = new ArrayList<>(List.of(started));
            for (int seconds : List.of(10, 5, 3, 2, 1)) {
                expected.add(ConnectedTestPlayers.ACTION_BAR + StationWarpGameTests.notice("warp_countdown",
                        "Notice alpha", StationWarpGameTests.MOON, seconds));
            }
            expected.add(committed);
            scheduled = true;
            helper.runAfterDelay(StationWarpGameTests.COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(alpha.station().orbitBody().equals(StationWarpGameTests.MOON),
                            "The warp did not commit");
                    for (UUID told : List.of(alpha.ownerId, memberId, visitorId)) {
                        helper.assertTrue(alpha.notices(told).equals(expected),
                                "Notices differ for " + told + ": " + alpha.notices(told));
                    }
                    for (UUID untold : List.of(strangerId, gapId)) {
                        helper.assertTrue(alpha.notices(untold).isEmpty(),
                                "A player outside the region was told: " + alpha.notices(untold));
                    }
                    // Hidden-region privacy: the visitor's exact notices above carry no ID, and nothing at all
                    // the visitor received names a member, the owner or either station.
                    List<String> everything = alpha.tokens.get(visitorId);
                    for (UUID hidden : List.of(memberId, alpha.ownerId, alpha.id(), beta.id(), beta.ownerId)) {
                        helper.assertTrue(everything.stream().noneMatch(token -> token.contains(hidden.toString())),
                                "The visitor learned " + hidden + ": " + everything);
                    }
                    StationNoticeSummary summary = StationNoticeSavedData.get(alpha.server).summary(alpha.id());
                    helper.assertTrue(summary.revision() == 1L && summary.outcome().map(outcome -> ConnectedTestPlayers
                                    .token(StationNoticeService.component(outcome))).filter(committed::equals)
                                    .isPresent(), "The commit was not kept as the summary: " + summary);
                    helper.assertTrue(summary.players().equals(Set.of(alpha.ownerId, memberId)),
                            "Only the owner and the member told live are acknowledged: " + summary);
                } finally {
                    alpha.warp.installRocketAuthority(previous);
                    alpha.close();
                    beta.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                alpha.warp.installRocketAuthority(previous);
                alpha.close();
                beta.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_notice_login", timeoutTicks = 400)
    public static void anOfflineMemberSeesTheLatestOutcomeOnceAndARemovedMemberNothing(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Notice login",
                CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        UUID offlineId = UUID.randomUUID();
        UUID removedId = UUID.randomUUID();
        boolean scheduled = false;
        try {
            for (UUID member : List.of(offlineId, removedId)) {
                fixture.data.invite(fixture.id(), member);
                fixture.data.acceptInvitation(fixture.id(), member);
            }
            fixture.core(fixture.pad.east(2));
            fixture.join(fixture.ownerId, "loginOwner");
            fixture.join(removedId, "loginRemoved");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + StationWarpGameTests.MOON);
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            String committed = StationWarpGameTests.notice("warp_committed", "Notice login",
                    StationWarpGameTests.code(StationManagementCode.WARP_COMMITTED), CelestialIds.EARTH_ID,
                    StationWarpGameTests.MOON, StationWarpGameTests.warpClass("in_system"), 2_000_000, 500_000);
            scheduled = true;
            helper.runAfterDelay(StationWarpGameTests.COMMIT_DELAY, () -> {
                try {
                    StationNoticeSavedData notices = StationNoticeSavedData.get(fixture.server);
                    StationNoticeSummary summary = notices.summary(fixture.id());
                    helper.assertTrue(summary.revision() == 1L
                                    && summary.acknowledged(fixture.ownerId).equals(OptionalLong.of(1L))
                                    && summary.acknowledged(removedId).equals(OptionalLong.of(1L))
                                    && summary.acknowledged(offlineId).isEmpty(),
                            "Only the members told live are acknowledged: " + summary);
                    // The offline member sees the latest outcome once at login, and the server records it.
                    String whileAway = WHILE_AWAY + "(" + committed + ")";
                    fixture.join(offlineId, "loginOffline");
                    helper.assertTrue(fixture.notices(offlineId).equals(List.of(whileAway)),
                            "The login notice differs: " + fixture.notices(offlineId));
                    helper.assertTrue(notices.summary(fixture.id()).acknowledged(offlineId)
                            .equals(OptionalLong.of(1L)), "The login was not acknowledged");
                    fixture.leave(offlineId);
                    fixture.join(offlineId, "loginOffline");
                    helper.assertTrue(fixture.notices(offlineId).equals(List.of(whileAway)),
                            "The outcome was shown twice: " + fixture.notices(offlineId));
                    // Removed-member privacy: the acknowledgement leaves the saved root with the membership.
                    StationRuntime.manager().orElseThrow().removeMember(fixture.server, fixture.ownerId, false,
                            fixture.id(), removedId);
                    helper.assertTrue(!notices.summary(fixture.id()).players().contains(removedId)
                                    && !contains(notices.save(new CompoundTag()), removedId),
                            "The removed member's acknowledgement is still saved");
                    fixture.leave(removedId);
                    new StationNoticeService().deliverOutcome(fixture.server, fixture.id(), fixture.ownerId,
                            StationNotice.aborted("Notice login", StationManagementCode.WARP_CANCELLED,
                                    StationWarpGameTests.MOON));
                    helper.assertTrue(notices.summary(fixture.id()).revision() == 2L,
                            "The new outcome did not replace the summary in place");
                    fixture.join(removedId, "loginRemoved");
                    helper.assertTrue(fixture.notices(removedId).stream().noneMatch(token -> token.startsWith(WHILE_AWAY)),
                            "A removed member was shown an outcome: " + fixture.notices(removedId));
                    // A restart reads the same summary back.
                    helper.assertTrue(StationNoticeSavedData.load(notices.save(new CompoundTag()))
                                    .summary(fixture.id()).equals(notices.summary(fixture.id())),
                            "The saved summary does not read back");
                } finally {
                    fixture.warp.installRocketAuthority(previous);
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.warp.installRocketAuthority(previous);
                fixture.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_notice_refused", timeoutTicks = 100)
    public static void aRefusedNoticeRootIsKeptWhileLiveNoticesContinue(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Notice refused",
                CelestialIds.EARTH_ID, 0);
        var storage = fixture.server.overworld().getDataStorage();
        StationNoticeSavedData original = StationNoticeSavedData.get(fixture.server);
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.put("stations", new ListTag());
        StationNoticeSavedData refused = StationNoticeSavedData.load(future);
        try {
            storage.set(StationNoticeSavedData.NAME, refused);
            UUID memberId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            fixture.join(fixture.ownerId, "refusedOwner");
            new StationNoticeService().deliverOutcome(fixture.server, fixture.id(), fixture.ownerId,
                    StationNotice.aborted("Notice refused", StationManagementCode.WARP_CANCELLED,
                            StationWarpGameTests.MOON));
            helper.assertTrue(fixture.notices(fixture.ownerId).equals(List.of(StationWarpGameTests.notice(
                            "warp_aborted", "Notice refused", StationWarpGameTests.code(StationManagementCode.WARP_CANCELLED),
                            StationWarpGameTests.MOON))), "Live notices stopped: " + fixture.notices(fixture.ownerId));
            fixture.join(memberId, "refusedMember");
            helper.assertTrue(fixture.notices(memberId).isEmpty(), "An unavailable store showed an outcome");
            helper.assertTrue(!refused.operational() && !refused.isDirty()
                            && refused.save(new CompoundTag()) == future && future.getInt("schema_version") == 2,
                    "The refused root was not kept verbatim");
        } finally {
            storage.set(StationNoticeSavedData.NAME, original);
            fixture.close();
        }
        helper.assertTrue(StationNoticeSavedData.get(fixture.server) == original, "The store was not restored");
        helper.succeed();
    }

    /** Whether any int array in the tree is exactly {@code player}'s UUID. */
    private static boolean contains(Tag tag, UUID player) {
        if (tag instanceof net.minecraft.nbt.IntArrayTag array) {
            return java.util.Arrays.equals(array.getAsIntArray(), NbtUtils.createUUID(player).getAsIntArray());
        }
        if (tag instanceof CompoundTag compound) {
            return compound.getAllKeys().stream().anyMatch(key -> contains(compound.get(key), player));
        }
        return tag instanceof ListTag list && list.stream().anyMatch(element -> contains(element, player));
    }
}
