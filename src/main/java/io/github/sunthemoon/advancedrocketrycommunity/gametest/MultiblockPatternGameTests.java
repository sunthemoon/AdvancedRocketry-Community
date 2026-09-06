package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternRoleResolver;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.ServerLevelPatternWorldView;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MultiblockPatternGameTests {
    private MultiblockPatternGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void forgeAdapterObservesLoadedBlockTagsAndControllerRole(GameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos casing = controller.east();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.IRON_BLOCK);

        MultiblockPatternDefinition definition = new MultiblockPatternDefinition(
                "test:loaded_adapter",
                1,
                512,
                new PatternSize(2, 1, 1),
                new PatternPosition(0, 0, 0),
                Set.of(PatternRotation.ZERO),
                false,
                Map.of(
                        new PatternPosition(0, 0, 0), new PatternMatcher.Controller(),
                        new PatternPosition(1, 0, 0), new PatternMatcher.BlockTag(
                                BlockTags.MINEABLE_WITH_PICKAXE.location().toString()
                        )
                )
        );
        ServerLevelPatternWorldView world = new ServerLevelPatternWorldView(
                helper.getLevel(),
                rolesAt(controller)
        );

        var result = MultiblockPatternValidator.validate(
                definition,
                new PatternTransform(PatternRotation.ZERO, false),
                position(controller),
                world
        );

        helper.assertTrue(helper.getLevel().hasChunkAt(casing), "Loaded casing chunk unexpectedly disappeared");
        helper.assertTrue(result.formed(), "Forge adapter did not form the loaded tagged pattern");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unloadedObservationDoesNotForceChunkLoad(GameTestHelper helper) {
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(16_384, 0, 16_384);
        helper.assertTrue(!helper.getLevel().hasChunkAt(far), "Far test chunk was loaded before validation");
        MultiblockPatternDefinition definition = new MultiblockPatternDefinition(
                "test:unloaded_adapter",
                1,
                256,
                new PatternSize(1, 1, 1),
                new PatternPosition(0, 0, 0),
                Set.of(PatternRotation.ZERO),
                false,
                Map.of(new PatternPosition(0, 0, 0), new PatternMatcher.Controller())
        );
        ServerLevelPatternWorldView world = new ServerLevelPatternWorldView(
                helper.getLevel(),
                rolesAt(far)
        );

        var result = MultiblockPatternValidator.validate(
                definition,
                new PatternTransform(PatternRotation.ZERO, false),
                position(far),
                world
        );

        helper.assertTrue(
                result.status() == PatternValidationStatus.WAITING_UNLOADED,
                "Unloaded pattern did not return WAITING_UNLOADED"
        );
        helper.assertTrue(!helper.getLevel().hasChunkAt(far), "Pattern validation force-loaded a chunk");
        helper.succeed();
    }

    private static PatternRoleResolver rolesAt(BlockPos controller) {
        return new PatternRoleResolver() {
            @Override
            public boolean isController(BlockPos position, BlockState state) {
                return position.equals(controller);
            }

            @Override
            public Optional<String> portChannel(BlockPos position, BlockState state) {
                return Optional.empty();
            }
        };
    }

    private static PatternPosition position(BlockPos position) {
        return new PatternPosition(position.getX(), position.getY(), position.getZ());
    }
}
