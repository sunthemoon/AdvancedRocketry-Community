package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinGuardGameTests {
    private RocketRejoinGuardGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_rejoin_guard")
    public static void joinProtectsDamageAndForceEffectsThenCleansOnlyGainedEffects(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var previous = RocketRejoinEvents.get(server);
        RocketRuntime.rejoinAdmissions(server); // Initialize the installed release owner before the fixture cache.
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "ARCEGuard"));
        player.getActiveEffectsMap().put(MobEffects.POISON, new MobEffectInstance(MobEffects.POISON, 600));
        var data = RocketRejoinSavedData.load(RocketRejoinCodec.encode(root(player.getUUID())));
        server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
        try {
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player);
            var guards = data.guards(server); var session = guards.session(player.getUUID());
            helper.assertTrue(session != null && RocketRejoinProtection.guarded(player), "Join did not install guard");
            guards.join(player);
            helper.assertTrue(guards.session(player.getUUID()) == session, "Login repeated the session/deadline");
            player.setHealth(1.0F);
            for (var source : List.of(player.damageSources().lava(), player.damageSources().inFire(),
                    player.damageSources().fellOutOfWorld(), player.damageSources().inWall(), player.damageSources().wither())) {
                helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(player, source, 100)), "Attack not canceled");
                helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, source, 100)), "Hurt not canceled");
                helper.assertTrue(MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, source, 100)), "Damage not canceled");
                player.hurt(source, 100);
                helper.assertTrue(player.isAlive() && player.getHealth() == 1.0F, "Guarded low-health player was damaged");
            }
            player.forceAddEffect(new MobEffectInstance(MobEffects.WITHER, 600), player);
            helper.assertTrue(!player.hasEffect(MobEffects.WITHER), "forceAddEffect bypassed Applicable denial");
            // R106 defence-in-depth route: mutate the public effect map without canBeAffected.
            player.getActiveEffectsMap().put(MobEffects.WITHER, new MobEffectInstance(MobEffects.WITHER, 600));
            player.setRemainingFireTicks(200); player.setTicksFrozen(200); player.fallDistance = 100;
            player.setDeltaMovement(2, -10, 3); player.setPos(session.position.add(4, 2, 0));
            guards.pin(player);
            helper.assertTrue(player.position().equals(session.position) && player.getDeltaMovement().equals(Vec3.ZERO)
                    && player.fallDistance == 0 && player.getRemainingFireTicks() <= 0 && player.getTicksFrozen() == 0,
                    "Guard did not pin/clear motion and environmental status");
            guards.complete(session);
            helper.assertTrue(!RocketRejoinProtection.guarded(player) && !player.hasEffect(MobEffects.WITHER)
                    && player.hasEffect(MobEffects.POISON), "Cleanup changed original effects or retained gained harm");
        } finally {
            if (player.connection != null) { server.getPlayerList().remove(player); }
            data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), previous);
        }
        helper.succeed();
    }

    static RocketRejoinRoot root(UUID player) {
        var release = UUID.randomUUID(); var version = new RocketRejoinRoot.Version("a".repeat(64), "PREPARED", null);
        var batch = new RocketRejoinRoot.Batch(release, 0, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RocketRejoinRoot.Hold.H1, "minecraft:overworld", new RocketRejoinRoot.Position(0, 64, 0),
                new RocketRejoinRoot.Position(-1, 64, -1), new RocketRejoinRoot.Position(1, 66, 1),
                new RocketRejoinRoot.Resources("b".repeat(64), "c".repeat(64)), version, version, List.of(player),
                List.of(new RocketRejoinRoot.Slot("minecraft:overworld", 0, 0)));
        return RocketRejoinRoot.empty().admit(batch, List.of(new RocketRejoinRoot.Entry(release, player, 0,
                RocketRejoinRoot.Placement.PENDING, RocketRejoinRoot.Seat.PENDING, null)));
    }
}
