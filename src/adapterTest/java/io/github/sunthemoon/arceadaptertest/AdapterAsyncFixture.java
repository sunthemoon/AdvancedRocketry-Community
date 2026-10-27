package io.github.sunthemoon.arceadaptertest;

import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;

/** Only timing facts/pacing cross the test bridge; requests, inventories and recovery remain native/public. */
final class AdapterAsyncFixture {
    private static final String ACCESS = "io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedAdapterTestAccess";

    static TestFunction test(String batch, String name, int members, int operations, boolean reload,
            Consumer<GameTestHelper> body) {
        return new TestFunction(batch, name.toLowerCase(java.util.Locale.ROOT), "advancedrocketrycommunity:rocket_test",
                Rotation.NONE, ticks(members, operations, reload), 0L, true, helper -> {
                    var pace = (Runnable) invoke("pacer", new Class<?>[] {ServerLevel.class}, helper.getLevel());
                    helper.onEachTick(pace);
                    body.accept(helper);
                });
    }

    static int ticks(int members, int operations, boolean reload) {
        return (int) invoke("completionTicks", new Class<?>[] {int.class, int.class, boolean.class}, members, operations, reload);
    }
    static int retryTicks() { return (int) invoke("retryTicks", new Class<?>[0]); }

    static final class Player extends FakePlayer {
        String reply;
        Player(ServerLevel level, GameProfile profile) { super(level, profile); }
        @Override public void displayClientMessage(Component message, boolean actionBar) {
            reply = message.getContents() instanceof TranslatableContents text ? text.getKey() : message.getString();
        }
    }

    /** A refused request has not started B2. Never reissue one already acknowledged as pending. */
    static final class Submission {
        final Player player;
        final Entity rocket;
        boolean admitted;
        long nextAttempt;
        Submission(FakePlayer player, Entity rocket) { this.player = (Player) player; this.rocket = rocket; }
        boolean advance(GameTestHelper helper) {
            if (admitted) { return true; }
            if (helper.getTick() < nextAttempt) { return false; }
            CompoundTag before = save(helper);
            player.reply = null; player.setPos(rocket.position()); player.setShiftKeyDown(true);
            try { rocket.interact(player, InteractionHand.MAIN_HAND); }
            finally { player.setShiftKeyDown(false); }
            LogUtils.getLogger().info("ARCE_ADAPTER_ADMISSION entity={} reply={} tick={}", rocket.getUUID(), player.reply, helper.getTick());
            if ("validation.advancedrocketrycommunity.rocket.disassembly_pending".equals(player.reply)) {
                admitted = true; return true;
            }
            helper.assertTrue("validation.advancedrocketrycommunity.rocket.region_busy".equals(player.reply),
                    "Unexpected disassembly admission result: " + player.reply);
            helper.assertTrue(!rocket.isRemoved() && before.equals(save(helper)), "Refused request changed D authority");
            nextAttempt = helper.getTick() + (int) invoke("intentWindowTicks", new Class<?>[0]);
            return false;
        }
        private CompoundTag save(GameTestHelper helper) {
            var root = new CompoundTag(); helper.assertTrue(rocket.save(root), "Admission D did not serialize");
            return root.getCompound("RocketEntityData");
        }
    }

    static boolean checkedAbsent(BlockEntity carrier) {
        try {
            return (boolean) Class.forName("io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedProductionTestAccess")
                    .getMethod("checkedAbsent", BlockEntity.class).invoke(null, carrier);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Carrier absence observation unavailable", failure); }
    }

    private static Object invoke(String name, Class<?>[] signature, Object... args) {
        try { return Class.forName(ACCESS).getMethod(name, signature).invoke(null, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Adapter fixture timing unavailable", failure); }
    }
    private AdapterAsyncFixture() { }
}
