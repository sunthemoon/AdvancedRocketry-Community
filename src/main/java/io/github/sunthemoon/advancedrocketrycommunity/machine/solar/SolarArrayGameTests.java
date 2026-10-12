package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native lifecycle and conservation checks; real clients and dedicated restarts remain separate Gates. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SolarArrayGameTests {
    private static BlockPos position(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(5, 2, 5));
        return new BlockPos(anchor.getX(), helper.getLevel().getMaxBuildHeight() - 2, anchor.getZ());
    }
    private static SolarArrayFixture fixture(GameTestHelper helper, int panels) {
        var fixture = new SolarArrayFixture(helper.getLevel(), position(helper));
        try { fixture.install(fixture.position, panels); return fixture; }
        catch (RuntimeException | Error failed) { fixture.close(); throw failed; }
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void formationRefusalDirtyReloadAndUnloadedStopWithoutLoading(GameTestHelper helper) {
        var level = helper.getLevel(); var forced = new HashSet<>(level.getForcedChunks());
        try (var fixture = fixture(helper, 63)) {
            var array = fixture.array;
            helper.assertTrue(array.structure() == SolarArrayRules.Structure.FORMED && array.collectors() == 63, "Full array did not form");
            helper.assertTrue(!array.getCapability(ForgeCapabilities.ENERGY).isPresent(), "Controller exposed an independent FE port");
            var old = fixture.port(-1);
            fixture.set(fixture.position.offset(0, 0, 21), Blocks.STONE.defaultBlockState());
            helper.assertTrue(old.extractEnergy(1, false) == 0, "Dirty structure retained output authority");
            fixture.form();
            helper.assertTrue(array.structure() == SolarArrayRules.Structure.UNFORMED, "Invalid rear cell was accepted");
            fixture.set(fixture.position.offset(0, 0, 21), Blocks.AIR.defaultBlockState()); fixture.form();
            helper.assertTrue(array.structure() == SolarArrayRules.Structure.FORMED && array.collectors() == 62, "AIR was counted or refused");
            SolarArrayRuntime.manager(level).reload(); fixture.form();
            helper.assertTrue(array.collectors() == 62 && old.extractEnergy(1, false) == 0, "Reload revived a captured formation token");
            ChunkPos rear = new ChunkPos(fixture.position.offset(0, 0, 21));
            SolarArrayRuntime.manager(level).chunkChanged(rear.x, rear.z, true); fixture.form(); fixture.tick();
            helper.assertTrue(array.structure() == SolarArrayRules.Structure.UNLOADED && array.actualCredit() == 0, "Unloading footprint still generated");
            SolarArrayRuntime.manager(level).chunkChanged(rear.x, rear.z, false); fixture.form();
            helper.assertTrue(array.structure() == SolarArrayRules.Structure.FORMED, "Loaded footprint did not re-form");
        }
        try (var fixture = fixture(helper, 0)) {
            helper.assertTrue(fixture.array.structure() == SolarArrayRules.Structure.NO_COLLECTORS, "Zero-panel array was formed");
        }
        helper.assertTrue(forced.equals(new HashSet<>(level.getForcedChunks())), "Runtime added a forced-chunk ticket"); helper.succeed();
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void sharedPlugConflictRecoversWhenTheFirstControllerIsRemoved(GameTestHelper helper) {
        try (var fixture = fixture(helper, 63)) {
            var second = fixture.install(fixture.position.offset(2, 0, 0), 63);
            // Construction notifications can revoke either claimant. Establish a live first claim before testing refusal.
            SolarArrayRuntime.manager(fixture.level).reload();
            fixture.array.revalidate(fixture.level);
            helper.assertTrue(fixture.array.structure() == SolarArrayRules.Structure.FORMED, "First claimant was not established");
            second.revalidate(fixture.level);
            fixture.form();
            helper.assertTrue(fixture.array.structure() == SolarArrayRules.Structure.FORMED && second.structure() == SolarArrayRules.Structure.CONFLICT,
                    "Two controllers claimed one plug");
            fixture.level.setBlock(fixture.position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); fixture.form();
            helper.assertTrue(second.structure() == SolarArrayRules.Structure.FORMED, "Released output did not wake the conflicting controller");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_array_test", batch = "solar_array_surface", timeoutTicks = 80)
    public static void surfaceDayNightWeatherRoofAndOverflowUseNativeSamples(GameTestHelper helper) {
        var level = helper.getLevel(); long day = level.getDayTime(); float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        var fixture = fixture(helper, 63); BlockPos roof = fixture.position.above(); fixture.set(roof, Blocks.AIR.defaultBlockState());
        Runnable restore = () -> { try { fixture.close(); } finally { level.setDayTime(day); level.setRainLevel(rain); level.setThunderLevel(thunder); } };
        level.setDayTime(6_000); level.setRainLevel(0); level.setThunderLevel(0);
        await(helper, () -> level.isDay() && level.canSeeSky(roof), () -> {
            fixture.energy(0); fixture.tick();
            helper.assertTrue(fixture.array.actualCredit() == 126 && fixture.array.reason() == SolarGeneration.Reason.GENERATING, "Surface daytime array rate differs");
            level.setRainLevel(1); level.setThunderLevel(1); fixture.energy(0); fixture.tick();
            helper.assertTrue(fixture.array.actualCredit() == 31 && fixture.array.environment().weatherPermille() == 250, "Array forked weather accounting");
            level.setRainLevel(0); level.setThunderLevel(0); fixture.multiplier = 4; fixture.energy(19_999); fixture.tick();
            helper.assertTrue(fixture.array.energyStored() == 20_000 && fixture.array.actualCredit() == 1, "Nearly full buffer lost or wrapped FE");
            fixture.tick(); helper.assertTrue(fixture.array.actualCredit() == 0 && fixture.array.reason() == SolarGeneration.Reason.BUFFER_FULL, "Full buffer continued credit");
            level.setDayTime(18_000);
            await(helper, () -> !level.isDay(), () -> {
                fixture.energy(777); fixture.tick();
                helper.assertTrue(fixture.array.actualCredit() == 0 && fixture.array.reason() == SolarGeneration.Reason.NOT_DAYLIGHT, "Night array generated");
                fixture.set(roof, Blocks.STONE.defaultBlockState());
                await(helper, () -> !level.canSeeSky(roof), () -> {
                    fixture.tick(); helper.assertTrue(fixture.array.reason() == SolarGeneration.Reason.SKY_BLOCKED && fixture.array.actualCredit() == 0, "Roof did not block collection");
                    restore.run(); helper.succeed();
                }, restore);
            }, restore);
        }, restore);
    }

    @GameTest(template = "solar_array_test", batch = "solar_array_station", timeoutTicks = 100)
    public static void operationalStationExposureAndMissingBodyUseSharedFallback(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space fixture unavailable");
        var stations = StationRegistrySavedData.get(server); UUID id = UUID.randomUUID();
        var reservation = stations.reserve(id, UUID.randomUUID(), "Array", CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
        BlockPos pos = new BlockPos(reservation.landingPad().x(), space.getMaxBuildHeight() - 2, reservation.landingPad().z());
        Set<ChunkPos> forced = new HashSet<>();
        for (int z = 0; z < 22; z++) { for (int x = -1; x <= 1; x++) {
            ChunkPos chunk = new ChunkPos(pos.offset(x, 0, z));
            if (!space.getForcedChunks().contains(chunk.toLong())) { forced.add(chunk); }
        } }
        forced.forEach(chunk -> space.setChunkForced(chunk.x, chunk.z, true));
        SolarArrayFixture[] fixture = {null}; boolean[] committed = {false};
        Runnable restore = () -> {
            try { if (fixture[0] != null) { fixture[0].close(); } }
            finally {
                try { if (committed[0]) { stations.delete(id); } else { stations.release(id); } }
                finally { forced.forEach(chunk -> space.setChunkForced(chunk.x, chunk.z, false)); }
            }
        };
        await(helper, () -> forced.stream().allMatch(chunk -> space.getChunkSource().getChunkNow(chunk.x, chunk.z) != null), () -> {
            fixture[0] = new SolarArrayFixture(space, pos); fixture[0].install(pos, 63); fixture[0].set(pos.above(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(!fixture[0].exposure.read(space, pos, fixture[0].array).available(), "Reservation became station authority");
            stations.commit(id); committed[0] = true; fixture[0].energy(0); fixture[0].tick();
            helper.assertTrue(fixture[0].array.environment().context() == 2 && fixture[0].array.environment().day() == 2
                    && fixture[0].array.actualCredit() == 126, "Operational station did not use orbit irradiance");
            // Removing Earth leaves child references invalid. Relocate to the leaf Moon before removing that orbit body.
            stations.foldWarpCredits(java.util.Map.of(id, 1));
            helper.assertTrue(stations.checkedRelocation(server, stations.find(id).orElseThrow(), CelestialIds.MOON_ID, 1)
                    == StationRegistrySavedData.CheckedUpdate.COMMITTED, "Station fixture relocation did not commit");
            helper.assertTrue(fixture[0].catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions().stream()
                    .filter(body -> !body.id().equals(CelestialIds.MOON_ID)).toList())), "Missing-orbit fixture catalog was rejected");
            fixture[0].energy(0); fixture[0].tick();
            helper.assertTrue(fixture[0].array.environment().context() == 3 && fixture[0].array.actualCredit() == 126, "Missing body was silently replaced by surface context");
            fixture[0].set(pos.above(), Blocks.STONE.defaultBlockState());
            fixture[0].form(); fixture[0].tick();
            helper.assertTrue(fixture[0].array.actualCredit() == 0 && !fixture[0].array.environment().sky()
                    && fixture[0].array.reason() == SolarGeneration.Reason.SKY_BLOCKED, "Station occlusion was ignored");
            restore.run(); helper.succeed();
        }, restore);
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void plugsShareOutputQuotaDisableAndStaleFacadeRules(GameTestHelper helper) {
        try (var fixture = fixture(helper, 63)) {
            fixture.energy(20_000);
            var left = fixture.port(-1); var right = fixture.port(1);
            helper.assertTrue(!left.canReceive() && left.receiveEnergy(100, false) == 0, "Output plug accepted input");
            helper.assertTrue(left.extractEnergy(1_000, true) == 1_000 && right.extractEnergy(1_000, true) == 1_000, "Simulation spent the quota");
            helper.assertTrue(left.extractEnergy(600, false) == 600 && right.extractEnergy(600, false) == 400
                    && left.extractEnergy(1, false) == 0 && fixture.array.energyStored() == 19_000, "Plugs exceeded their shared quota");
            fixture.enabled = false; fixture.tick();
            helper.assertTrue(fixture.array.energyStored() == 19_000 && fixture.array.reason() == SolarGeneration.Reason.DISABLED
                    && !left.canExtract() && right.extractEnergy(1, false) == 0, "Disabled array exported or discarded FE");
            fixture.enabled = true; fixture.array.invalidateCaps(); fixture.array.reviveCaps(); fixture.form();
            helper.assertTrue(!left.canExtract() && left.extractEnergy(1, false) == 0, "Captured facade survived a capability epoch");
            var plug = fixture.level.getBlockEntity(fixture.position.offset(-1, 0, 0));
            var captured = fixture.port(-1); plug.invalidateCaps(); fixture.tick();
            helper.assertTrue(fixture.array.actualCredit() == 0 && fixture.array.structure() == SolarArrayRules.Structure.PENDING
                    && captured.extractEnergy(1, false) == 0, "Revoked plug continued collection or retained authority");
            plug.reviveCaps(); fixture.form();
            helper.assertTrue(fixture.array.structure() == SolarArrayRules.Structure.FORMED, "Revoked plug did not re-form");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void pushAndPullShareQuotaAndReentrantReceiverCannotExtract(GameTestHelper helper) {
        try (var fixture = fixture(helper, 63)) {
            fixture.energy(20_000);
            var retained = fixture.port(1);
            helper.assertTrue(retained.extractEnergy(400, false) == 400, "Pull did not spend quota");
            BlockPos receiverPosition = fixture.position.offset(-1, 1, 0);
            fixture.set(receiverPosition, Blocks.FURNACE.defaultBlockState());
            var receiver = new Receiver(receiverPosition, () -> helper.assertTrue(retained.extractEnergy(100, false) == 0, "Receiver reentered donor"));
            fixture.level.setBlockEntity(receiver); fixture.form();
            // Formation may change with neighbor notifications. Capture the current facade for the callback.
            receiver.reenter = () -> helper.assertTrue(fixture.port(1).extractEnergy(100, false) == 0, "Current receiver reentered donor");
            fixture.catalogs.clear(); // No new credit; unavailable context must still permit retained output.
            fixture.tick();
            helper.assertTrue(receiver.received == 600 && fixture.array.energyStored() == 19_000
                    && fixture.port(-1).extractEnergy(1, false) == 0, "Push exceeded the remaining pull allowance or donor was not debited");
        }
        helper.succeed();
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void nativeSaveLoadAndUnsupportedRootRetainExactBufferMeaning(GameTestHelper helper) {
        try (var fixture = fixture(helper, 63)) {
            fixture.energy(17_777); var saved = fixture.array.saveWithFullMetadata();
            var restored = (SolarArrayBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(fixture.position, fixture.array.getBlockState(), saved);
            helper.assertTrue(restored != null && restored.energyStored() == 17_777 && restored.structure() == SolarArrayRules.Structure.PENDING, "Native save/load lost FE or trusted cached formation");
            var future = SolarSave.encode(17_777, SolarArrayRules.CAPACITY); future.putInt("schema", 2); future.putString("future", "keep");
            CompoundTag outer = new CompoundTag(); outer.put(SolarSave.ARRAY_ROOT, future); fixture.array.load(outer); fixture.tick();
            helper.assertTrue(fixture.array.repairRequired() && fixture.array.actualCredit() == 0
                    && fixture.array.saveWithoutMetadata().get(SolarSave.ARRAY_ROOT).equals(future), "Future root was discarded or generated energy");
            helper.assertTrue(!ModBlocks.SOLAR_ARRAY.get().onDestroyedByPlayer(fixture.array.getBlockState(), fixture.level, fixture.position,
                    helper.makeMockPlayer(), true, fixture.array.getBlockState().getFluidState()), "Ordinary removal destroyed a preserved root");
            fixture.energy(0);
        }
        helper.succeed();
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void nonAdmissibleArrayRootRefusesOutgoingChunk(GameTestHelper helper) {
        try (var fixture = fixture(helper, 1)) {
            CompoundTag large = new CompoundTag(); large.putByteArray("payload", new byte[4_096]);
            CompoundTag outer = new CompoundTag(); outer.put(SolarSave.ARRAY_ROOT, large); fixture.array.load(outer);
            var chunk = fixture.level.getChunkAt(fixture.position);
            try {
                helper.assertTrue(fixture.array.saveWithoutMetadata().get(SolarSave.ARRAY_ROOT) == large, "Non-admissible root was copied or omitted");
                var outgoing = ChunkSerializer.write(fixture.level, chunk); chunk.setUnsaved(false);
                boolean refused = false;
                try { MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, fixture.level, outgoing)); }
                catch (IllegalStateException expected) { refused = expected.getMessage().contains("non-admissible solar input"); }
                helper.assertTrue(refused && chunk.isUnsaved(), "Array unsafe root did not retain whole-chunk save protection");
            } finally { fixture.energy(0); GuardedChunkSaves.restoreGameTestFixture(fixture.level, chunk.getPos()); }
        }
        helper.succeed();
    }

    @GameTest(template = "solar_array_test", timeoutTicks = 40)
    public static void connectedScalarMenuOpensWithStructureAndNoInventoryMutation(GameTestHelper helper) {
        try (var fixture = fixture(helper, 63)) {
            var player = new net.minecraft.server.level.ServerPlayer(fixture.level.getServer(), fixture.level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "arrayMenu")) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return true; }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            fixture.level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.teleportTo(fixture.level, fixture.position.getX() + 0.5, fixture.position.getY(), fixture.position.getZ() + 0.5, 0, 0);
            try {
                fixture.energy(777);
                var menu = new SolarArrayMenu(ModMenuTypes.SOLAR_ARRAY.get(), 17, fixture.array);
                helper.assertTrue(menu.stillValid(player) && menu.slots.isEmpty() && menu.view().collectors() == 63 && menu.view().energy() == 777, "Scalar structure menu differs");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 7)); menu.clicked(-999, 0, ClickType.THROW, player);
                helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && player.getMainHandItem().getCount() == 7, "Scalar menu mutated inventory");
                var interaction = ModBlocks.SOLAR_ARRAY.get().use(fixture.array.getBlockState(), fixture.level, fixture.position, player,
                        InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(fixture.position), Direction.UP, fixture.position, false));
                helper.assertTrue(interaction == InteractionResult.CONSUME && player.containerMenu instanceof SolarArrayMenu, "Registered interaction did not open the array menu");
                player.teleportTo(fixture.level, fixture.position.getX() + 10.5, fixture.position.getY(), fixture.position.getZ() + 0.5, 0, 0);
                helper.assertTrue(!menu.stillValid(player) && !menu.stillValid(helper.makeMockPlayer()), "Menu bypassed repeated actor/distance checks");
            } finally { player.closeContainer(); fixture.level.getServer().getPlayerList().remove(player); }
        }
        helper.succeed();
    }

    private static void await(GameTestHelper helper, BooleanSupplier ready, Runnable verify, Runnable restore) {
        await(helper, new GameTestTickPacer(helper.getLevel().getServer()), ready, verify, restore);
    }
    private static void await(GameTestHelper helper, GameTestTickPacer pacer, BooleanSupplier ready, Runnable verify, Runnable restore) {
        helper.runAfterDelay(1, () -> {
            try {
                if (!ready.getAsBoolean()) {
                    helper.assertTrue(helper.getTick() < 75, "Solar fixture readiness was not published within its tick budget");
                    pacer.pace(); await(helper, pacer, ready, verify, restore); return;
                }
                verify.run();
            } catch (RuntimeException | Error failed) { restore.run(); throw failed; }
        });
    }
    private static final class Receiver extends net.minecraft.world.level.block.entity.BlockEntity {
        private int received;
        private Runnable reenter;
        private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> port =
                net.minecraftforge.common.util.LazyOptional.of(() -> new net.minecraftforge.energy.IEnergyStorage() {
                    @Override public int receiveEnergy(int maximum, boolean simulate) {
                        reenter.run(); if (!simulate) { received += maximum; } return maximum;
                    }
                    @Override public int extractEnergy(int maximum, boolean simulate) { return 0; }
                    @Override public int getEnergyStored() { return received; }
                    @Override public int getMaxEnergyStored() { return 20_000; }
                    @Override public boolean canExtract() { return false; }
                    @Override public boolean canReceive() { return true; }
                });
        private Receiver(BlockPos position, Runnable reenter) {
            super(net.minecraft.world.level.block.entity.BlockEntityType.FURNACE, position, Blocks.FURNACE.defaultBlockState()); this.reenter = reenter;
        }
        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> capability, Direction side) {
            return capability == ForgeCapabilities.ENERGY ? port.cast() : super.getCapability(capability, side);
        }
        @Override public void invalidateCaps() { super.invalidateCaps(); port.invalidate(); }
    }
    private SolarArrayGameTests() { }
}
