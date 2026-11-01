package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** Check that a negative landing/expiry test reaches its own stimulus with a live offer. */
final class BayPassengerExitGameTestDescentAudit {
    private BayPassengerExitGameTestDescentAudit() { }

    static void beforeStimulus(BayPassengerExitGameTestFixture fixture) {
        if (fixture.offer == null || fixture.offer.descent == null) { return; }
        var descent = fixture.offer.descent;
        int tick = fixture.player.getServer().getTickCount();
        if (fixture.stage != 2 && (fixture.stage != 4 || tick >= fixture.firstDeadline)) { return; }
        var observed = fixture.exits.motion.generation(fixture.player);
        fixture.helper.assertTrue(fixture.exits.state.live.get(fixture.player) == fixture.offer && !descent.closed,
                "Offer ended before named stimulus: case=" + fixture.scenario + "; tick=" + tick
                        + "; deadline=" + fixture.firstDeadline + "; authority=" + fixture.offer.permit.authority()
                        + "; expected=" + descent.generation + "; observed=" + observed
                        + "; last=" + descent.continuity.last + "; current="
                        + BayPassengerExitMotion.sample(fixture.player, tick)
                        + "; contact=" + fixture.offer.world.firstContact + "; usage=" + fixture.exits.budget.usage());
        fixture.helper.assertTrue(descent.deadline == fixture.firstDeadline
                        && fixture.exits.state.history(fixture.player.getUUID(), fixture.flight.record().transferId()).deadline() == fixture.firstDeadline,
                "Observation renewed the original deadline");
        if (fixture.stage == 4) {
            fixture.helper.assertTrue(fixture.attacks.stream().noneMatch(LivingAttackEvent::isCanceled),
                    "Expiry case consumed protection before its deadline");
        }
    }
}
