package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.DISTANCE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.FOREIGN_OWNER;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.RATE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.SPECTATOR;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.UNLOADED;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * W179: the single-block machines of the menu-authority matrix. None of them has an owner, so the foreign-owner
 * check applies only to the satellite terminal; the menus whose only actions are vanilla slot clicks have no rate.
 */
final class MenuAuthorityMachineProbes {
    /**
     * The holder's offset from the test's origin. A single block needs no cleared space: it goes in the one-block
     * {@code empty} template, whose tests stand six blocks apart, so a large batch does not generate a wide grid.
     */
    static final BlockPos HOLDER = new BlockPos(8, 1, 8);
    static final String SINGLE = "empty";
    private static final String RATE_LIMITED = SatelliteOperationCode.RATE_LIMITED.translationKey();

    private MenuAuthorityMachineProbes() {
    }

    static List<MenuAuthorityProbe> all() {
        return List.of(
                MenuAuthorityProbe.of("combustion_generator", false, 40,
                        helper -> new Placed(helper, ModBlocks.COMBUSTION_GENERATOR), DISTANCE, SPECTATOR, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("solar_generator", false, 40,
                        helper -> new Placed(helper, ModBlocks.SOLAR_GENERATOR), DISTANCE, SPECTATOR, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("electrolyzer", false, 40,
                        helper -> new Placed(helper, ModBlocks.ELECTROLYZER), DISTANCE, SPECTATOR, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("microwave_receiver", false, 40,
                        helper -> new Placed(helper, ModBlocks.MICROWAVE_RECEIVER), DISTANCE, SPECTATOR, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("satellite_builder", true, 40, SatelliteBuilder::new,
                        DISTANCE, SPECTATOR, RATE, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("satellite_terminal", true, 40, SatelliteTerminal::new,
                        DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("suit_workstation", true, 40, SuitWorkstation::new,
                        DISTANCE, SPECTATOR, RATE, UNLOADED).in(SINGLE),
                MenuAuthorityProbe.of("rolling_machine", false, 100, helper -> new Structure(helper,
                        RollingMachineGameTestFixtures.CONTROLLER, () -> RollingMachineGameTestFixtures
                        .placeStructure(helper)), DISTANCE, SPECTATOR, UNLOADED),
                MenuAuthorityProbe.of("precision_assembler", false, 100, helper -> new Structure(helper,
                        PrecisionAssemblerGameTests.CONTROLLER, () -> PrecisionAssemblerGameTests
                        .placeStructure(helper)), DISTANCE, SPECTATOR, UNLOADED));
    }

    /** A block placed at {@link #HOLDER}, as a player would place it, without an owner. */
    static class Placed extends MenuAuthorityFixture.OfBlock {
        Placed(GameTestHelper helper, Supplier<? extends Block> block) {
            super(helper.getLevel(), helper.absolutePos(HOLDER), UUID.randomUUID());
            level.setBlock(holder, block.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** A multiblock that its own GameTests' fixture places around {@code controller}; the batch clears it. */
    private static final class Structure extends MenuAuthorityFixture.OfBlock {
        Structure(GameTestHelper helper, BlockPos controller, Runnable place) {
            super(helper.getLevel(), helper.absolutePos(controller), UUID.randomUUID());
            place.run();
        }
    }

    /** Assembling is the builder's one action; a refusal for rate reports {@code RATE_LIMITED} to the player. */
    private static final class SatelliteBuilder extends Placed {
        SatelliteBuilder(GameTestHelper helper) {
            super(helper, ModBlocks.SATELLITE_BUILDER);
        }

        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            return satellite(player, () -> menu.clickMenuButton(player, SatelliteBuilderMenu.BUTTON_ASSEMBLE));
        }
    }

    /** An owned terminal; selecting the previous target is its cheapest action. */
    private static final class SatelliteTerminal extends Placed {
        SatelliteTerminal(GameTestHelper helper) {
            super(helper, ModBlocks.SATELLITE_TERMINAL);
            ((SatelliteTerminalBlockEntity) level.getBlockEntity(holder)).setOwner(owner());
        }

        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            return satellite(player, () -> menu.clickMenuButton(player, SatelliteTerminalMenu.BUTTON_PREVIOUS));
        }
    }

    /**
     * The workstation's buttons edit the held armour. The first action installs a helmet module (button 0); the next
     * removes it again (button 1), which only a rate refusal stops, because installing emptied the transfer slot.
     */
    private static final class SuitWorkstation extends Placed {
        SuitWorkstation(GameTestHelper helper) {
            super(helper, SuitEquipmentContent.BLOCK);
        }

        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            SuitWorkstationBlockEntity station = (SuitWorkstationBlockEntity) level.getBlockEntity(holder);
            int button = 1;
            if (station.stack(0).isEmpty()) {
                ModuleKind kind = Arrays.stream(ModuleKind.values())
                        .filter(candidate -> candidate.part() == SuitPart.HEAD && candidate != ModuleKind.BEACON_FINDER)
                        .findFirst().orElseThrow();
                String module = kind.itemPaths().iterator().next();
                if (!station.replace(player, 0, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()))
                        || !station.replace(player, 1, SuitEquipmentContent.modules().get(module).get()
                        .getDefaultInstance())) {
                    return MenuAuthorityMatrix.Outcome.REFUSED;
                }
                button = 0;
            }
            return menu.clickMenuButton(player, button) ? MenuAuthorityMatrix.Outcome.ACCEPTED
                    : MenuAuthorityMatrix.Outcome.REFUSED;
        }

        /** Breaking the workstation drops what it holds; the drops go with it. */
        @Override
        public void close() {
            super.close();
            level.getEntitiesOfClass(ItemEntity.class, new AABB(holder).inflate(3)).forEach(ItemEntity::discard);
        }
    }

    /** A satellite button: accepted unless the intent limiter reported {@code RATE_LIMITED} to the player. */
    private static MenuAuthorityMatrix.Outcome satellite(ServerPlayer player, Supplier<Boolean> click) {
        MenuAuthorityMatrix.MatrixPlayer matrix = (MenuAuthorityMatrix.MatrixPlayer) player;
        int mark = matrix.messages.size();
        click.get();
        return matrix.saidSince(mark, RATE_LIMITED) ? MenuAuthorityMatrix.Outcome.RATE_LIMITED
                : MenuAuthorityMatrix.Outcome.ACCEPTED;
    }
}
