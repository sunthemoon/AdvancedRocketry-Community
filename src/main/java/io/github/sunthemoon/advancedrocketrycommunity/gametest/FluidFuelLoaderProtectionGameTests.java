package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.FluidFuelLoaderFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W176: runtime rejection and observed native incompatibility have different terrain-save outcomes. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidFuelLoaderProtectionGameTests {
    @GameTest(template = TEMPLATE, batch = "fluid_fuel_loader_save_keepable", timeoutTicks = 40)
    public static void losslessAndWorkLimitedRootsSaveVerbatimWithoutDenial(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4); var level = helper.getLevel(); var at = helper.absolutePos(pos);
        var valid = owned(UUID.randomUUID(), LoaderTank.EMPTY);
        var future = valid.copy(); future.putInt("schema_version", 2);
        var invalid = valid.copy(); invalid.putString("unknown", "kept");
        var oversized = valid.copy(); oversized.putByteArray("opaque", new byte[40_000]);
        var workLimit = valid.copy(); workLimit.putByteArray("opaque", new byte[2_097_153]);
        try {
            for (var raw : List.of(valid, future, invalid, oversized, workLimit)) {
                var device = place(helper, pos, raw);
                helper.assertTrue(!device.unkeepable() && raw.equals(device.saveWithoutMetadata().get(LoaderRoot.KEY)),
                        "Keepable root changed or denied native saving");
                if (raw == oversized || raw == workLimit) {
                    helper.assertTrue(device.heldRoot().orElseThrow() == raw
                            && device.saveWithoutMetadata().get(LoaderRoot.KEY) == raw, "Oversized data was copied");
                }
                FluidFuelLoaderProtection.chunkSave(save(level, at, device.saveWithFullMetadata()));
                helper.assertTrue(level.getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow()
                        .reason(new ChunkPos(at).toLong()).isEmpty(), "Runtime/work budget denied terrain");
                clear(level, at);
            }
        } finally { clear(level, at); }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "fluid_fuel_loader_save_incompatible", timeoutTicks = 40)
    public static void liveAndDeferredLoadDenialsSurviveUnloadAndSerializedRoots(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4); var level = helper.getLevel(); var at = helper.absolutePos(pos);
        try {
            for (boolean deferred : new boolean[] {false, true}) {
                var raw = new CompoundTag(); raw.putInt("schema_version", 2);
                raw.put("opaque", FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)));
                FluidFuelLoaderBlockEntity device;
                if (deferred) {
                    device = new FluidFuelLoaderBlockEntity(at, ModBlocks.FLUID_FUEL_LOADER.get().defaultBlockState());
                    device.load(outer(raw)); device.setLevel(level); device.onLoad();
                } else { device = place(helper, pos, raw); }
                helper.assertTrue(device.unkeepable() && Float.floatToRawIntBits(device.saveWithoutMetadata()
                        .getCompound(LoaderRoot.KEY).getFloat("opaque")) == 0x7fc00001, "Native incompatibility was normalized");
                var serialized = device.saveWithFullMetadata();
                device.onChunkUnloaded(); device.setRemoved(); clear(level, at);
                var replacement = new LevelChunk(level, new ChunkPos(at)); replacement.setUnsaved(false);
                refuse(helper, () -> GuardedChunkSaves.beforeSave(new ChunkDataEvent.Save(replacement, level, new CompoundTag())));
                helper.assertTrue(replacement.isUnsaved(), "Denial did not dirty the replacement chunk");
                GuardedChunkSaves.restoreGameTestFixture(level, new ChunkPos(at));
                refuse(helper, () -> FluidFuelLoaderProtection.chunkSave(save(level, at, serialized)));
                GuardedChunkSaves.restoreGameTestFixture(level, new ChunkPos(at));
            }
        } finally { clear(level, at); GuardedChunkSaves.restoreGameTestFixture(level, new ChunkPos(at)); }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "fluid_fuel_loader_save_saturation", timeoutTicks = 20)
    public static void isolatedProductionAttachmentPins256CoordinatesAnd257thSaturation(GameTestHelper helper) {
        // Never saturate the running GameTest world's capability; production has no online reset.
        var attach = new AttachCapabilitiesEvent<Level>(Level.class, helper.getLevel());
        GuardedChunkSaves.attach(attach);
        var state = attach.getCapabilities().get(ModIdentity.id("guarded_chunk_saves"))
                .getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow();
        helper.assertTrue(state != helper.getLevel().getCapability(GuardedChunkSaves.CAPABILITY).resolve().orElseThrow(),
                "Saturation fixture must be isolated");
        for (int i = 0; i < 256; i++) {
            state.deny(ChunkPos.asLong(i, -1), FluidFuelLoaderBlockEntity.SAVE_REFUSAL);
            helper.assertTrue(state.size() == i + 1 && state.reason(ChunkPos.asLong(-1, -1)).isEmpty(), "Premature saturation");
            helper.assertTrue(state.reason(ChunkPos.asLong(i, -1)).orElseThrow().equals(FluidFuelLoaderBlockEntity.SAVE_REFUSAL),
                    "Denied coordinate lost its loader reason");
        }
        state.deny(ChunkPos.asLong(255, -1), FluidFuelLoaderBlockEntity.SAVE_REFUSAL);
        helper.assertTrue(state.size() == 256 && state.reason(ChunkPos.asLong(-1, -1)).isEmpty(), "Duplicate consumed capacity");
        state.deny(ChunkPos.asLong(256, -1), FluidFuelLoaderBlockEntity.SAVE_REFUSAL);
        helper.assertTrue(state.size() == 256 && state.reason(ChunkPos.asLong(-1, -1)).orElseThrow().contains("capacity exhausted"),
                "257th distinct denial did not saturate the Level");
        state.close();
        helper.assertTrue(state.size() == 0 && state.reason(0).orElseThrow().contains("lifecycle ended"), "Close revived save authority");
        helper.succeed();
    }

    private static ChunkDataEvent.Save save(ServerLevel level, BlockPos at, CompoundTag entity) {
        var root = new CompoundTag(); var entities = new ListTag(); entities.add(entity); root.put("block_entities", entities);
        return new ChunkDataEvent.Save(level.getChunkAt(at), level, root);
    }
    private static void refuse(GameTestHelper helper, Runnable action) {
        try { action.run(); helper.fail("Native-incompatible loader save was admitted"); }
        catch (IllegalStateException refused) {
            helper.assertTrue(FluidFuelLoaderBlockEntity.SAVE_REFUSAL.equals(refused.getMessage()), "Wrong save refusal");
        }
    }
    private FluidFuelLoaderProtectionGameTests() { }
}
