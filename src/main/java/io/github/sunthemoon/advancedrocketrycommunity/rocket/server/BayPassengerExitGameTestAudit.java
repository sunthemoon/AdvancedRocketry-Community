package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Bounded fixture diagnostics; no production hooks or observer changes. */
final class BayPassengerExitGameTestAudit {
    private BayPassengerExitGameTestAudit() { }
    static String difference(Object before, Object after) {
        var left = (Map<?, ?>) before; var right = (Map<?, ?>) after;
        var changed = new TreeMap<String, String>();
        left.forEach((key, value) -> { if (!value.equals(right.get(key))) { changed.put(key.toString(), value + " -> " + right.get(key)); } });
        right.forEach((key, value) -> { if (!left.containsKey(key)) { changed.put(key.toString(), "absent -> " + value); } });
        return changed.toString();
    }
    static void firstTickLanding(BayPassengerExitGameTestFixture fixture) {
        var player = fixture.player; var helper = fixture.helper;
        double target = Math.floor(player.getY()) - 3;
        float distance = (float) (player.getY() - target);
        helper.assertTrue(distance > 3 && distance <= 4, "First-tick descent did not fit its unchanged allowance");
        fixture.pad(BlockPos.containing(player.getX(), target, player.getZ()));
        player.setOnGround(false); player.setPos(player.getX(), target, player.getZ());
        player.fallDistance = distance;
        player.doCheckFallDamage(0, -distance, 0, true);
        helper.assertTrue(player.getHealth() == 20 && fixture.falls == 1
                && fixture.attacks.stream().filter(net.minecraftforge.event.entity.living.LivingAttackEvent::isCanceled).count() == 1,
                "First-tick ordinary landing did not consume exactly one FALL cancellation");
        helper.assertTrue(fixture.fallDistance == distance && fixture.multiplier == 1 && fixture.offer.descent.closed,
                "First-tick landing changed the source-less event or kept its capability");
        player.invulnerableTime = 0; player.causeFallDamage(distance, 1, player.damageSources().fall());
        helper.assertTrue(player.getHealth() < 20, "A second first-tick fall was protected");
    }
}
