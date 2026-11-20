package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStartedEvent;

/** W417 A1 serialized restart fixtures; these do not claim a native server-process restart. */
final class EndgameRestartFixtures {
    private EndgameRestartFixtures() { }

    /** A single-test batch may discard runtime caches, but must retain the entire shared saved root. */
    static BlockEntity[] restart(GameTestHelper helper, BlockEntity... before) {
        var server = helper.getLevel().getServer();
        var service = EndgameRuntime.operational().orElseThrow();
        var data = EndgameSavedData.get(server);
        CompoundTag root = binaryCopy(data.save(new CompoundTag()));
        CompoundTag[] tags = new CompoundTag[before.length];
        for (int i = 0; i < before.length; i++) {
            tags[i] = binaryCopy(before[i].saveWithFullMetadata());
        }
        EndgameSavedData restoredData = EndgameSavedData.load(root);
        helper.assertTrue(restoredData != data && restoredData.operational()
                        && restoredData.view() != data.view()
                        && restoredData.save(new CompoundTag()).equals(root),
                "The current endgame SavedData did not decode into an equivalent fresh root");
        for (BlockEntity entity : before) {
            entity.onChunkUnloaded();
            entity.getLevel().removeBlockEntity(entity.getBlockPos());
        }
        // Reuse the event-bus owner, not its old data/index/observations or device admission/budget caches.
        EndgameRuntime.devices().orElseThrow().clear();
        service.clear();
        server.overworld().getDataStorage().set(EndgameSavedData.DATA_NAME, restoredData);
        service.onServerStarted(new ServerStartedEvent(server));
        helper.assertTrue(service.root().orElseThrow() == restoredData.view(), "The service still uses the old root");
        BlockEntity[] after = new BlockEntity[before.length];
        for (int i = 0; i < before.length; i++) {
            ServerLevel level = (ServerLevel) before[i].getLevel();
            BlockPos pos = before[i].getBlockPos();
            BlockState state = level.getBlockState(pos);
            after[i] = BlockEntity.loadStatic(pos, state, tags[i]);
            helper.assertTrue(after[i] != null && after[i] != before[i]
                            && after[i].getClass() == before[i].getClass(), "The block entity was not reconstructed");
            level.setBlockEntity(after[i]);
            helper.assertTrue(after[i].saveWithoutMetadata().getCompound("endgame")
                            .equals(tags[i].getCompound("endgame")), "The fresh block entity changed its saved root");
        }
        return after;
    }

    private static CompoundTag binaryCopy(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                NbtIo.write(tag, out);
            }
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                return NbtIo.read(in);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("The restart fixture could not round-trip binary NBT", failure);
        }
    }

    static void cleanup(GameTestHelper helper, Runnable close) {
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) { }
                @Override public void testPassed(GameTestInfo info) { close.run(); }
                @Override public void testFailed(GameTestInfo info) { close.run(); }
            });
        } catch (ReflectiveOperationException failure) {
            close.run();
            throw new IllegalStateException("Could not attach restart fixture cleanup", failure);
        }
    }

    /** Reuses the owned, ticketed station fixture; replaces only its test structure with the generator pattern. */
    static BlackHoleGeneratorBlockEntity generator(OrbitalLaserDrillGameTests.Fixture fixture) {
        fixture.space.setBlockAndUpdate(fixture.controller, Blocks.AIR.defaultBlockState());
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = 0; z <= 2; z++) {
                    BlockState state = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
                    if (y == 0 && (x == -1 && z == 1 || x == 1 && z == 1 || x == 0 && z == 2)) {
                        state = Blocks.OBSIDIAN.defaultBlockState();
                    } else if (y == 0 && x == 0 && z == 1) {
                        state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                    } else if (y == 0 && x == 0 && z == 0) {
                        state = ModBlocks.BLACK_HOLE_GENERATOR.get().defaultBlockState()
                                .setValue(BlackHoleGeneratorBlock.FACING, Direction.NORTH);
                    }
                    fixture.space.setBlockAndUpdate(fixture.controller.offset(x, y, z), state);
                }
            }
        }
        BlackHoleGeneratorBlockEntity generator = (BlackHoleGeneratorBlockEntity)
                fixture.space.getBlockEntity(fixture.controller);
        generator.assignOwner(fixture.owner);
        return generator;
    }

    /** Fixture.close is drill-specific, so detach a generator before asking it to clear its owned structure. */
    static void closeGenerator(OrbitalLaserDrillGameTests.Fixture fixture) {
        if (fixture.space.getBlockEntity(fixture.controller) instanceof BlackHoleGeneratorBlockEntity generator) {
            for (int slot = 0; slot < generator.fuel().getSlots(); slot++) {
                generator.fuel().setStackInSlot(slot, ItemStack.EMPTY);
            }
            fixture.space.setBlockAndUpdate(fixture.controller, Blocks.AIR.defaultBlockState());
        }
        fixture.close();
    }
}
