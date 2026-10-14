package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * The extinguished torch's {@code arce_unlit_torch} identity. A root that does not decode is retained unchanged, and a
 * decoded identity that no longer resolves stays in place; both are quarantined (ADR-066 W33 note, R-034).
 */
public final class UnlitTorchBlockEntity extends BlockEntity {
    /** The loot table's dynamic entry that yields the saved source item. */
    public static final ResourceLocation SOURCE_DROP = ModIdentity.id("unlit_torch_source");

    private UnlitTorchRoot.Identity identity;
    private Tag retained;

    public UnlitTorchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNLIT_TORCH.get(), pos, state);
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public Optional<UnlitTorchRoot.Identity> identity() {
        return Optional.ofNullable(identity);
    }

    /** True when an undecodable root is kept as raw data. */
    public boolean retainedRoot() {
        return retained != null;
    }

    /** Quarantine: a retained root, or an identity that no longer resolves. A torch with no root is not protected. */
    public boolean protectedData() {
        return retained != null || identity != null && resolve().isEmpty();
    }

    void assign(UnlitTorchRoot.Identity value) {
        identity = value;
        retained = null;
        setChanged();
    }

    /** The live pair, lit block and item for this block's shape, or empty. */
    public Optional<Resolved> resolve() {
        if (identity == null || retained != null) {
            return Optional.empty();
        }
        boolean wall = getBlockState().getBlock() instanceof UnlitWallTorchBlock;
        TorchPair pair = CombustionTorches.catalog().get(identity.pair()).orElse(null);
        if (pair == null || !pair.block(wall).equals(identity.block()) || !pair.item().equals(identity.item())) {
            return Optional.empty();
        }
        Block lit = TorchShapes.block(identity.block());
        Item item = TorchShapes.item(identity.item());
        if (lit == null || item == null || !TorchShapes.supports(lit, wall) || lit.asItem() != item) {
            return Optional.empty();
        }
        return Optional.of(new Resolved(pair, lit, item));
    }

    /** The lit state that relighting restores, with the saved wall facing. */
    Optional<BlockState> litState() {
        return resolve().map(resolved -> {
            BlockState state = resolved.lit().defaultBlockState();
            BlockState unlit = getBlockState();
            return unlit.hasProperty(UnlitWallTorchBlock.FACING)
                    ? state.setValue(UnlitWallTorchBlock.FACING, unlit.getValue(UnlitWallTorchBlock.FACING)) : state;
        });
    }

    static boolean protectedAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof UnlitTorchBlockEntity torch && torch.protectedData();
    }

    /** Offers the resolved source item to the loot table's dynamic entry; nothing for any other torch. */
    static void offerSourceDrop(LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof UnlitTorchBlockEntity torch) {
            torch.resolve().ifPresent(resolved -> builder.withDynamicDrop(SOURCE_DROP,
                    output -> output.accept(new ItemStack(resolved.item()))));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        if (retained != null) {
            // An oversized raw root is written by reference; the chunk-save guard refuses that chunk.
            outer.put(UnlitTorchRoot.KEY, UnlitTorchRoot.bounded(retained) ? retained.copy() : retained);
        } else if (identity != null) {
            outer.put(UnlitTorchRoot.KEY, UnlitTorchRoot.encode(identity));
        }
    }

    @Override
    public void load(CompoundTag outer) {
        super.load(outer);
        identity = null;
        retained = null;
        Tag raw = outer.get(UnlitTorchRoot.KEY);
        if (raw == null) {
            return;
        }
        Optional<UnlitTorchRoot.Identity> decoded = UnlitTorchRoot.decode(raw);
        if (decoded.isPresent()) {
            identity = decoded.get();
        } else {
            retained = UnlitTorchRoot.bounded(raw) ? raw.copy() : raw;
        }
    }

    /** One resolved source torch. */
    public record Resolved(TorchPair pair, Block lit, Item item) {
    }
}
