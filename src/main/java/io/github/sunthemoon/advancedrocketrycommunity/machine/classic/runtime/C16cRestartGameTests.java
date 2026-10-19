package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestChunkUnloadPump;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W64 (W62 S1): mid-job restarts of the crystallizer, chemical reactor and precision laser etcher. Every block entity of
 * the machine is replaced by a fresh one decoded from its saved NBT, as a chunk load does; the etcher also crosses a real
 * chunk unload and reload.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class C16cRestartGameTests {
    @GameTest(template = "empty", batch = "w64_crystal_restart", timeoutTicks = 30)
    public static void crystallizerMidJobRestartKeepsExactResourcesFeAndProgress(GameTestHelper helper) {
        restart(helper, "crystallizer", "crystallizing_dilithium", 101);
    }
    @GameTest(template = "empty", batch = "w64_chemical_restart", timeoutTicks = 30)
    public static void chemicalReactorMidJobRestartKeepsExactFluidsFeAndProgress(GameTestHelper helper) {
        restart(helper, "chemical_reactor", "chemical_reacting_rocket_fuel", 41);
    }
    @GameTest(template = "empty", batch = "w64_etcher_restart", timeoutTicks = 30)
    public static void etcherMidJobRestartKeepsExactResourcesLensFeAndProgress(GameTestHelper helper) {
        restart(helper, "precision_laser_etcher", "laser_etching_basic_circuit_plate", 301);
    }
    private static void restart(GameTestHelper helper, String kind, String id, int ticks) {
        try (var fixture = new C16cMachineFixture(helper, kind, Direction.NORTH)) {
            var recipe = fixture.recipe(id); fixture.seed(recipe);
            ItemStack lens = markLens(fixture, recipe);
            fixture.tick(ticks);
            var old = fixture.controller; var job = old.checkpoint().job().orElseThrow();
            int progress = old.progressTicks(), stored = old.storedEnergy(); long consumed = old.consumedEnergy();
            CompoundTag resources = fixture.resources();
            helper.assertTrue(progress > 0 && progress < recipe.rows().processingTicks() && old.reason() == ClassicWait.RUNNING
                    && consumed + stored == fixture.injected, "The job was not running mid-batch with conserved FE: " + old.reason());
            reloadBlockEntities(helper, fixture);
            var fresh = fixture.controller;
            helper.assertTrue(fresh != old && old.isRemoved() && !fresh.repairRequired(), "The controller was not replaced by a loadable fresh entity");
            helper.assertTrue(fresh.checkpoint().job().orElseThrow().equals(job) && fresh.progressTicks() == progress
                    && fresh.consumedEnergy() == consumed && fresh.storedEnergy() == stored && resources.equals(fixture.resources())
                    && fresh.checkpoint().batchOrdinal() == 0, "The restart changed the job, its progress, FE or resources");
            fixture.tick(recipe.rows().processingTicks() - progress); fixture.assertCompleted(recipe);
            if (lens != null) { helper.assertTrue(ItemStack.matches(lens, fixture.input().getStackInSlot(3)), "The restart lost the lens payload"); }
            var completed = fixture.resources(); fixture.tick(5);
            helper.assertTrue(completed.equals(fixture.resources()), "The restarted machine duplicated its batch");
        }
        helper.succeed();
    }
    /** Gives the etcher's retained lens a custom payload, so that exact item data must survive; other kinds return null. */
    private static ItemStack markLens(C16cMachineFixture fixture, ClassicMachineRecipe recipe) {
        if (!recipe.retainsLens()) { return null; }
        ItemStack lens = fixture.input().getStackInSlot(3); lens.getOrCreateTag().putString("w64_marker", "kept");
        var inputHatch = fixture.hatches("item_input").get(0);
        fixture.replaceBanks(List.of(fixture.controller.checkpoint().resources().bank(inputHatch.bankKey()).orElseThrow().withItem(3, lens)));
        return lens;
    }
    /** Saves every block entity of the machine, then installs a fresh one decoded from that NBT, as a chunk load does. */
    private static void reloadBlockEntities(GameTestHelper helper, C16cMachineFixture fixture) {
        var level = helper.getLevel(); Map<BlockPos, CompoundTag> saved = new LinkedHashMap<>();
        for (BlockPos position : fixture.positions) {
            BlockEntity entity = level.getBlockEntity(position);
            if (entity != null) { saved.put(position, entity.saveWithFullMetadata()); }
        }
        helper.assertTrue(saved.containsKey(fixture.origin) && saved.size() > 1, "The machine has no controller or no hatches to reload");
        saved.forEach((position, tag) -> {
            BlockEntity old = level.getBlockEntity(position);
            BlockEntity fresh = BlockEntity.loadStatic(position, level.getBlockState(position), tag.copy());
            helper.assertTrue(fresh != null && fresh.getType() == old.getType(), "Saved block entity did not load at " + position.toShortString());
            level.setBlockEntity(fresh);
            helper.assertTrue(old.isRemoved() && level.getBlockEntity(position) == fresh, "Fresh block entity was not installed");
            helper.assertTrue(fresh.saveWithFullMetadata().equals(tag), "Block entity NBT did not round-trip exactly at " + position.toShortString());
        });
        fixture.rebind();
    }

    @GameTest(template = "empty", batch = "w64_etcher_unload", timeoutTicks = 900)
    public static void etcherJobCrossesARealChunkUnloadAndReloadWithExactAccounting(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper); var level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        // An isolated chunk far from every test structure; the whole 3x3x3 pattern sits inside it.
        BlockPos origin = new BlockPos(((base.getX() + 1_000_000) & ~15) + 8, 200, ((base.getZ() - 1_000_000) & ~15) + 8);
        ChunkPos chunk = new ChunkPos(origin);
        helper.assertTrue(!level.hasChunkAt(origin), "The isolated fixture chunk is already loaded");
        var fixture = new C16cMachineFixture(helper, "precision_laser_etcher", Direction.NORTH, origin);
        AtomicBoolean unloaded = new AtomicBoolean(), closed = new AtomicBoolean();
        Consumer<ChunkEvent.Unload> witness = event -> {
            if (event.getLevel() == level && event.getChunk().getPos().equals(chunk)) { unloaded.set(true); }
        };
        MinecraftForge.EVENT_BUS.addListener(witness);
        Runnable cleanup = () -> {
            if (closed.compareAndSet(false, true)) { MinecraftForge.EVENT_BUS.unregister(witness); fixture.close(); }
        };
        try {
            helper.assertTrue(fixture.positions.stream().allMatch(position -> new ChunkPos(position).equals(chunk)),
                    "The pattern crosses a chunk boundary");
            helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.controller.checkpoint().batchOrdinal() == 0,
                    "The isolated fixture was not fresh");
            var recipe = fixture.recipe("laser_etching_basic_circuit_plate"); fixture.seed(recipe);
            ItemStack lens = markLens(fixture, recipe);
            // Native server ticks run the job on the seeded FE (two full plugs) until it pauses for energy mid-batch.
            await(driver, 120, "the etcher to pause for energy", () -> fixture.controller.reason() == ClassicWait.ENERGY, null, () -> {
                var original = fixture.controller; var job = original.checkpoint().job().orElseThrow();
                int progress = original.progressTicks(), stored = original.storedEnergy(); long consumed = original.consumedEnergy();
                CompoundTag resources = fixture.resources();
                helper.assertTrue(progress > 0 && progress < recipe.rows().processingTicks() && stored < recipe.rows().energyPerTick()
                        && consumed + stored == fixture.injected, "The paused job did not conserve FE mid-batch");
                Map<BlockPos, BlockEntity> parts = new LinkedHashMap<>(); Map<BlockPos, CompoundTag> partRoots = new LinkedHashMap<>();
                for (BlockPos position : fixture.positions) {
                    if (level.getBlockEntity(position) instanceof ClassicHatchBlockEntity hatch) {
                        parts.put(position, hatch); partRoots.put(position, hatch.saveWithFullMetadata());
                    }
                }
                fixture.releaseChunks();
                // Never look up a block entity while waiting: that would load the chunk again. Pacing lets background
                // generation finish, but cannot drain ChunkMap's separate unload queue when the tick has no spare time.
                // Pump only already-ready vanilla callbacks; they still perform the real save, event and removal.
                var pacer = new GameTestTickPacer(level.getServer());
                await(driver, 600, "the fixture chunk to unload", () -> {
                    if (!unloaded.get()) { GameTestChunkUnloadPump.drainReady(level); }
                    return unloaded.get() && !level.hasChunkAt(origin) && original.isRemoved();
                }, pacer, () -> {
                    helper.assertTrue(parts.values().stream().allMatch(BlockEntity::isRemoved), "A hatch outlived its chunk");
                    fixture.retainChunks();
                    fixture.rebind();
                    var reloaded = fixture.controller;
                    helper.assertTrue(reloaded != null && reloaded != original && !reloaded.repairRequired(), "The chunk reload did not restore a fresh controller");
                    helper.assertTrue(reloaded.checkpoint().job().orElseThrow().equals(job) && reloaded.progressTicks() == progress
                            && reloaded.consumedEnergy() == consumed && resources.equals(fixture.resources())
                            && reloaded.checkpoint().batchOrdinal() == 0
                            && reloaded.controllerState().machineInstanceId().equals(original.controllerState().machineInstanceId()),
                            "The chunk reload changed the job, its progress, FE or resources");
                    partRoots.forEach((position, root) -> {
                        BlockEntity part = level.getBlockEntity(position);
                        helper.assertTrue(part != parts.get(position) && part.saveWithFullMetadata().equals(root),
                                "A hatch did not reload exactly at " + position.toShortString());
                    });
                    // onLoad runs on the next Level tick; the production manager then re-forms the reloaded controller.
                    await(driver, 40, "the reloaded etcher to re-form", reloaded::formed, null, () -> {
                        try {
                            helper.assertTrue(reloaded.progressTicks() == progress && reloaded.storedEnergy() == stored
                                    && resources.equals(fixture.resources()), "Re-formation changed the unpowered job");
                            fixture.tick(recipe.rows().processingTicks() - progress); fixture.assertCompleted(recipe);
                            helper.assertTrue(ItemStack.matches(lens, fixture.input().getStackInSlot(3)), "The chunk reload lost the lens payload");
                        } finally { cleanup.run(); }
                        helper.succeed();
                    }, cleanup);
                }, cleanup);
            }, cleanup);
        } catch (RuntimeException | Error failure) {
            cleanup.run(); throw failure;
        }
    }
    /**
     * One continuation per tick on the test-owned driver; it never schedules vanilla work inside a callback. A non-null
     * {@code pacer} holds each unchanged tick to a server tick's length.
     */
    private static void await(GameTestTickDriver driver, int attempts, String what, BooleanSupplier ready, GameTestTickPacer pacer,
            Runnable next, Runnable cleanup) {
        driver.after(1, () -> {
            boolean done;
            try {
                done = ready.getAsBoolean();
                if (!done && attempts <= 1) { throw new GameTestAssertException("Timed out waiting for " + what); }
                if (done) { next.run(); } else if (pacer != null) { pacer.pace(); }
            } catch (RuntimeException | Error failure) {
                cleanup.run(); throw failure;
            }
            if (!done) { await(driver, attempts - 1, what, ready, pacer, next, cleanup); }
        });
    }
    private C16cRestartGameTests() { }
}
