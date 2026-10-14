package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Lifecycle-bound access to the torch rules: the reloaded catalog and the running server's service. Both are reset
 * when the server stops; no world position is held here.
 */
public final class CombustionTorches {
    /** Lit torches the rules govern; the vanilla standing and wall torches by default. */
    public static final TagKey<Block> TORCHES = TagKey.create(Registries.BLOCK, ModIdentity.id("combustion_torches"));
    /** Items whose use needs combustion air and that relight an unlit torch. */
    public static final TagKey<Item> FIRE_STARTERS = TagKey.create(Registries.ITEM,
            ModIdentity.id("combustion_fire_starters"));

    private static final AtomicReference<TorchPairCatalog> CATALOG = new AtomicReference<>(TorchPairCatalog.BUILT_IN);
    private static volatile TorchCombustionService service;
    private static volatile Fixture fixture;

    private CombustionTorches() {
    }

    public static TorchPairCatalog catalog() {
        Fixture current = fixture;
        return current == null ? CATALOG.get() : current.catalog();
    }

    static void accept(TorchPairCatalog catalog) {
        CATALOG.set(catalog);
    }

    /** A lit block the rules govern: tagged, and never a thermite or unlit torch. */
    public static boolean isCombustionTorch(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof UnlitTorchBlock || block instanceof UnlitWallTorchBlock || isThermite(block)) {
            return false;
        }
        Fixture current = fixture;
        return state.is(TORCHES) || current != null && current.tagged().contains(block);
    }

    /** The catalog pair that maps this block, if the block is supported at all. */
    public static Optional<TorchPair> pairFor(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id == null ? Optional.empty() : catalog().forBlock(id);
    }

    static boolean isThermite(Block block) {
        return block == ModBlocks.THERMITE_TORCH.get() || block == ModBlocks.THERMITE_WALL_TORCH.get();
    }

    static Optional<TorchCombustionService> service() {
        return Optional.ofNullable(service);
    }

    /** Observable per-Level queue counts, while the server runs. */
    public static Optional<TorchQueue.Metrics> metrics(ServerLevel level) {
        TorchCombustionService current = service;
        return current == null ? Optional.empty() : current.metrics(level.dimension());
    }

    static synchronized void start() {
        service = new TorchCombustionService();
    }

    static synchronized void stop() {
        TorchCombustionService current = service;
        service = null;
        if (current != null) {
            current.clear();
        }
        fixture = null;
        CATALOG.set(TorchPairCatalog.BUILT_IN);
    }

    /** Disposable GameTest fixtures only: extra tagged blocks and catalog pairs until {@link #clearFixture()}. */
    public static void installFixture(MinecraftServer server, Set<Block> tagged, List<TorchPair> pairs) {
        if (!(server instanceof GameTestServer)) {
            throw new IllegalStateException("Torch fixtures require the actual GameTestServer");
        }
        fixture = new Fixture(Set.copyOf(tagged), CATALOG.get().with(pairs));
    }

    public static void clearFixture() {
        fixture = null;
    }

    /** Fills the queue of one Level for a GameTest of the capacity rule. */
    public static int fillQueueForTest(MinecraftServer server, ServerLevel level, long firstPosition) {
        if (!(server instanceof GameTestServer)) {
            throw new IllegalStateException("Torch fixtures require the actual GameTestServer");
        }
        return service().map(current -> current.fillForTest(level, firstPosition)).orElse(0);
    }

    private record Fixture(Set<Block> tagged, TorchPairCatalog catalog) {
    }
}
