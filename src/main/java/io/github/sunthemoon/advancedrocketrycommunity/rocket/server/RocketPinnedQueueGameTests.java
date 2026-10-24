package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPinnedQueueGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketPinnedQueueGameTests {
    private RocketPinnedQueueGameTests() { }

    @GameTest(template = "rocket_test", batch = "ordinary_retirement_pinned_queue", timeoutTicks = 400)
    public static void twoLogicalRocketsShareASlotThroughCheckedArmAndFreshOrdinaryRetirement(GameTestHelper helper) {
        new RocketSourceRetirementGameTests.Case(helper) {
            EscrowPinnedQueueGameTestSupport consumer;
            boolean began, returned, acknowledged;
            int superseded;
            void act() {
                source = flight.spawnCountdown(helper); prepare(true);
                // Distinct positions and logical IDs, but exactly the same physical entity slot.
                var at = new BlockPos((origin.getX() & ~15) + 8, origin.getY() + 12, (origin.getZ() & ~15) + 8);
                consumer = new EscrowPinnedQueueGameTestSupport(helper, at);
                helper.assertTrue(consumer.rocket().chunkPosition().equals(source.chunkPosition())
                        && !consumer.rocket().assemblyTransactionId().equals(source.assemblyTransactionId()), "Not two same-slot logical rockets");
                superseded = EscrowRuntime.of(server()).barrier().supersedingSaves();
            }
            boolean before() {
                long now = server().getTickCount();
                if (!began) { began = consumer.begin(now); return false; }
                if (!returned) {
                    helper.assertTrue(service.failBackToSource(server(), journal, record, source,
                            RocketTransferReturnReason.COUNTDOWN_CANCELLED, completion()), "Ordinary cancellation refused");
                    assertReturnedAndHeld(true); returned = true;
                }
                if (!acknowledged) {
                    helper.assertTrue(recovery().retirements().attempts(record.logicalRocketId()) == 0,
                            "Ordinary capture preceded the checked ARM acknowledgement");
                    helper.assertTrue(journal.find(record.transferId()).orElse(null) == record && accepted.get() == 0,
                            "Ordinary T disappeared before its own proof");
                    consumer.tick(now);
                    if (!consumer.done()) { return false; }
                    consumer.assertAcknowledged(); acknowledged = true;
                }
                return true;
            }
            void retired() {
                consumer.assertAcknowledged();
                helper.assertTrue(EscrowRuntime.of(server()).barrier().supersedingSaves() == superseded,
                        "Ordinary traffic invalidated the governed proof");
                helper.assertTrue(consumer.rocket().isAlive(), "Ordinary retirement removed the unrelated governed rocket");
            }
            void restore() { if (consumer != null) { consumer.close(); } }
        }.start();
    }
}
