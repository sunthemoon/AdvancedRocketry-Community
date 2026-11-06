package io.github.sunthemoon.arceadaptertest;

import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

    /** Never reissue pending work; only an actual refusal or terminal result permits a fresh request. */
    static final class Submission {
        final Player player;
        final Entity rocket;
        boolean admitted;
        long nextAttempt;
        CompoundTag acceptedAuthority;
        String unexpectedAdmission;
        Submission(FakePlayer player, Entity rocket) { this.player = (Player) player; this.rocket = rocket; }
        boolean advance(GameTestHelper helper) {
            // thenWaitUntil retries assertion failures. Preserve the first refusal instead of
            // flooding the limiter and replacing its useful diagnosis with a silent null reply.
            helper.assertTrue(unexpectedAdmission == null, unexpectedAdmission);
            if (admitted) {
                if (!"validation.advancedrocketrycommunity.rocket.region_busy".equals(player.reply)) { return true; }
                helper.assertTrue(!rocket.isRemoved() && acceptedAuthority.equals(save(helper)),
                        "Rejected pending request changed D authority");
                LogUtils.getLogger().info("ARCE_ADAPTER_ADMISSION entity={} transition=PENDING_REJECTED tick={}",
                        rocket.getUUID(), helper.getTick());
                admitted = false;
                nextAttempt = helper.getTick() + (int) invoke("intentWindowTicks", new Class<?>[0]);
            }
            if (helper.getTick() < nextAttempt) { return false; }
            CompoundTag before = save(helper);
            player.reply = null; player.setPos(rocket.position()); player.setShiftKeyDown(true);
            try { rocket.interact(player, InteractionHand.MAIN_HAND); }
            finally { player.setShiftKeyDown(false); }
            LogUtils.getLogger().info("ARCE_ADAPTER_ADMISSION entity={} reply={} tick={}", rocket.getUUID(), player.reply, helper.getTick());
            if ("validation.advancedrocketrycommunity.rocket.disassembly_pending".equals(player.reply)) {
                acceptedAuthority = before;
                admitted = true; return true;
            }
            if (!"validation.advancedrocketrycommunity.rocket.region_busy".equals(player.reply)) {
                unexpectedAdmission = "Unexpected disassembly admission result: " + player.reply;
                if ("validation.advancedrocketrycommunity.rocket.target_occupied".equals(player.reply)) {
                    unexpectedAdmission += "; restore targets=" + describeTargets(helper, before);
                }
                helper.assertTrue(false, unexpectedAdmission);
            }
            helper.assertTrue(!rocket.isRemoved() && before.equals(save(helper)), "Refused request changed D authority");
            nextAttempt = helper.getTick() + (int) invoke("intentWindowTicks", new Class<?>[0]);
            return false;
        }
        private CompoundTag save(GameTestHelper helper) {
            var root = new CompoundTag(); helper.assertTrue(rocket.save(root), "Admission D did not serialize");
            return root.getCompound("RocketEntityData");
        }
        private String describeTargets(GameTestHelper helper, CompoundTag data) {
            var snapshot = data.getCompound("snapshot");
            int[] xyz = snapshot.getIntArray("source_origin");
            var origin = new BlockPos(xyz[0], xyz[1], xyz[2]);
            var targets = new java.util.ArrayList<String>();
            for (var tag : snapshot.getList("relative_blocks", Tag.TAG_COMPOUND)) {
                int[] offset = ((CompoundTag) tag).getIntArray("position");
                var at = origin.offset(offset[0], offset[1], offset[2]);
                targets.add(at.toShortString() + "=" + (helper.getLevel().hasChunkAt(at)
                        ? helper.getLevel().getBlockState(at) + " fluid=" + helper.getLevel().getFluidState(at)
                        : "unloaded"));
            }
            return targets.toString();
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
