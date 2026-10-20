package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** D-P fixtures: explicit after snapshots and the unchanged adapter, not item transactions or crash-safe disassembly. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowItemCargoGameTests {
    private record Move(boolean load, Item item, int count, int container, int buffer, int expected) { }

    @GameTest(template = "rocket_test", batch = "item_cargo", timeoutTicks = 40)
    public static void loadAndUnloadAfterPayloadsRestoreThroughTheUnchangedAdapter(GameTestHelper helper) {
        var adapters = RocketBlockEntityAdapters.defaults();
        var moves = List.of(new Move(true, Items.STONE, 5, 0, 0, 15), new Move(true, Items.ENDER_PEARL, 6, 5, 1, 16),
                new Move(true, Items.DIRT, 3, 1, 2, 3), new Move(false, Items.STONE, 10, 0, 0, 0),
                new Move(false, Items.ENDER_PEARL, 4, 5, 3, 6));
        int column = 0;
        for (var block : List.of(Blocks.CHEST, Blocks.BARREL)) {
            var pos = new BlockPos(1 + 2 * column++, 2, 2);
            helper.setBlock(pos, block);
            var container = (BaseContainerBlockEntity) helper.getBlockEntity(pos);
            container.setItem(0, new ItemStack(Items.STONE, 10)); container.setItem(5, new ItemStack(Items.ENDER_PEARL, 10));
            container.setCustomName(Component.literal("Cargo hold"));
            var locked = container.saveWithoutMetadata(); locked.putString("Lock", "key"); container.load(locked);
            var payload = adapters.capture(container).payload();
            helper.assertTrue(payload != null && payload.adapterId().equals(VanillaContainerRocketAdapter.ID)
                    && payload.data().contains("CustomName") && payload.data().getString("Lock").equals("key"), "Container capture changed");
            var state = RocketBlockStateAdapter.capture(container.getBlockState());
            helper.assertTrue(RocketItemCargoMutation.storage(state), "A single chest or a barrel must hold item cargo");
            var before = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(),
                    new RocketPosition(0, 64, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0), state, payload)),
                    List.of(), new RocketStats(1, 1, 1, 2000, 1, 0, 1, 1), 10, propulsion());
            for (var move : moves) {
                var stack = new ItemStack(move.item(), move.count()).save(new CompoundTag());
                var delta = new EscrowItemCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                        VanillaContainerRocketAdapter.ID.toString()), stack,
                        move.load() ? move.buffer() : move.container(), move.load() ? move.container() : move.buffer());
                var after = RocketItemCargoMutation.apply(before, delta, move.load());
                var afterPayload = after.blocks().get(0).blockEntityPayload().orElseThrow();
                helper.assertTrue(adapters.restore(container, afterPayload)
                        && afterPayload.equals(adapters.capture(container).payload()), "After payload did not restore exactly: " + move);
                var held = container.getItem(move.container());
                helper.assertTrue(move.expected() == 0 ? held.isEmpty() : held.is(move.item()) && held.getCount() == move.expected(),
                        "Wrong container count after " + move);
                helper.assertTrue(Component.literal("Cargo hold").equals(container.getCustomName())
                        && adapters.capture(container).payload().data().getString("Lock").equals("key"), "Name or lock was lost");
                helper.assertTrue(before.structureDigest().equals(after.structureDigest())
                        && !before.contentHash().equals(after.contentHash()), "Item cargo changed structure or kept the hash");
            }
            helper.assertTrue(adapters.restore(container, payload), "Before payload no longer restores");
        }
        helper.succeed();
    }

    /** W112's ticket, native capture and one-use permit carry an item delta; the device side is a fixture until D-A. */
    @GameTest(template = "rocket_test", batch = "item_cargo", timeoutTicks = 200)
    public static void itemPreparationCapturesAndPublishesThroughTheOneUsePermit(GameTestHelper helper) {
        var scenario = new Publication(helper);
        helper.onEachTick(scenario::tick);
    }

    private static final class Publication {
        private final GameTestHelper helper;
        private RocketEntity rocket;
        private RocketCargoPreparations.Ticket ticket;
        private EscrowCargoPreparedCapture capture;
        private EscrowCargoPublication.Preparation publication;
        private EscrowItemCargoPayload delta;
        private EscrowOperation operation;
        private EscrowLedger root;
        private RocketStructureSnapshot before;
        private CompoundTag deviceBefore, deviceAfter;
        private int phase;
        Publication(GameTestHelper helper) { this.helper = helper; }

        void tick() {
            if (phase == 5) { return; }
            try {
                var level = helper.getLevel(); var server = level.getServer();
                if (phase == 0) {
                    var origin = helper.absolutePos(new BlockPos(2, 2, 2));
                    if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())) { return; }
                    if (!RocketTransferSavedData.get(server).retryChecked(server)) { return; }
                    rocket = create(helper); before = rocket.snapshot().orElseThrow();
                    delta = new EscrowItemCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                            VanillaContainerRocketAdapter.ID.toString()), new ItemStack(Items.STONE, 5).save(new CompoundTag()), 0, 0);
                    root = new EscrowLedger(UUID.randomUUID(), 0, 0, List.of(), List.of(), List.of());
                    phase = 1;
                }
                var session = RocketRuntime.cargoPreparations(server);
                if (phase == 1 || phase == 3) {
                    var started = session.begin(rocket, delta, true);
                    if (started.isEmpty()) { return; }
                    ticket = started.orElseThrow(); publication = null; phase++;
                }
                if (phase == 2) {
                    if (capture == null) {
                        check(!RocketRuntime.resourceMutationAllowed(rocket), "Item preparation did not lock resources");
                        if (!ticket.advance()) { return; }
                        check(ticket.cursor().after().contentHash().equals(RocketItemCargoMutation.apply(before, delta, true).contentHash()),
                                "Resumable item preparation differs from the pure mutation");
                        var op = unbound();
                        capture = EscrowCargoPreparedCapture.nativeCapture(ticket, root.id(), op, deviceBefore::copy, deviceAfter::copy, () -> true, 0);
                    }
                    if (!capture.advance(root, List.of())) { return; }
                    check(capture.result().status() == RocketCargoSelection.Status.ELIGIBLE, "Item capture refused: " + capture.result().status());
                    operation = capture.result().capture().operation(); capture.discard();
                    check(!RocketRuntime.cargoPreparing(rocket), "A discarded capture kept the preparation lock");
                    root = root.append(List.of(operation)); phase = 3; return;
                }
                if (phase == 4) {
                    if (!ticket.advance()) { return; }
                    if (publication == null) { publication = new EscrowCargoPublication.Preparation(root, operation, ticket, () -> true); }
                    var permit = publication.issue();
                    if (permit == null) { return; }
                    rocket.applyEscrowCargo(permit);
                    rejects(() -> rocket.applyEscrowCargo(permit), "Permit could be reused");
                    var published = rocket.snapshot().orElseThrow();
                    check(published.contentHash().equals(operation.nativeBinding().snapshotHash()) && published.snapshotId().equals(before.snapshotId())
                            && published.structureDigest().equals(before.structureDigest()), "Publication hash or identity differs");
                    var block = published.blocks().get(0);
                    var slots = RocketItemCargoMutation.container(block.state(), block.blockEntityPayload().orElseThrow());
                    check(slots.get(0).getByte("Count") == 15 && block.blockEntityPayload().orElseThrow().data().getString("Lock").equals("key"),
                            "Published container is wrong");
                    check(rocket.escrowSection().epoch() == 1 && rocket.escrowSection().markers().size() == 1
                            && rocket.escrowSection().markers().get(0).role().equals("DESTINATION"), "Publication lost revision or marker");
                    check(rocket.flightData().orElseThrow().fuel().totalAmount() == 1000, "Item cargo changed propulsion");
                    check(session.begin(rocket, delta, true).isEmpty(), "A marked rocket admitted fresh item cargo");
                    rocket.discard(); phase = 5; helper.succeed();
                }
            } catch (Throwable failure) {
                if (ticket != null) { ticket.close(); }
                if (rocket != null && rocket.isAlive()) { rocket.discard(); }
                throw failure;
            }
        }

        /** The unbound load operation and its fixture device envelopes; native capture binds it. */
        private EscrowOperation unbound() {
            var after = RocketItemCargoMutation.apply(before, delta, true);
            var logical = rocket.assemblyTransactionId().orElseThrow(); var chunk = rocket.chunkPosition();
            var fuel = rocket.flightData().orElseThrow().fuel().resourceSignature(); var deviceId = UUID.randomUUID();
            var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, rocket.level().dimension().location().toString(), rocket.getUUID(),
                    logical, EscrowAdapterIds.ROCKET, chunk.x, 0, chunk.z, EscrowEndpoint.Unit.CONTAINER, rocket.escrowSection().epoch(),
                    EscrowSignatureKind.composite(logical, before.contentHash(), fuel), EscrowSignatureKind.composite(logical, after.contentHash(), fuel));
            var oldBuffer = new EscrowItemBuffer(List.of(new ItemStack(Items.STONE, 20).save(new CompoundTag()), new CompoundTag(),
                    new CompoundTag(), new CompoundTag()));
            var newBuffer = RocketItemCargoMutation.buffer(oldBuffer, delta, true);
            var type = ModIdentity.id("rocket_item_loader").toString();
            var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, endpoint.level(), deviceId, null, type, 0, 64, 0,
                    EscrowEndpoint.Unit.CONTAINER, 0, oldBuffer.signature(deviceId), newBuffer.signature(deviceId));
            var payload = delta.encode();
            var op = new EscrowOperation(UUID.randomUUID(), root.issuedThrough() + 1, rocket.ownerId().orElseThrow(), 0,
                    EscrowOperation.Phase.PREPARED, EscrowReason.NONE, payload, RocketNativeResources.signature(payload), source, endpoint,
                    new EscrowGuard(logical, null, 1, "0".repeat(64)), EscrowOperation.Kind.ITEM_CARGO_LOAD, new CompoundTag());
            deviceBefore = new CompoundTag(); deviceBefore.putString("id", type);
            deviceBefore.putInt("x", 0); deviceBefore.putInt("y", 64); deviceBefore.putInt("z", 0);
            var device = new CompoundTag(); device.putInt("schema_version", 1); device.putUUID("owner", op.owner());
            device.putUUID("device", deviceId); device.putString("role", "LOAD"); device.put("buffer", oldBuffer.encode());
            device.put("resource_escrow", RocketEscrowSection.neutral().encode());
            deviceBefore.put(EscrowItemCargoEligibility.DEVICE_ROOT, device);
            deviceAfter = deviceBefore.copy(); var afterDevice = deviceAfter.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT);
            afterDevice.put("buffer", newBuffer.encode());
            afterDevice.put("resource_escrow", new RocketEscrowSection(root.id(), source.afterEpoch(),
                    List.of(new EscrowPrefix.Link(op, source, "SOURCE").marker())).encode());
            return op;
        }
        private void check(boolean value, String message) { helper.assertTrue(value, message); }
        private void rejects(Runnable action, String message) {
            boolean refused = false;
            try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { refused = true; }
            check(refused, message);
        }
    }

    static RocketEntity create(GameTestHelper helper) {
        var name = Component.Serializer.toJson(Component.literal("Cargo hold"));
        var data = new CompoundTag(); var items = new net.minecraft.nbt.ListTag();
        var stone = new ItemStack(Items.STONE, 10).save(new CompoundTag()); stone.putByte("Slot", (byte) 0); items.add(stone);
        data.put("Items", items); data.putString("CustomName", name); data.putString("Lock", "key");
        var chest = new RocketBlockState(net.minecraft.resources.ResourceLocation.tryParse("minecraft:chest"),
                Map.of("facing", "north", "type", "single", "waterlogged", "false"));
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), chest, new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, data)),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(
                        net.minecraft.resources.ResourceLocation.tryParse("fixture:hull"), Map.of())));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(),
                new RocketPosition(0, 64, 0), blocks, List.of(), new RocketStats(blocks.size(), 100, 1000, 2000, 1, 0, 1, 1), 25, propulsion());
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(helper.getLevel()));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1000), 25));
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Rocket fixture failed");
        return rocket;
    }

    static RocketPropulsion propulsion() {
        var settings = RocketPropulsion.Settings.DEFAULTS;
        var fluids = new EnumMap<>(settings.allowedFluids()); fluids.put(RocketPropulsion.Role.MONO, List.of("minecraft:water"));
        return new RocketPropulsion(RocketPropulsion.Mode.MONO, 2000, 0, 1, 1, settings.withAllowedFluids(fluids));
    }
}
