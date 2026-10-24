package io.github.sunthemoon.arceadaptertest;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** R95-R98 prerequisite. Conservation assertions remain strict if the platform probe fails. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedStagingProbeGameTests {
    private static final String HOST = "advancedrocketrycommunity";
    private static final BlockPos RELATIVE = new BlockPos(5, 2, 5);
    private static final UUID OWNER = UUID.fromString("618733de-22e0-45f4-a613-ef57c45782b3");
    private enum Failure { FALSE, THROW, SLOW, MISMATCH }
    private static Function<BlockEntity, CompoundTag> builtInContainerCapture;

    /** Test-batch injection by the host, keeping this independent fixture free of host-internal dependencies. */
    public static void setBuiltInContainerCapture(Function<BlockEntity, CompoundTag> capture) {
        builtInContainerCapture = capture;
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w152_probe", timeoutTicks = 40)
    public static void preparedPublicationCoversEveryAdmittedMember(GameTestHelper helper) {
        List<Block> members = members();
        helper.assertTrue(members.contains(Blocks.CHEST) && members.contains(Blocks.BARREL)
                && members.contains(host("pressurized_tank")) && members.contains(host("rocket_satellite_bay"))
                && members.contains(AdapterTestMod.CONTAINER.get()), "Probe omitted a payload host");
        int plain = 0;
        for (Block block : members) {
            var trace = prepare(helper, block);
            try {
                if (trace.prepared != null) {
                    restore(trace.prepared, trace.carrier.sealed);
                    CompoundTag readback = recapture(trace.prepared);
                    helper.assertTrue(trace.carrier.sealed.getAllKeys().stream()
                            .allMatch(key -> trace.carrier.sealed.get(key).equals(readback.get(key))),
                            "Prepared payload did not recapture exactly");
                    helper.assertTrue(trace.prepared.getLevel() == null, "Preparation bound the target to a Level");
                    trace.observe("restored.unpublished");
                    helper.assertTrue(!trace.prematureWorldAccess, "Target became reachable during prepare");
                } else { plain++; }
                trace.publish = true;
                trace.events.add("publish.begin");
                boolean placed = helper.getLevel().setBlock(trace.at, block.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                trace.observe("publish.return");
                helper.assertTrue(placed && helper.getLevel().getBlockState(trace.at).is(block), "Publication failed");
                helper.assertTrue(helper.getLevel().getBlockEntity(trace.at) == trace.prepared,
                        "Publication constructed a different or empty native block entity");
                if (trace.prepared != null) {
                    helper.assertTrue(trace.prepared.getLevel() == helper.getLevel(), "Publication did not bind the Level");
                    helper.assertTrue(resource(trace.prepared, false) == original(block), "Published payload changed");
                    helper.assertTrue(trace.carrier.isRemoved(), "Carrier was not retired");
                }
                helper.getLevel().updateNeighborsAt(trace.at, block);
                helper.getLevel().updateNeighbourForOutputSignal(trace.at, block);
                trace.observe("publish.notifications.complete");
                report(block, "publication", trace, resource(trace.prepared, false), 0);
            } finally { cleanup(trace); }
        }
        helper.assertTrue(plain >= 16, "Probe did not cover all original plain members");
        LogUtils.getLogger().info("W152_PROBE_MEMBERS total={} plain={}", members.size(), plain);
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w152_probe", timeoutTicks = 40)
    public static void abortedBuiltInsAreEmptyThroughRetainedReferences(GameTestHelper helper) {
        for (Block block : payloadHosts(false)) {
            for (Failure failure : Failure.values()) {
                var trace = prepare(helper, block);
                try {
                    reject(trace, failure);
                    abort(trace.prepared);
                    trace.observe("abort.complete");
                    helper.assertTrue(trace.retained == trace.prepared && trace.retained.isRemoved(),
                            "Attachment listener did not retain the discarded target");
                    helper.assertTrue(resource(trace.retained, false) == 0 && resource(trace.retained, true) == 0,
                            "Discarded built-in target retained resources");
                    helper.assertTrue(helper.getLevel().getBlockEntity(trace.at) == trace.carrier
                            && !trace.prematureWorldAccess, "Abort exposed or replaced the carrier");
                    report(block, failure + ":abort", trace, resource(trace.retained, false),
                            resource(trace.retained, true));
                } finally { cleanup(trace); }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w152_probe", timeoutTicks = 40)
    public static void attachmentReferencesMustConserveResourcesBeforeRemoval(GameTestHelper helper) {
        conservation(helper, false);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", batch = "w152_probe", timeoutTicks = 40)
    public static void serializableAttachmentReferencesMustConserveResourcesBeforeRemoval(GameTestHelper helper) {
        conservation(helper, true);
    }

    private static void conservation(GameTestHelper helper, boolean serializableAttachment) {
        List<String> violations = new ArrayList<>();
        // The R98 chest/barrel adapters recapture owned data directly. The fixture
        // instead captures its owned inventory directly. Tank/bay adapters copy dedicated roots,
        // so their diagnostic resource reads must not stand in for native capability serialization.
        List<Block> hosts = serializableAttachment
                ? List.of(Blocks.CHEST, Blocks.BARREL, AdapterTestMod.CONTAINER.get()) : payloadHosts(true);
        for (Block block : hosts) {
            for (Failure failure : Failure.values()) {
                var trace = prepare(helper, block, serializableAttachment);
                try {
                    CompoundTag sealed = trace.carrier.sealed.copy();
                    trace.armed = true;
                    reject(trace, failure);
                    abort(trace.prepared);
                    trace.armed = false;
                    trace.observe("abort.complete");
                    helper.assertTrue(trace.carrier.sealed.equals(sealed), "Aborted restore changed the carrier payload");
                    helper.assertTrue(!trace.prematureWorldAccess, "An uninstalled target was reachable through world lookup");
                    int rawOriginal = resource(trace.retained, false);
                    int rawForeign = resource(trace.retained, true);
                    int totalOriginal = original(block) + trace.recipient.getItem(0).getCount()
                            + trace.receivedFluid.getAmount() + rawOriginal;
                    int totalForeign = (block == host("pressurized_tank")
                            ? trace.foreignFluid.getAmount() : trace.foreignSupply.getCount()) + rawForeign;
                    report(block, failure + (serializableAttachment ? ":serializable-retained" : ":retained"),
                            trace, totalOriginal, totalForeign);
                    if (totalOriginal != original(block) || totalForeign != foreign(block)
                            || trace.unpublishedCallbacks != 0) {
                        violations.add(id(block) + "/" + failure + " original=" + totalOriginal + "/"
                                + original(block) + " foreign=" + totalForeign + "/" + foreign(block)
                                + " callbacks=" + trace.unpublishedCallbacks);
                    }
                } finally { cleanup(trace); }
            }
        }
        // Do not accept an empty discarded object as sufficient: recipients may already own its resources.
        helper.assertTrue(violations.isEmpty(), "W152B isolation stop: " + String.join("; ", violations));
        helper.succeed();
    }

    private static SealedStagingProbeFixture.Trace prepare(GameTestHelper helper, Block block) {
        return prepare(helper, block, false);
    }

    private static SealedStagingProbeFixture.Trace prepare(GameTestHelper helper, Block block,
            boolean serializableAttachment) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(RELATIVE);
        level.setBlock(at.east(), SealedStagingProbeFixture.OBSERVER.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at, SealedStagingProbeFixture.CARRIER.get().defaultBlockState(), Block.UPDATE_ALL);
        var trace = SealedStagingProbeFixture.begin(level, at);
        trace.serializableAttachment = serializableAttachment;
        trace.carrier = (SealedStagingProbeFixture.Carrier) level.getBlockEntity(at);
        trace.carrier.sealed = payload(block);
        trace.observe("carrier.ready");
        if (block instanceof EntityBlock entityBlock) {
            trace.prepared = entityBlock.newBlockEntity(at, block.defaultBlockState());
            helper.assertTrue(trace.retained == trace.prepared, "AttachCapabilities did not expose the exact target");
            trace.events.add("constructor.return");
            helper.assertTrue(trace.prepared.getLevel() == null, "Private target already has a Level");
            trace.observe("target.level-less.uninstalled");
        }
        return trace;
    }

    private static void reject(SealedStagingProbeFixture.Trace trace, Failure failure) {
        trace.events.add("restore.begin:" + failure);
        boolean[] accepted = {false};
        long start = System.nanoTime();
        if (trace.prepared instanceof FixtureContainerBlockEntity) {
            var mode = FixtureAdapterFaults.Mode.valueOf("RESTORE_" + failure);
            var fault = new FixtureAdapterFaults.Probe(mode);
            try {
                FixtureAdapterFaults.duringRestore(fault,
                        () -> accepted[0] = new FixtureInventoryAdapter().restore(trace.prepared, trace.carrier.sealed.copy()));
            } catch (IllegalStateException expected) {
                if (failure != Failure.THROW) { throw expected; }
                trace.events.add("restore.exception");
            }
        } else {
            try {
                restore(trace.prepared, trace.carrier.sealed);
                accepted[0] = true;
                // Built-ins have no fault injection API. Inject at the private prepare boundary,
                // after their native restore methods, before any target may be published.
                switch (failure) {
                    case FALSE -> accepted[0] = false;
                    case THROW -> throw new IllegalStateException("W152 probe restore exception");
                    case SLOW -> {
                        while (System.nanoTime() - start < 20_000_000L) { LockSupport.parkNanos(1_000_000L); }
                    }
                    case MISMATCH -> {
                        if (trace.prepared instanceof Container container) {
                            // Preserve original resources while making recapture differ from C.
                            container.setItem(container.getContainerSize() - 1, new ItemStack(Items.STICK));
                        } else { empty(trace.prepared); }
                    }
                }
            } catch (IllegalStateException expected) {
                if (failure != Failure.THROW) { throw expected; }
                accepted[0] = false;
                trace.events.add("restore.exception");
            }
        }
        long elapsed = System.nanoTime() - start;
        // A rejected or over-budget restore is aborted without invoking a capture it does not need.
        // Only an accepted, in-budget restore reaches the production recapture boundary.
        boolean same = false;
        if (accepted[0] && elapsed <= 5_000_000L) {
            CompoundTag readback = recapture(trace.prepared);
            same = trace.carrier.sealed.getAllKeys().stream()
                    .allMatch(key -> trace.carrier.sealed.get(key).equals(readback.get(key)));
        }
        if (accepted[0] && elapsed <= 5_000_000L && same) {
            throw new AssertionError("Injected prepare failure was accepted");
        }
        trace.events.add("restore.rejected:returned=" + accepted[0] + ",overBudget="
                + (elapsed > 5_000_000L) + ",recapture=" + same);
    }

    private static CompoundTag recapture(BlockEntity target) {
        if (target instanceof ChestBlockEntity || target instanceof BarrelBlockEntity) {
            if (builtInContainerCapture == null) { throw new AssertionError("Host capture bridge is not installed"); }
            return builtInContainerCapture.apply(target);
        }
        return target instanceof FixtureContainerBlockEntity fixture
                ? new FixtureInventoryAdapter().capture(fixture) : target.saveWithoutMetadata();
    }

    private static void restore(BlockEntity target, CompoundTag payload) {
        if (target instanceof FixtureContainerBlockEntity) {
            if (!new FixtureInventoryAdapter().restore(target, payload.copy())) {
                throw new IllegalStateException("Fixture restore rejected a valid payload");
            }
        } else {
            target.load(payload.copy());
            // VanillaContainerRocketAdapter calls this after load. Bay.restoreRoot also calls it.
            // Calling it on the tank is an additional conservative re-entrancy probe.
            target.setChanged();
        }
    }

    private static void abort(BlockEntity target) {
        if (target == null) { return; }
        // Empty first, so capability invalidation observers cannot extract a built-in during setRemoved.
        empty(target);
        target.setRemoved();
    }

    private static void empty(BlockEntity target) {
        if (target instanceof Container container) { container.clearContent(); }
        else {
            var empty = new CompoundTag();
            if (target.getBlockState().is(host("pressurized_tank"))) { empty = payload(host("pressurized_tank"));
                empty.getCompound("arce_pressurized_tank").put("fluid", new CompoundTag()); }
            target.load(empty);
        }
    }

    private static void cleanup(SealedStagingProbeFixture.Trace trace) {
        trace.armed = false;
        SealedStagingProbeFixture.end();
        abort(trace.prepared);
        trace.level.removeBlockEntity(trace.at);
        trace.level.setBlock(trace.at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        trace.level.setBlock(trace.at.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static CompoundTag payload(Block block) {
        var result = new CompoundTag();
        if (block == Blocks.CHEST || block == Blocks.BARREL || block == AdapterTestMod.CONTAINER.get()) {
            var items = new ListTag();
            var item = new ItemStack(Items.DIAMOND, 7).save(new CompoundTag());
            item.putByte("Slot", (byte) 0);
            items.add(item);
            result.put("Items", items);
        } else if (block == host("pressurized_tank")) {
            var root = new CompoundTag(); root.putInt("schema", 1);
            var fluid = new CompoundTag(); fluid.putString("FluidName", "minecraft:water"); fluid.putInt("Amount", 1_000);
            root.put("fluid", fluid); result.put("arce_pressurized_tank", root);
        } else if (block == host("rocket_satellite_bay")) {
            var root = new CompoundTag(); root.putInt("schema_version", 1); root.putUUID("owner", OWNER);
            var identity = new CompoundTag(); identity.putInt("schema_version", 1);
            identity.putUUID("satellite_id", UUID.fromString("3876b09f-bd09-4092-8197-dbb5bd362457"));
            identity.putUUID("owner_id", OWNER); identity.putString("definition_id", "arce_adapter_test:research_payload");
            ItemStack pkg = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(HOST + ":data_satellite_package")));
            pkg.getOrCreateTag().put("SatelliteIdentity", identity);
            root.put("package", pkg.save(new CompoundTag())); result.put("arce_satellite_bay", root);
        }
        return result;
    }

    private static int resource(BlockEntity target, boolean foreign) {
        if (target == null) { return 0; }
        if (target instanceof Container container) {
            int count = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack item = container.getItem(slot);
                if (item.is(foreign ? Items.GOLD_INGOT : Items.DIAMOND)) { count += item.getCount(); }
            }
            return count;
        }
        CompoundTag data = target.saveWithoutMetadata();
        if (target.getBlockState().is(host("pressurized_tank"))) {
            CompoundTag fluid = data.getCompound("arce_pressurized_tank").getCompound("fluid");
            return fluid.getString("FluidName").equals(foreign ? "minecraft:lava" : "minecraft:water")
                    ? fluid.getInt("Amount") : 0;
        }
        return foreign ? 0 : data.getCompound("arce_satellite_bay").getCompound("package").getByte("Count");
    }

    private static void report(Block block, String scenario, SealedStagingProbeFixture.Trace trace, int original, int foreign) {
        LogUtils.getLogger().info("W152_PROBE host={} scenario={} original={} foreign={} extracted={} inserted={} firstCap={} callbacks={} trace={}",
                id(block), scenario, original, foreign, trace.extracted, trace.foreignInserted, trace.firstCapability,
                trace.unpublishedCallbacks, String.join(" -> ", trace.events));
    }

    private static int original(Block block) {
        return block == host("pressurized_tank") ? 1_000 : block == host("rocket_satellite_bay") ? 1 : 7;
    }
    private static int foreign(Block block) { return block == host("pressurized_tank") ? 500 : 3; }
    private static Block host(String name) { return ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":" + name)); }
    private static String id(Block block) { return ForgeRegistries.BLOCKS.getKey(block).toString(); }
    private static List<Block> payloadHosts(boolean external) {
        var result = new ArrayList<>(List.of(Blocks.CHEST, Blocks.BARREL, host("pressurized_tank"), host("rocket_satellite_bay")));
        if (external) { result.add(AdapterTestMod.CONTAINER.get()); }
        return result;
    }
    private static List<Block> members() {
        List<TagKey<Block>> tags = List.of("rocket_movable", "rocket_propulsion", "rocket_satellite_bays").stream()
                .map(name -> TagKey.create(Registries.BLOCK, ResourceLocation.tryParse(HOST + ":" + name))).toList();
        var result = new ArrayList<Block>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (tags.stream().anyMatch(block.defaultBlockState()::is)) { result.add(block); }
        }
        // The bay is a separate host category, independent of the movable/propulsion tags.
        if (!result.contains(host("rocket_satellite_bay"))) { result.add(host("rocket_satellite_bay")); }
        result.sort(Comparator.comparing(SealedStagingProbeGameTests::id));
        return result;
    }

    private SealedStagingProbeGameTests() { }
}
