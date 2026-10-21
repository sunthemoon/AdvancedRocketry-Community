package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W132-R2: an unloader with four full slots is DEVICE_FULL before any walk, and a walk's no-move verdict keeps an
 * unchanged device out of the cargo queue until its root changes. Nothing is admitted; the ledger stays detached.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemCargoNoMoveGameTests {
    private static final String BATCH = "item_cargo_no_move";
    private static Scenario running;

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 600)
    public static void noMoveVerdictsNeitherWalkNorQueueAgainUntilTheDeviceChanges(GameTestHelper helper) {
        running = new Scenario(helper); helper.onEachTick(running::tick);
    }
    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { if (running != null) { running.cleanup(); running = null; } }

    private static final class Scenario {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final List<RocketItemCargoBlockEntity> devices = new ArrayList<>();
        ResourceEscrowSavedData original;
        Path file, backup;
        boolean detached, aside, done;
        int phase, quietSince;
        RocketEntity rocket;
        RocketItemCargoBlockEntity loader, unloader;
        Scenario(GameTestHelper helper) { this.helper = helper; pacer = new GameTestTickPacer(helper.getLevel().getServer()); }

        void tick() {
            if (done) { return; }
            pacer.pace(); var level = helper.getLevel(); var server = level.getServer(); var runtime = EscrowRuntime.of(server);
            var queue = runtime.fills(server);
            if (phase == 0) {
                var origin = helper.absolutePos(new BlockPos(4, 2, 4));
                if (!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong())
                        || !RocketTransferSavedData.get(server).retryChecked(server) || runtime.propulsion(server).holdsWork()) { return; }
                original = ResourceEscrowSavedData.existing(server);
                check(runtime.replaceLedgerForTests(server, null), "Cannot detach idle ledger"); detached = true;
                file = ResourceEscrowSavedData.file(server); backup = file.resolveSibling(file.getFileName() + ".w132f-backup");
                try { if (Files.exists(file)) { Files.move(file, backup, StandardCopyOption.ATOMIC_MOVE); } aside = true; }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                var full = new ListTag();
                for (int slot = 0; slot < 27; slot++) {
                    var entry = new ItemStack(Items.DIRT, 64).save(new CompoundTag()); entry.putByte("Slot", (byte) slot); full.add(entry);
                }
                rocket = ItemCargoReconciliationFixtures.create(level, ItemCargoReconciliationFixtures.snapshot(level, origin, full));
                loader = device(new BlockPos(2, 2, 4), RocketCargoRole.LOAD, ItemCargoReconciliationFixtures.buffer(true));
                unloader = device(new BlockPos(6, 2, 4), RocketCargoRole.UNLOAD, new EscrowItemBuffer(List.of(ItemCargoReconciliationFixtures.stack(64),
                        ItemCargoReconciliationFixtures.stack(64), ItemCargoReconciliationFixtures.stack(64), ItemCargoReconciliationFixtures.stack(64))));
                enable(loader, true); enable(unloader, true);
                phase = 1; return;
            }
            if (phase == 1) {
                if (!io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.resourceMutationAllowed(rocket)
                        || !RocketEscrowGate.idle(rocket) || RocketTransferSavedData.get(server)
                        .acknowledgedGuard(rocket.assemblyTransactionId().orElseThrow()).isEmpty()) { return; }
                check(unloader.attempt() == RocketCargoStatus.DEVICE_FULL && !queue.waiting(id(unloader)),
                        "A full unloader was queued for a walk instead of DEVICE_FULL");
                // The device's own pacer may have queued or even finished the first walk already.
                var first = loader.attempt();
                check(first == RocketCargoStatus.READY && queue.waiting(id(loader))
                        || first == RocketCargoStatus.CARGO_FULL && !queue.waiting(id(loader)), "The loader's first walk was not queued: " + first);
                phase = 2; return;
            }
            if (phase == 2) {
                if (queue.waiting(id(loader))) { return; }
                check(loader.displayStatus() == RocketCargoStatus.CARGO_FULL, "The walk did not find CARGO_FULL: " + loader.displayStatus());
                check(loader.attempt() == RocketCargoStatus.CARGO_FULL && !queue.waiting(id(loader)), "An unchanged loader was queued again");
                quietSince = server.getTickCount(); phase = 3; return;
            }
            if (phase == 3) {
                // Two pacer periods: the devices' own 20-tick attempts must not queue either unchanged device.
                check(!queue.waiting(id(loader)) && loader.displayStatus() == RocketCargoStatus.CARGO_FULL
                        && !queue.waiting(id(unloader)) && unloader.displayStatus() == RocketCargoStatus.DEVICE_FULL,
                        "An unchanged device was queued or lost its verdict");
                if (server.getTickCount() - quietSince < 45) { return; }
                var loaderPort = loader.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
                var unloaderPort = unloader.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
                check(loaderPort.insertItem(1, new ItemStack(Items.COBBLESTONE), false).isEmpty()
                        && unloaderPort.extractItem(0, 1, false).getCount() == 1, "The local ports did not change either buffer");
                check(loader.attempt() == RocketCargoStatus.READY && queue.waiting(id(loader)), "A changed loader kept its old verdict");
                check(unloader.attempt() == RocketCargoStatus.READY && queue.waiting(id(unloader)), "An unloader with room was not walked");
                phase = 4; return;
            }
            if (phase == 4) {
                if (queue.waiting(id(loader)) || queue.waiting(id(unloader))) { return; }
                check(loader.displayStatus() == RocketCargoStatus.CARGO_FULL
                        && unloader.displayStatus() == RocketCargoStatus.INCOMPATIBLE_CARGO, "The second walks found the wrong verdicts");
                check(loader.attempt() == RocketCargoStatus.CARGO_FULL && !queue.waiting(id(loader))
                        && unloader.attempt() == RocketCargoStatus.INCOMPATIBLE_CARGO && !queue.waiting(id(unloader)),
                        "A device was queued again after its second verdict");
                check(ResourceEscrowSavedData.existing(server) == null, "A no-move walk created a ledger");
                cleanup(); helper.succeed();
            }
        }
        void check(boolean value, String message) { helper.assertTrue(value, message); }
        UUID id(RocketItemCargoBlockEntity device) { return device.root().orElseThrow().device(); }
        RocketItemCargoBlockEntity device(BlockPos at, RocketCargoRole role, EscrowItemBuffer buffer) {
            helper.setBlock(at, role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_ITEM_LOADER.get() : ModBlocks.ROCKET_ITEM_UNLOADER.get());
            var device = (RocketItemCargoBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
            var root = RocketItemCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), role)
                    .withBuffer(buffer).withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
            var tag = new CompoundTag(); tag.put(RocketItemCargoRoot.KEY, root.encode()); device.load(tag); devices.add(device); return device;
        }
        void enable(RocketItemCargoBlockEntity device, boolean enabled) {
            var root = device.root().orElseThrow(); EscrowCargoSwitches.forTests(helper.getLevel().getServer(),
                    root.role() == RocketCargoRole.LOAD ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD, root.device(), enabled);
        }
        void cleanup() {
            if (done) { return; } done = true;
            var server = helper.getLevel().getServer(); var runtime = EscrowRuntime.of(server);
            for (var device : devices) { enable(device, false); device.setRemoved(); }
            if (rocket != null) { rocket.discard(); }
            var created = ResourceEscrowSavedData.existing(server);
            if (detached && created != null && created != original) { created.stop(); }
            if (aside) {
                try { Files.deleteIfExists(file); if (Files.exists(backup)) { Files.move(backup, file, StandardCopyOption.ATOMIC_MOVE); } }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            }
            if (detached && !runtime.replaceLedgerForTests(server, original)) { throw new IllegalStateException("W132F ledger restore refused"); }
        }
    }
}
