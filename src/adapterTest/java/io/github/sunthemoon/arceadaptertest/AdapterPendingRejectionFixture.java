package io.github.sunthemoon.arceadaptertest;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestHelper;

/** Supersede the first pending request's entity proof with real vanilla saves, without refreshing the request. */
final class AdapterPendingRejectionFixture {
    private final GameTestHelper helper;
    private AdapterAsyncFixture.Submission submission;
    private long admittedAt;
    private int saves;
    private boolean rejected;

    AdapterPendingRejectionFixture(GameTestHelper helper) {
        this.helper = helper;
        helper.onEachTick(this::tick);
    }

    void arm(AdapterAsyncFixture.Submission submission) {
        this.submission = submission;
        admittedAt = helper.getTick();
    }

    private void tick() {
        if (submission == null || rejected) { return; }
        if ("validation.advancedrocketrycommunity.rocket.region_busy".equals(submission.player.reply)) {
            LogUtils.getLogger().info("ARCE_ADAPTER_PENDING_SUPERSESSION entity={} rejected_age={} saves={}",
                    submission.rocket.getUUID(), helper.getTick() - admittedAt, saves);
            // The manager expires age 201 at END; GameTest observes that reply on the following tick.
            helper.assertTrue(helper.getTick() - admittedAt == 202,
                    "The pending request's age-201 rejection was not observed on the following test tick");
            rejected = true;
            return;
        }
        helper.assertTrue(!submission.rocket.isRemoved(), "The superseded pending request removed D");
        helper.assertTrue(helper.getLevel().entityManager.storeChunkSections(
                        submission.rocket.chunkPosition().toLong(), entity -> { }),
                "The superseding vanilla entity save was refused");
        saves++;
    }

    void assertRejected() {
        helper.assertTrue(rejected && saves > 0, "The first pending request was not rejected after superseding saves");
    }
}
