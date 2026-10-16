package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.AtmosphereDetectorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.PipeSealBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.ScrubberBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorAnchorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldProjectorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicHatchBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayOutputBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.WarpCoreBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * W23: vanilla's {@code BlockItem.updateCustomBlockEntityTag} loads an item's {@code BlockEntityTag} for a non-operator
 * unless the block entity overrides {@code onlyOpCanSetNbt()}. Every block entity type this mod registers is listed
 * here, either as operator-only (it keeps an owner, an identity, a binding, a generation, or a retained or guarded
 * root) or as keeping no saved root of its own; a new type fails until it is listed.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OperatorItemNbtGameTests {
    /** Block entities whose item NBT only a creative operator may load. */
    private static final Set<Class<? extends BlockEntity>> OPERATOR_ONLY = Set.of(
            // Operator-only before W23.
            PumpBlockEntity.class, SolarGeneratorBlockEntity.class, SolarArrayBlockEntity.class,
            PlanetaryBeaconBlockEntity.class, LandingPadBlockEntity.class, ForceFieldProjectorBlockEntity.class,
            ForceFieldBlockEntity.class, RailgunBlockEntity.class, ElevatorAnchorBlockEntity.class,
            ElevatorTerminalBlockEntity.class, GravityFieldBlockEntity.class, BlackHoleGeneratorBlockEntity.class,
            OrbitalLaserDrillBlockEntity.class, LaserTargetBlockEntity.class,
            // W23.
            ScrubberBlockEntity.class, AtmosphereDetectorBlockEntity.class, OxygenVentBlockEntity.class,
            CombustionGeneratorBlockEntity.class, ElectrolyzerBlockEntity.class, RollingMachineBlockEntity.class,
            RollingMachinePortBlockEntity.class, PrecisionAssemblerBlockEntity.class,
            PrecisionAssemblerPortBlockEntity.class, PressurizedTankBlockEntity.class, ClassicHatchBlockEntity.class,
            ClassicControllerBlockEntity.class, SuitWorkstationBlockEntity.class, RocketAssemblerBlockEntity.class,
            io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchControllerBlockEntity.class,
            FuelLoaderBlockEntity.class, SatelliteTerminalBlockEntity.class, SatelliteBuilderBlockEntity.class,
            MicrowaveReceiverBlockEntity.class,
            // W33: the unlit torch's identity root and its retained, quarantined roots.
            io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlockEntity.class,
            // W27b: the satellite bay's owner, package identity and guarded roots.
            io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity.class,
            // W82: the fluid fuel loader's owner, device identity, binding and escrow section.
            io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity.class);
    /** Block entities that save no root of their own, so item NBT reaches no server-decided state. */
    private static final Set<Class<? extends BlockEntity>> NO_ROOT = Set.of(
            WarpCoreBlockEntity.class, SolarArrayOutputBlockEntity.class, PipeSealBlockEntity.class);

    private OperatorItemNbtGameTests() {
    }

    @GameTest(template = "empty", batch = "operator_item_nbt", timeoutTicks = 20)
    public static void everyRegisteredBlockEntityIsListedAndRootsRequireAnOperator(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Set<Class<?>> seen = new HashSet<>();
        List<String> defects = new ArrayList<>();
        for (var entry : ForgeRegistries.BLOCK_ENTITY_TYPES.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(ModIdentity.MOD_ID)) {
                continue;
            }
            BlockEntityType<?> type = entry.getValue();
            Block block = ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(candidate -> type.isValid(candidate.defaultBlockState())).findFirst().orElse(null);
            BlockEntity entity = block == null ? null : type.create(origin, block.defaultBlockState());
            if (entity == null) {
                defects.add(id + " creates no block entity");
                continue;
            }
            Class<?> kind = entity.getClass();
            seen.add(kind);
            boolean operatorOnly = OPERATOR_ONLY.contains(kind);
            if (operatorOnly == NO_ROOT.contains(kind)) {
                defects.add(id + " (" + kind.getName() + ") is not listed exactly once");
            } else if (entity.onlyOpCanSetNbt() != operatorOnly) {
                defects.add(id + " (" + kind.getSimpleName() + ") returns onlyOpCanSetNbt()=" + entity.onlyOpCanSetNbt());
            }
        }
        Set<Class<?>> listed = new HashSet<>(OPERATOR_ONLY);
        listed.addAll(NO_ROOT);
        listed.removeAll(seen);
        listed.forEach(kind -> defects.add(kind.getName() + " is listed but no registered type creates it"));
        helper.assertTrue(defects.isEmpty(), "Item NBT classification defects: " + defects);
        helper.succeed();
    }

    /** A scrubber item whose {@code BlockEntityTag} names another identity and a binding to the cell above {@code at}. */
    private static ItemStack forgedScrubber(BlockPos at, UUID forgedId) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 1);
        root.putString("identity", forgedId.toString());
        root.putLong("owner_position", at.above().asLong());
        root.putString("owner_generation", UUID.randomUUID().toString());
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put(ScrubberBlockEntity.KEY, root);
        ItemStack stack = new ItemStack(ModItems.CO2_SCRUBBER.get());
        stack.getOrCreateTag().put(BlockItem.BLOCK_ENTITY_TAG, blockEntity);
        return stack;
    }

    /** A new identity and no binding: nothing of the forged root was loaded. */
    private static boolean fresh(ScrubberBlockEntity scrubber, UUID forgedId) {
        return !scrubber.identity().equals(forgedId) && !scrubber.protectedData()
                && scrubber.saveWithoutMetadata().getCompound(ScrubberBlockEntity.KEY).getAllKeys()
                .equals(Set.of("schema_version", "identity"));
    }

    /** Places {@code stack} at {@code at}: used by {@code placer} through the native game-mode path, or as a dispenser does. */
    private static ScrubberBlockEntity place(GameTestHelper helper, @Nullable ServerPlayer placer, BlockPos at,
                                             ItemStack stack) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        InteractionResult result;
        if (placer == null) {
            result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null,
                    InteractionHand.MAIN_HAND, stack, hit));
        } else {
            placer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            result = placer.gameMode.useItemOn(placer, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
            placer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.assertTrue(result.consumesAction() && helper.getLevel().getBlockState(at).is(ModBlocks.CO2_SCRUBBER.get()),
                "Placing the scrubber was refused: " + result);
        return (ScrubberBlockEntity) helper.getLevel().getBlockEntity(at);
    }

    @GameTest(template = "empty", batch = "operator_item_nbt", timeoutTicks = 40)
    public static void nonOperatorScrubberItemNbtYieldsAFreshScrubber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos at = helper.absolutePos(new BlockPos(1, 3, 1));
        BlockPos dispensed = at.east(2);
        BlockPos byOperator = at.east(4);
        BlockPos standing = at.north(3);
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer operator = null;
        try {
            UUID forgedId = UUID.randomUUID();
            ScrubberBlockEntity detached = new ScrubberBlockEntity(at, ModBlocks.CO2_SCRUBBER.get().defaultBlockState());
            detached.load(BlockItem.getBlockEntityData(forgedScrubber(at, forgedId)));
            helper.assertTrue(detached.identity().equals(forgedId)
                    && detached.saveWithoutMetadata().getCompound(ScrubberBlockEntity.KEY).contains("owner_position"),
                    "The forged root is not a valid bound scrubber root");

            // A creative player without operator rights.
            ServerPlayer builder = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w23Builder", level, standing,
                    new ArrayList<>());
            joined.add(builder);
            builder.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(builder.getAbilities().instabuild && !builder.canUseGameMasterBlocks(),
                    "The builder is not a creative non-operator");
            ScrubberBlockEntity built = place(helper, builder, at, forgedScrubber(at, forgedId));
            helper.assertTrue(built.onlyOpCanSetNbt(), "The scrubber accepts non-operator item NBT");
            helper.assertTrue(fresh(built, forgedId), "A non-operator's item NBT reached the scrubber: "
                    + built.saveWithoutMetadata());

            // A placement without any player.
            ScrubberBlockEntity unplayered = place(helper, null, dispensed, forgedScrubber(dispensed, forgedId));
            helper.assertTrue(fresh(unplayered, forgedId), "A placement without a player loaded the forged root: "
                    + unplayered.saveWithoutMetadata());

            // The same item placed by a creative operator does load it.
            operator = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w23Operator", level, standing,
                    new ArrayList<>(), true);
            joined.add(operator);
            server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 4, false));
            operator.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            helper.assertTrue(operator.canUseGameMasterBlocks(), "The operator cannot use game-master blocks");
            ScrubberBlockEntity loaded = place(helper, operator, byOperator, forgedScrubber(byOperator, forgedId));
            helper.assertTrue(loaded.identity().equals(forgedId) && loaded.protectedData(),
                    "An operator's item NBT was not loaded: " + loaded.saveWithoutMetadata());
        } finally {
            for (BlockPos placed : List.of(at, dispensed, byOperator)) {
                level.setBlock(placed, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (operator != null) {
                server.getPlayerList().getOps().remove(operator.getGameProfile());
            }
            joined.forEach(player -> server.getPlayerList().remove(player));
        }
        helper.succeed();
    }
}
