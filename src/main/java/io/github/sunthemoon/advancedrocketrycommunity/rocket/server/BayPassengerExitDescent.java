package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/** A first-contact offer, narrowed to one synchronous exact-FALL attempt. No immunity or fall-event changes. */
final class BayPassengerExitDescent {
    enum Contact { AIR, ORDINARY, UNSAFE, UNKNOWN }
    final BayPassengerExitPermit permit;
    final BayPassengerExitMotion.Generation generation;
    final BayPassengerExitMotion.Continuity continuity;
    final BayPassengerExitMotion.CorrectionWindow corrections;
    final long deadline;
    boolean closed, token;
    long landingTick;

    BayPassengerExitDescent(BayPassengerExitPermit permit, BayPassengerExitMotion.Generation generation, long deadline) {
        this.permit = permit; this.generation = generation; this.deadline = deadline;
        continuity = new BayPassengerExitMotion.Continuity(BayPassengerExitMotion.sample(permit.player, permit.server.getTickCount()));
        corrections = new BayPassengerExitMotion.CorrectionWindow(generation, continuity, deadline);
    }
    boolean valid(BayPassengerExitMotion observer) {
        return valid(observer, false);
    }
    private boolean valid(BayPassengerExitMotion observer, boolean committed) {
        return !closed && permit.authority() && permit.server.getTickCount() < deadline
                && corrections.observe(observer.generation(permit.player),
                        BayPassengerExitMotion.sample(permit.player, permit.server.getTickCount()), committed);
    }
    Contact contact(BayExitBudget budget, BayPassengerExitWorld world) {
        var allowance = budget.openProbe();
        if (allowance.isEmpty()) { return Contact.UNKNOWN; }
        ServerPlayer player = permit.player;
        var size = player.getDimensions(Pose.STANDING);
        double y = player.getY();
        if (Math.abs(y - Math.rint(y)) <= .01) { y = Math.rint(y); }
        var box = new Box(new Position(world.level(), player.getX(), y, player.getZ()), size.width, size.height);
        try (var probe = allowance.orElseThrow()) {
            world.contact = false; world.inspected = false;
            var verdict = BayExitSafety.loaded(world).inspect(box, probe, (query, used) -> true);
            return verdict == BayExitSafety.Verdict.UNPROVED || !world.inspected ? Contact.UNKNOWN
                    : verdict == BayExitSafety.Verdict.SAFE ? Contact.ORDINARY
                    : world.contact ? Contact.UNSAFE : Contact.AIR;
        }
    }
    void sample(BayPassengerExitMotion observer, BayExitBudget budget, BayPassengerExitWorld world, boolean committed) {
        if (token || !valid(observer, committed) || contact(budget, world) != Contact.AIR) { close(); }
    }
    void fall(LivingFallEvent event, BayPassengerExitMotion observer, BayPassengerExitLanding landing,
              BayExitBudget budget, BayPassengerExitWorld world) {
        var previous = continuity.last;
        if (token || !valid(observer) || !previous.airborne() || previous.velocity().y > 0
                || !landing.ordinaryStack() || event.isCanceled() || event.getDistance() <= 3 || event.getDamageMultiplier() <= 0
                || contact(budget, world) != Contact.ORDINARY) { close(); return; }
        token = true; landingTick = permit.server.getTickCount();
    }
    boolean attack(LivingAttackEvent event, BayPassengerExitMotion observer, BayPassengerExitLanding landing,
                   BayExitBudget budget, BayPassengerExitWorld world) {
        boolean cancel = token && !event.isCanceled() && event.getAmount() > 0 && event.getSource().is(DamageTypes.FALL)
                && landingTick == permit.server.getTickCount() && valid(observer) && landing.attackStack()
                && contact(budget, world) == Contact.ORDINARY;
        close();
        return cancel;
    }
    void close() { closed = true; token = false; }
}
