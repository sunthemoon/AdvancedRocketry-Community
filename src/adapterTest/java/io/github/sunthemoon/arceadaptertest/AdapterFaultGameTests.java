package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
    private static final String HOST = "advancedrocketrycommunity";
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    private static final BlockPos CARGO = ORIGIN.east();
    private static final List<BlockPos> POSITIONS = List.of(ORIGIN, ORIGIN.above(), ORIGIN.above(2), CARGO);

    private AdapterFaultGameTests() { }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_success", timeoutTicks = 60)
    public static void throwingEligibilityPreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAN_MOVE_THROW);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_success", timeoutTicks = 60)
    public static void slowEligibilityPreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAN_MOVE_SLOW);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_success", timeoutTicks = 60)
    public static void throwingCapturePreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAPTURE_THROW);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_success", timeoutTicks = 60)
    public static void oversizedCapturePreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAPTURE_OVERSIZED);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_success", timeoutTicks = 60)
    public static void slowCapturePreservesSource(GameTestHelper helper) {
        scanFailure(helper, FixtureAdapterFaults.Mode.CAPTURE_SLOW);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_fault", timeoutTicks = 60)
    public static void throwingRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_THROW);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_fault", timeoutTicks = 60)
    public static void falseRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_FALSE);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_fault", timeoutTicks = 60)
    public static void slowRestoreCleansPartialInventoryAndRetries(GameTestHelper helper) {
        restoreFailure(helper, FixtureAdapterFaults.Mode.RESTORE_SLOW);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w164_adapter_fault", timeoutTicks = 60)
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
        fixture.source.fault = probe;
        queue(helper, fixture.owner);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(report(helper).getString("code").equals("UNSUPPORTED_BLOCK_ENTITY"),
                        "Scanner did not report the failing public adapter: " + report(helper)))
                .thenExecute(() -> {
                    reached(helper, probe);
                    helper.assertTrue(!report(helper).getString("detail").contains(FixtureAdapterFaults.PRIVATE_DETAIL),
                            "Host diagnostic exposed provider exception detail");
                    if (mode == FixtureAdapterFaults.Mode.CAPTURE_OVERSIZED) {
                        helper.assertTrue(report(helper).getString("detail").endsWith(
                                        "detail=" + AdapterTestMod.CONTAINER_ID + ": RocketSnapshotException}"),
                                "Oversized capture was not rejected by payload validation: " + report(helper));
                    }
                    helper.assertTrue(rockets(helper).isEmpty(), "Failed scan created a rocket authority");
                    assertRestored(helper, fixture);
                    helper.assertTrue(helper.getBlockEntity(CARGO) == fixture.source, "Failed scan replaced its source inventory");
                    fixture.source.fault = null;
                    queue(helper, fixture.owner);
                })
                .thenWaitUntil(() -> helper.assertTrue(rockets(helper).size() == 1, "Healthy scan retry failed"))
                .thenExecute(() -> interact(fixture.owner, rockets(helper).get(0)))
                // DISASSEMBLY_PENDING -> BLOCKS_PUBLISHED, through the installed B2 executor.
                .thenWaitUntil(() -> assertRestored(helper, fixture)).thenSucceed();
    }

    private static void restoreFailure(GameTestHelper helper, FixtureAdapterFaults.Mode mode) {
        var trial = new RestoreTrial(helper, mode);
        helper.runAtTickTime(59, trial::close);
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
                    rocket = rockets(helper).get(0); authority = saveRocket(helper, rocket);
                    fault = FixtureAdapterFaults.detachedRestore(helper.absolutePos(CARGO), probe);
                    interact(fixture.owner, rocket);
                    helper.assertTrue(!rocket.isRemoved() && authority.equals(saveRocket(helper, rocket)),
                            "Pending disassembly changed persisted rocket authority");
                    step = RestoreStep.DISASSEMBLY_PENDING;
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
                    assertHeld(); heldAt = helper.getTick(); step = RestoreStep.PREPARATION_HELD;
                }
                case PREPARATION_HELD -> {
                    assertHeld();
                    // Keep the original limit for the first regression run; R118 permits a derived limit if needed.
                    if (helper.getTick() - heldAt < 2) { return; }
                    clearFault();
                    var at = helper.absolutePos(CARGO); var state = carrier.getBlockState();
                    carrier.onChunkUnloaded(); helper.getLevel().removeBlockEntity(at);
                    var reloaded = BlockEntity.loadStatic(at, state, retained.copy());
                    helper.assertTrue(reloaded != null && reloaded != carrier && carrier.isRemoved(), "Carrier was not reconstructed");
                    helper.getLevel().setBlockEntity(reloaded); reloaded.onLoad();
                    helper.assertTrue(retained.equals(reloaded.saveWithFullMetadata()), "Reload changed retained resources");
                    step = RestoreStep.CARRIER_RELOADED;
                }
                case CARRIER_RELOADED -> {
                    if (!(helper.getBlockEntity(CARGO) instanceof FixtureContainerBlockEntity)) { return; }
                    assertRestored(helper, fixture);
                    helper.assertTrue(helper.getBlockEntity(CARGO) != fixture.source, "Restoration reused the detached source");
                    helper.assertTrue(probe.calls == 1, "Healthy reload retriggered the removed fault");
                    queue(helper, fixture.owner); step = RestoreStep.HEALTHY_REASSEMBLY;
                }
                case HEALTHY_REASSEMBLY -> {
                    if (rockets(helper).size() != 1) { return; }
                    interact(fixture.owner, rockets(helper).get(0)); step = RestoreStep.HEALTHY_DISASSEMBLY;
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
        var owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AdapterFault"));
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

    private static void interact(FakePlayer owner, Entity rocket) {
        owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
        owner.setShiftKeyDown(true);
        try { rocket.interact(owner, InteractionHand.MAIN_HAND); } finally { owner.setShiftKeyDown(false); }
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
