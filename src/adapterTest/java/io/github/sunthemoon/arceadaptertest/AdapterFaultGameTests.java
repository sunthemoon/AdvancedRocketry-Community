package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Public-consumer faults reach the installed scanner/transaction, never an internal test registry. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdapterFaultGameTests {
    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> asynchronousTransitions() {
        return java.util.List.of(
                AdapterAsyncFixture.test("w164_adapter_success", "AdapterFaultGameTests.throwingEligibilityPreservesSource",
                        4, 1, false, AdapterFaultGameTests::throwingEligibilityPreservesSource),
                AdapterAsyncFixture.test("w164_adapter_success", "AdapterFaultGameTests.slowEligibilityPreservesSource",
                        4, 1, false, AdapterFaultGameTests::slowEligibilityPreservesSource),
                AdapterAsyncFixture.test("w164_adapter_success", "AdapterFaultGameTests.throwingCapturePreservesSource",
                        4, 1, false, AdapterFaultGameTests::throwingCapturePreservesSource),
                AdapterAsyncFixture.test("w164_adapter_success", "AdapterFaultGameTests.oversizedCapturePreservesSource",
                        4, 1, false, AdapterFaultGameTests::oversizedCapturePreservesSource),
                AdapterAsyncFixture.test("w164_adapter_success", "AdapterFaultGameTests.slowCapturePreservesSource",
                        4, 1, false, AdapterFaultGameTests::slowCapturePreservesSource),
                AdapterAsyncFixture.test("w164_adapter_fault", "AdapterFaultGameTests.throwingRestoreCleansPartialInventoryAndRetries",
                        4, 2, true, AdapterFaultGameTests::throwingRestoreCleansPartialInventoryAndRetries),
                AdapterAsyncFixture.test("w164_adapter_fault", "AdapterFaultGameTests.falseRestoreCleansPartialInventoryAndRetries",
                        4, 2, true, AdapterFaultGameTests::falseRestoreCleansPartialInventoryAndRetries),
                AdapterAsyncFixture.test("w164_adapter_fault", "AdapterFaultGameTests.slowRestoreCleansPartialInventoryAndRetries",
                        4, 2, true, AdapterFaultGameTests::slowRestoreCleansPartialInventoryAndRetries),
                AdapterAsyncFixture.test("w164_adapter_fault", "AdapterFaultGameTests.mismatchedRestoreCleansPartialInventoryAndRetries",
                        4, 2, true, AdapterFaultGameTests::mismatchedRestoreCleansPartialInventoryAndRetries));
    }
    private static final String HOST = "advancedrocketrycommunity";
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    private static final BlockPos CARGO = ORIGIN.east();
    private static final List<BlockPos> POSITIONS = List.of(ORIGIN, ORIGIN.above(), ORIGIN.above(2), CARGO);

    private AdapterFaultGameTests() { }

    public static void throwingEligibilityPreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAN_MOVE_THROW);
    }

    public static void slowEligibilityPreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAN_MOVE_SLOW);
    }

    public static void throwingCapturePreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAPTURE_THROW);
    }

    public static void oversizedCapturePreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAPTURE_OVERSIZED);
    }

    public static void slowCapturePreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAPTURE_SLOW);
    }

    public static void throwingRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_THROW);
    }

    public static void falseRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_FALSE);
    }

    public static void slowRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_SLOW);
    }

    public static void mismatchedRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_MISMATCH);
    }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void restoreScopeIsClearedWhenCallerThrows(GameTestHelper helper) {
        var probe = new FixtureAdapterFaults.Probe(FixtureAdapterFaults.Mode.RESTORE_FALSE);
        RuntimeException failure = new IllegalArgumentException("fixture caller failure");
        try {
            FixtureAdapterFaults.duringRestore(probe, () -> { throw failure; });
            helper.fail("Caller exception was swallowed");
        } catch (RuntimeException actual) {
            helper.assertTrue(actual == failure, "Caller exception changed");
        }
        helper.assertTrue(FixtureAdapterFaults.restoring() == null, "Restore scope leaked into another test");
        helper.succeed();
    }

    private static void scanFailure(GameTestHelper helper, FixtureAdapterFaults.Mode mode) {
        Fixture fixture = create(helper, mode == FixtureAdapterFaults.Mode.CAN_MOVE_SLOW ? AdapterTestMod.SLOW_MOVE_CONTAINER.get()
                : mode == FixtureAdapterFaults.Mode.CAPTURE_SLOW ? AdapterTestMod.SLOW_CAPTURE_CONTAINER.get() : AdapterTestMod.CONTAINER.get());
        var probe = new FixtureAdapterFaults.Probe(mode);
        AdapterAsyncFixture.Submission[] disassembly = new AdapterAsyncFixture.Submission[1];
        fixture.source.fault = probe;
        queue(helper, fixture.owner);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(report(helper).getString("code").equals("UNSUPPORTED_BLOCK_ENTITY"),
                        "Scanner did not report the failing public adapter: " + report(helper)))
                .thenExecute(() -> {
                    reached(helper, probe);
                    helper.assertTrue(!report(helper).getString("detail").contains(FixtureAdapterFaults.PRIVATE_DETAIL),
                            "Host diagnostic exposed provider exception detail");
                    helper.assertTrue(report(helper).getString("detail").equals("Rocket contains an unsupported block entity"),
                            "Player report is not the translated reason: " + report(helper));
                    String diagnostic = operatorReport(helper, fixture.owner);
                    helper.assertTrue(diagnostic.contains("code=UNSUPPORTED_BLOCK_ENTITY")
                                    && !diagnostic.contains(FixtureAdapterFaults.PRIVATE_DETAIL),
                            "Operator report lost the failure or exposed provider exception detail: " + diagnostic);
                    if (mode == FixtureAdapterFaults.Mode.CAPTURE_OVERSIZED) {
                        helper.assertTrue(diagnostic.endsWith(
                                        "detail=" + AdapterTestMod.CONTAINER_ID + ": RocketSnapshotException"),
                                "Oversized capture was not rejected by payload validation: " + diagnostic);
                    }
                    helper.assertTrue(rockets(helper).isEmpty(), "Failed scan created a rocket authority");
                    assertRestored(helper, fixture);
                    helper.assertTrue(helper.getBlockEntity(CARGO) == fixture.source, "Failed scan replaced its source inventory");
                    fixture.source.fault = null;
                    queue(helper, fixture.owner);
                })
                .thenWaitUntil(() -> helper.assertTrue(rockets(helper).size() == 1, "Healthy scan retry failed"))
                .thenExecute(() -> disassembly[0] = new AdapterAsyncFixture.Submission(fixture.owner, rockets(helper).get(0)))
                .thenWaitUntil(() -> helper.assertTrue(disassembly[0].advance(helper), "Healthy disassembly admission is busy"))
                // Observe terminal rejection before a fresh request; never refresh an accepted pending operation.
                .thenWaitUntil(() -> {
                    if (!rockets(helper).isEmpty()) { disassembly[0].advance(helper); }
                    assertRestored(helper, fixture);
                }).thenSucceed();
    }

    private static void restoreFailure(GameTestHelper helper, FixtureAdapterFaults.Mode mode) {
        var trial = new RestoreTrial(helper, mode);
        helper.runAtTickTime(AdapterAsyncFixture.ticks(POSITIONS.size(), 2, true) - 1, () -> {
            if (!trial.closed) { trial.close(); helper.fail("Unfinished adapter transition: " + trial.step); }
        });
        helper.onEachTick(() -> {
            if (trial.closed) { return; }
            try { trial.tick(); }
            catch (RuntimeException | Error failure) { trial.close(); throw failure; }
        });
    }

    private enum RestoreStep { ASSEMBLY, DISASSEMBLY_PENDING, PREPARATION_HELD, CARRIER_RELOADED,
        HEALTHY_REASSEMBLY, HEALTHY_DISASSEMBLY }

    /** R107 recovery, not DR: C owns resources by the time the detached restore callback is invoked. */
    private static final class RestoreTrial implements AutoCloseable {
        final GameTestHelper helper;
        final Fixture fixture;
        final FixtureAdapterFaults.Probe probe;
        RestoreStep step = RestoreStep.ASSEMBLY;
        Entity rocket;
        CompoundTag authority, retained;
        BlockEntity carrier;
        AutoCloseable fault;
        AdapterAsyncFixture.Submission submission;
        long heldAt;
        boolean closed;

        RestoreTrial(GameTestHelper helper, FixtureAdapterFaults.Mode mode) {
            this.helper = helper; fixture = create(helper); probe = new FixtureAdapterFaults.Probe(mode);
            queue(helper, fixture.owner);
        }
        void tick() {
            noDrops(helper);
            switch (step) {
                case ASSEMBLY -> {
                    if (rockets(helper).size() != 1) { return; }
                    if (rocket == null) {
                        rocket = rockets(helper).get(0); authority = saveRocket(helper, rocket);
                        fault = FixtureAdapterFaults.detachedRestore(helper.absolutePos(CARGO), probe);
                        submission = new AdapterAsyncFixture.Submission(fixture.owner, rocket);
                    }
                    if (!submission.advance(helper)) { return; }
                    helper.assertTrue(!rocket.isRemoved() && authority.equals(saveRocket(helper, rocket)),
                            "Pending disassembly changed persisted rocket authority");
                    advance(RestoreStep.DISASSEMBLY_PENDING);
                }
                case DISASSEMBLY_PENDING -> {
                    if (!rocket.isRemoved()) {
                        helper.assertTrue(rockets(helper).size() == 1 && authority.equals(saveRocket(helper, rocket)),
                                "Disassembly changed or duplicated D before its proved retirement");
                    }
                    if (probe.calls == 0) { return; }
                    reached(helper, probe);
                    helper.assertTrue(probe.readbacks == (probe.mode == FixtureAdapterFaults.Mode.RESTORE_MISMATCH ? 1 : 0),
                            "Restore failure did not follow the expected readback branch: " + probe.readbacks);
                    helper.assertTrue(FixtureAdapterFaults.restoring() == null, "Restore fault scope leaked");
                    carrier = helper.getBlockEntity(CARGO);
                    retained = carrier.saveWithFullMetadata();
                    assertHeld(); heldAt = helper.getTick(); advance(RestoreStep.PREPARATION_HELD);
                }
                case PREPARATION_HELD -> {
                    assertHeld();
                    if (helper.getTick() - heldAt < AdapterAsyncFixture.retryTicks()) { return; }
                    // Other U writers can make the whole-root acknowledgement temporarily stale.
                    // Observe a current checked absence before reload, not at an arbitrary later sample.
                    if (!AdapterAsyncFixture.checkedAbsent(carrier)) { return; }
                    clearFault();
                    var at = helper.absolutePos(CARGO); var state = carrier.getBlockState();
                    carrier.onChunkUnloaded(); helper.getLevel().removeBlockEntity(at);
                    var reloaded = BlockEntity.loadStatic(at, state, retained.copy());
                    helper.assertTrue(reloaded != null && reloaded != carrier && carrier.isRemoved(), "Carrier was not reconstructed");
                    helper.getLevel().setBlockEntity(reloaded); reloaded.onLoad();
                    helper.assertTrue(retained.equals(reloaded.saveWithFullMetadata()), "Reload changed retained resources");
                    advance(RestoreStep.CARRIER_RELOADED);
                }
                case CARRIER_RELOADED -> {
                    if (!(helper.getBlockEntity(CARGO) instanceof FixtureContainerBlockEntity)) { return; }
                    assertRestored(helper, fixture);
                    helper.assertTrue(helper.getBlockEntity(CARGO) != fixture.source, "Restoration reused the detached source");
                    helper.assertTrue(probe.calls == 1, "Healthy reload retriggered the removed fault");
                    queue(helper, fixture.owner); submission = null; advance(RestoreStep.HEALTHY_REASSEMBLY);
                }
                case HEALTHY_REASSEMBLY -> {
                    if (rockets(helper).size() != 1) { return; }
                    if (submission == null) { submission = new AdapterAsyncFixture.Submission(fixture.owner, rockets(helper).get(0)); }
                    if (submission.advance(helper)) { advance(RestoreStep.HEALTHY_DISASSEMBLY); }
                }
                case HEALTHY_DISASSEMBLY -> {
                    if (!(helper.getBlockEntity(CARGO) instanceof FixtureContainerBlockEntity)) { return; }
                    assertRestored(helper, fixture); close(); helper.succeed();
                }
            }
        }
        private void assertHeld() {
            helper.assertTrue(rocket.isRemoved() && rockets(helper).isEmpty(), "Held carrier coexists with D");
            helper.assertTrue(carrier != null && carrier == helper.getBlockEntity(CARGO)
                    && retained.equals(carrier.saveWithFullMetadata()), "Failed restoration changed carrier authority");
            var member = retained.getCompound("arce_sealed_rocket_member");
            helper.assertTrue(member.getUUID("rocket_entity_id").equals(rocket.getUUID())
                    && member.getCompound("block_entity").getCompound("data").getCompound("data").equals(fixture.inventory),
                    "Held carrier lost identity, cargo counts or metadata");
            helper.assertTrue(!(carrier instanceof net.minecraft.world.Container)
                    && !carrier.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Failed restoration exposed partial world inventory");
            helper.assertTrue(probe.calls == 1 && probe.target != null && probe.target.isRemoved()
                    && ((net.minecraft.world.Container) probe.target).isEmpty(), "Failed detached target retained usable cargo");
            noDrops(helper);
        }
        private void advance(RestoreStep next) {
            LogUtils.getLogger().info("ARCE_ADAPTER_TRANSITION mode={} from={} to={} tick={}",
                    probe.mode, step, next, helper.getTick());
            step = next;
        }
        private void clearFault() {
            if (fault == null) { return; }
            try { fault.close(); } catch (Exception failure) { throw new IllegalStateException(failure); }
            finally { fault = null; }
        }
        @Override public void close() { if (!closed) { closed = true; clearFault(); } }
    }

    private static Fixture create(GameTestHelper helper) {
        return create(helper, AdapterTestMod.CONTAINER.get());
    }

    private static Fixture create(GameTestHelper helper, Block cargo) {
        // Structure placement can drop natural cave decorations before this test starts.
        // Clear only pre-operation items; every later inventory assertion still requires zero drops.
        for (var item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, region(helper))) {
            LogUtils.getLogger().info("ARCE_ADAPTER_FIXTURE_PREEXISTING_DROP item={} position={} age={}",
                    item.getItem(), item.position(), item.getAge());
            item.discard();
        }
        noDrops(helper);
        helper.setBlock(ORIGIN.below(), block("rocket_assembler"));
        helper.setBlock(ORIGIN, block("rocket_motor"));
        helper.setBlock(ORIGIN.above(), block("rocket_seat"));
        helper.setBlock(ORIGIN.above(2), block("guidance_computer"));
        helper.setBlock(CARGO, cargo);
        var container = (FixtureContainerBlockEntity) helper.getBlockEntity(CARGO);
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 17);
        diamonds.setHoverName(Component.literal("Fault fixture cargo"));
        container.setItem(0, diamonds);
        container.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
        var owner = new AdapterAsyncFixture.Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AdapterFault"));
        BlockPos absolute = helper.absolutePos(ORIGIN);
        owner.setPos(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D);
        return new Fixture(owner, container, container.captureInventory());
    }

    private static void queue(GameTestHelper helper, FakePlayer owner) {
        BlockPos assembler = helper.absolutePos(ORIGIN.below());
        try {
            int result = helper.getLevel().getServer().getCommands().getDispatcher().execute(
                    "arce rocket assemble " + assembler.getX() + " " + assembler.getY() + " " + assembler.getZ(),
                    owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            helper.assertTrue(result == 1, "Production assembly request was not queued");
        } catch (CommandSyntaxException exception) { throw new IllegalStateException(exception); }
    }

    private static CompoundTag report(GameTestHelper helper) {
        return helper.getBlockEntity(ORIGIN.below()).saveWithoutMetadata().getCompound("RocketAssemblerData");
    }

    private static String operatorReport(GameTestHelper helper, FakePlayer owner) {
        var replies = new ArrayList<String>();
        var output = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { replies.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        BlockPos assembler = helper.absolutePos(ORIGIN.below());
        try {
            int result = helper.getLevel().getServer().getCommands().getDispatcher().execute(
                    "arce rocket status " + assembler.getX() + " " + assembler.getY() + " " + assembler.getZ(),
                    owner.createCommandSourceStack().withPermission(2).withSource(output));
            // Status returns the operation's failure code even when the diagnostic lookup succeeds.
            helper.assertTrue(result == 0 && replies.size() == 1, "Operator failure status response differs: " + replies);
        } catch (CommandSyntaxException exception) { throw new IllegalStateException(exception); }
        return String.join("\n", replies);
    }

    private static void reached(GameTestHelper helper, FixtureAdapterFaults.Probe probe) {
        helper.assertTrue(probe.calls == 1, "Fault callback was not reached exactly once: " + probe.mode + "/" + probe.calls);
        if (probe.mode.name().endsWith("_SLOW")) {
            helper.assertTrue(probe.delayedNanos >= FixtureAdapterFaults.SLOW_NANOS,
                    "Slow fixture returned before its finite delay");
        }
        // Log outside the timed provider callback so receipt I/O cannot select a different failure branch.
        LogUtils.getLogger().info("ARCE_ADAPTER_FAULT mode={} calls={} delayed_ns={} readbacks={}",
                probe.mode, probe.calls, probe.delayedNanos, probe.readbacks);
    }

    private static CompoundTag saveRocket(GameTestHelper helper, Entity rocket) {
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(rocket.save(saved), "Rocket serialization failed");
        return saved.getCompound("RocketEntityData");
    }

    private static void assertRestored(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(rockets(helper).isEmpty(), "World inventory and rocket authority coexist");
        helper.assertTrue(helper.getBlockState(ORIGIN).is(block("rocket_motor"))
                        && helper.getBlockState(ORIGIN.above()).is(block("rocket_seat"))
                        && helper.getBlockState(ORIGIN.above(2)).is(block("guidance_computer"))
                        && helper.getBlockState(CARGO).equals(fixture.source.getBlockState()), "Original block set differs");
        var restored = (FixtureContainerBlockEntity) helper.getBlockEntity(CARGO);
        helper.assertTrue(fixture.inventory.equals(restored.captureInventory()), "Cargo counts or metadata differ");
        CompoundTag saved = restored.saveWithoutMetadata().getCompound("FixtureInventory");
        helper.assertTrue(saved.getInt("schema_version") == 1, "Fixture schema changed");
        saved.remove("schema_version");
        helper.assertTrue(fixture.inventory.equals(saved), "Native inventory differs or fault state was persisted");
        noDrops(helper);
    }

    private static List<Entity> rockets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Entity.class, region(helper),
                entity -> ResourceLocation.tryParse(HOST + ":rocket").equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
    }

    private static AABB region(GameTestHelper helper) { return new AABB(helper.absolutePos(ORIGIN)).inflate(3); }
    private static void noDrops(GameTestHelper helper) {
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, region(helper));
        helper.assertTrue(drops.isEmpty(), "Duplicate inventory drops found: " + drops.stream()
                .map(item -> item.getItem() + " at " + item.position() + " age=" + item.getAge()).toList());
    }
    private static Block block(String id) { return ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":" + id)); }
    private record Fixture(FakePlayer owner, FixtureContainerBlockEntity source, CompoundTag inventory) { }
}
