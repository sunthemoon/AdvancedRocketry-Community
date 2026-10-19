package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFluidCargoFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.FluidFuelLoaderFixtures.clear;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Raw preservation and native terrain denial are separate from ordinary unsupported-data protection. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFluidCargoProtectionGameTests {
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void protectedRootsRefuseBreakExplosionExtractionAndLiveReplacement(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var at = helper.absolutePos(pos); var server = level.getServer();
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Protect", level, at.east(2),
                    new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                for (var role : RocketCargoRole.values()) {
                    var future = owned(owner.getUUID(), role, 1_000); future.putInt("schema_version", 2);
                    var invalid = owned(owner.getUUID(), role, 1_000); invalid.putInt("unknown", 1);
                    var oversize = owned(owner.getUUID(), role, 1_000); oversize.putByteArray("payload", new byte[40 * 1_024]);
                    var absent = owned(owner.getUUID(), role, 1_000); absent.getCompound("tank").putString("FluidName", "absentmod:fluid");
                    var mismatch = owned(owner.getUUID(), role == RocketCargoRole.LOAD ? RocketCargoRole.UNLOAD : RocketCargoRole.LOAD, 1_000);
                    var marked = new RocketFluidCargoRoot(owner.getUUID(), UUID.randomUUID(), role, null, tank(1_000),
                            new RocketEscrowSection(UUID.randomUUID(), 1, List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, role.markerRole(), 1)))).encode();
                    for (Tag raw : List.of(future, invalid, oversize, absent, mismatch, marked)) {
                        var device = place(helper, pos, role, raw);
                        helper.assertTrue(device.protectedFromRemoval() && raw.equals(device.saveWithoutMetadata().get(RocketFluidCargoRoot.KEY)),
                                "Protected root changed or became removable");
                        if (raw == marked) {
                            helper.assertTrue(port(device).fill(water(1_000), FluidAction.EXECUTE) == 0
                                    && port(device).drain(1_000, FluidAction.EXECUTE).isEmpty(), "Marked device exposed resources");
                        } else { helper.assertTrue(!device.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "Unsupported capability exposed"); }
                        device.load(outer(owned(owner.getUUID(), role, 0)));
                        helper.assertTrue(raw.equals(device.saveWithoutMetadata().get(RocketFluidCargoRoot.KEY)), "Live load replaced protected data");
                        var breaking = new BlockEvent.BreakEvent(level, at, device.getBlockState(), owner);
                        MinecraftForge.EVENT_BUS.post(breaking);
                        helper.assertTrue(breaking.isCanceled() && !owner.gameMode.destroyBlock(at), "Player removed protected device");
                        var explosion = new Explosion(level, null, at.getX() + .5, at.getY() + .5, at.getZ() + .5, 1,
                                false, Explosion.BlockInteraction.DESTROY);
                        explosion.getToBlow().add(at);
                        MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
                        device.getBlockState().onBlockExploded(level, at, explosion);
                        helper.assertTrue(explosion.getToBlow().isEmpty() && level.getBlockEntity(at) == device
                                && Block.getDrops(device.getBlockState(), level, at, device).isEmpty(), "Explosion or loot lost protected data");
                        // Commands remain a recorded residual: no claim of prevented salvage.
                        if (raw == marked || raw == future) {
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                                    "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " minecraft:air");
                            helper.assertTrue(level.getBlockState(at).isAir() && FluidFuelLoaderFixtures.drops(level, at).isEmpty(),
                                    "Raw replacement salvaged a carrier");
                        }
                        clear(level, at);
                    }
                }
            } finally { clear(level, at); server.getPlayerList().remove(owner); }
        }, pos);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void nativeWorkBudgetExcessPreservesRawWithoutDenyingTerrain(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4); var level = helper.getLevel(); var at = helper.absolutePos(pos);
        try {
            for (var role : RocketCargoRole.values()) {
                var raw = owned(UUID.randomUUID(), role, 1_000); raw.putByteArray("opaque", new byte[2_097_153]);
                var device = place(helper, pos, role, raw);
                helper.assertTrue(device.unsupportedRoot() && !device.unkeepable() && device.heldRoot().orElseThrow() == raw
                        && device.saveWithoutMetadata().get(RocketFluidCargoRoot.KEY) == raw, "Work budget triggered copying or native denial");
                RocketFluidCargoProtection.chunkSave(save(level, at, device.saveWithFullMetadata()));
                helper.assertTrue(level.getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow().reason(new ChunkPos(at).toLong()).isEmpty(),
                        "Work-budget excess denied terrain");
                clear(level, at);
            }
        } finally { clear(level, at); }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void observedNativeVetoSurvivesUnloadedAndReplacedChunkObjects(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4); var level = helper.getLevel(); var at = helper.absolutePos(pos);
        try {
            for (var role : RocketCargoRole.values()) {
                var raw = incompatible();
                var device = place(helper, pos, role, raw);
                helper.assertTrue(device.unkeepable() && raw.equals(device.saveWithoutMetadata().get(RocketFluidCargoRoot.KEY)),
                        "Observed native incompatibility was not retained");
                helper.assertTrue(Float.floatToRawIntBits(device.saveWithoutMetadata().getCompound(RocketFluidCargoRoot.KEY)
                        .getFloat("opaque")) == 0x7fc00001, "Noncanonical native bits were normalized");
                var serialized = device.saveWithFullMetadata();
                clear(level, at);
                var replacement = new LevelChunk(level, new ChunkPos(at)); replacement.setUnsaved(false);
                refuse(helper, () -> GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(replacement, level, new CompoundTag())));
                helper.assertTrue(replacement.isUnsaved(), "Denial did not dirty the replacement chunk object");
                GuardedChunkSaves.restoreGameTestFixture(level, new ChunkPos(at));
                // With no live device or cached denial, the serialized-root hook independently observes the veto.
                refuse(helper, () -> RocketFluidCargoProtection.chunkSave(save(level, at, serialized)));
                GuardedChunkSaves.restoreGameTestFixture(level, new ChunkPos(at));
            }
        } finally { clear(level, at); GuardedChunkSaves.restoreGameTestFixture(level, new ChunkPos(at)); }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void isolatedLevelAttachmentPinsThe256CoordinateSaturationBoundary(GameTestHelper helper) {
        // Saturation intentionally has no online reset. Exercise a fresh production Level attachment without
        // installing it over the running world's capability or poisoning the other GameTests' terrain saves.
        var attach = new AttachCapabilitiesEvent<Level>(Level.class, helper.getLevel());
        GuardedChunkSaves.attach(attach);
        var provider = attach.getCapabilities().get(ModIdentity.id("guarded_chunk_saves"));
        var state = provider.getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow();
        var actual = helper.getLevel().getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow();
        helper.assertTrue(state != actual && !SatelliteBayRoot.nativelyKeepable(incompatible()), "Fixture is not isolated/incompatible");
        for (int i = 0; i < 256; i++) {
            state.deny(ChunkPos.asLong(i, -1), RocketFluidCargoBlockEntity.SAVE_REFUSAL);
            helper.assertTrue(state.size() == i + 1 && state.reason(ChunkPos.asLong(-1, -1)).isEmpty(), "Premature saturation at " + i);
        }
        state.deny(ChunkPos.asLong(255, -1), RocketFluidCargoBlockEntity.SAVE_REFUSAL);
        helper.assertTrue(state.size() == 256 && state.reason(ChunkPos.asLong(-1, -1)).isEmpty(), "Duplicate denial consumed capacity");
        state.deny(ChunkPos.asLong(256, -1), RocketFluidCargoBlockEntity.SAVE_REFUSAL);
        helper.assertTrue(state.size() == 256 && state.reason(ChunkPos.asLong(-1, -1)).orElseThrow().contains("capacity exhausted"),
                "The next distinct denial did not saturate the Level at capacity 256");
        state.close();
        helper.assertTrue(state.size() == 0 && state.reason(0).orElseThrow().contains("lifecycle ended"), "Close resurrected save authority");
        helper.succeed();
    }

    private static CompoundTag incompatible() {
        var raw = new CompoundTag(); raw.putInt("schema_version", 2);
        raw.put("opaque", FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)));
        return raw;
    }
    private static ChunkDataEvent.Save save(net.minecraft.server.level.ServerLevel level, BlockPos at, CompoundTag entity) {
        var root = new CompoundTag(); var entities = new ListTag(); entities.add(entity); root.put("block_entities", entities);
        return new ChunkDataEvent.Save(level.getChunkAt(at), level, root);
    }
    private static void refuse(GameTestHelper helper, Runnable action) {
        try { action.run(); helper.fail("Native-incompatible cargo save was admitted"); }
        catch (IllegalStateException refused) { helper.assertTrue(RocketFluidCargoBlockEntity.SAVE_REFUSAL.equals(refused.getMessage()), "Wrong veto reason"); }
    }
    private RocketFluidCargoProtectionGameTests() { }
}
