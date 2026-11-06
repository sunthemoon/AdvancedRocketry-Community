package io.github.sunthemoon.advancedrocketrycommunity.client;

import static io.github.sunthemoon.advancedrocketrycommunity.client.EndgameScreenFeedbackTest.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Executes the shared screen hooks without creating a Minecraft client or sending any packet. */
class EndgameRefusalScreenTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @AfterEach void clear() throws Exception {
        notices().clear();
        EndgameDeviceViewCache.clear();
    }

    @Test void allFourScreensInheritTheRefusalClassifierAndEveryButtonUsesTheSharedReleasePoint() throws Exception {
        for (Class<?> screen : List.of(RailgunScreen.class, ElevatorScreen.class,
                OrbitalLaserDrillScreen.class, BlackHoleGeneratorScreen.class)) {
            assertTrue(ScreenActionMessages.RefusalOwner.class.isAssignableFrom(screen));
            assertEquals(EndgameRefusalScreen.class, screen.getMethod("refusal", Component.class).getDeclaringClass());
            String source = Files.readString(Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/"
                    + screen.getSimpleName() + ".java"));
            if (screen != BlackHoleGeneratorScreen.class) {
                assertTrue(source.contains("ignored -> sendButton(id)"), screen.getSimpleName());
                assertFalse(source.contains("handleInventoryButtonClick"), screen.getSimpleName());
            }
            for (EndgameCode code : EndgameCode.values()) {
                var fixture = new TestScreen(new int[]{0});
                assertEquals(EndgameScreenFeedback.refusal(message(code)).isPresent(), fixture.refusal(message(code)));
            }
        }
    }

    @Test void eachButtonReleasesBeforeItsDispatchAndSlotActionsDoTheSame() throws Exception {
        var screen = new TestScreen(new int[]{0});
        for (int id = 0; id <= 8; id++) {
            pin(screen, EndgameCode.INSUFFICIENT_ENERGY);
            screen.sendButton(id); // No client means no packet; release still occurs before that check.
            assertTrue(notices().current(screen, 100000).isEmpty(), "button " + id);
        }
        for (ClickType type : ClickType.values()) {
            pin(screen, EndgameCode.UNAUTHORIZED);
            // Vanilla's dispatch needs a live client. The hook must release before reaching that boundary.
            assertThrows(NullPointerException.class, () -> screen.slotClicked(null, -999, 0, type));
            assertTrue(notices().current(screen, 100000).isEmpty(), type.name());
        }
        EndgameDeviceViewCache.accept(view(EndgameSystem.RAILGUN, EndgameCode.OK));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isEmpty(), "Released tracking must not recreate a notice");
    }

    @Test void unchangedReadoutsAndTimedProgressKeepTheRefusalButAChangedDestinationReleasesIt() throws Exception {
        var screen = new TestScreen(new int[]{0});
        var before = view(EndgameSystem.RAILGUN, EndgameCode.OK, line("destination", "old"));
        EndgameDeviceViewCache.accept(before);
        pin(screen, EndgameCode.INSUFFICIENT_ENERGY);
        screen.refreshRefusal();
        assertEquals(message(EndgameCode.INSUFFICIENT_ENERGY), notices().current(screen, 100000).orElseThrow());
        var progress = message(EndgameCode.AWAITING_WORLD_SAVE);
        assertFalse(screen.refusal(progress));
        notices().accept(screen, true, true, progress, 200);
        screen.refreshRefusal();
        assertEquals(message(EndgameCode.INSUFFICIENT_ENERGY), notices().current(screen, 100000).orElseThrow());
        EndgameDeviceViewCache.accept(view(EndgameSystem.RAILGUN, EndgameCode.OK, line("destination", "new")));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isEmpty());
    }

    @Test void delayedViewsDoNotReleaseAndARecordedRefusalReleasesOnlyWhenThatStatusClears() throws Exception {
        var screen = new TestScreen(new int[]{0});
        pin(screen, EndgameCode.NO_SINGULARITY);
        screen.refreshRefusal();
        EndgameDeviceViewCache.accept(view(EndgameSystem.BLACK_HOLE_GENERATOR, EndgameCode.NO_SINGULARITY));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isPresent());
        EndgameDeviceViewCache.accept(view(EndgameSystem.BLACK_HOLE_GENERATOR, EndgameCode.GENERATING));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isEmpty());

        EndgameDeviceViewCache.accept(view(EndgameSystem.LASER_DRILL, EndgameCode.OK));
        pin(screen, EndgameCode.NO_LENS);
        EndgameDeviceViewCache.accept(view(EndgameSystem.LASER_DRILL, EndgameCode.NO_LENS));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isPresent());
        EndgameDeviceViewCache.accept(view(EndgameSystem.LASER_DRILL, EndgameCode.OK));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isEmpty());
    }

    @Test void automaticDeviceInventoryChangesAnswerResourceRefusalsButNotAuthorityRefusals() throws Exception {
        var screen = new TestScreen(new int[]{0});
        pin(screen, EndgameCode.NO_PAYLOAD);
        screen.getMenu().slots.get(1).set(new ItemStack(Items.IRON_INGOT)); // Player inventory is unrelated.
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isPresent());
        screen.getMenu().slots.get(0).set(new ItemStack(Items.IRON_INGOT));
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isEmpty());
        pin(screen, EndgameCode.UNAUTHORIZED);
        screen.getMenu().slots.get(0).set(ItemStack.EMPTY);
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isPresent());
    }

    @Test void partialChargingDoesNotClearTheRailgunRefusal() throws Exception {
        int[] energy = {0};
        var screen = new TestScreen(energy);
        EndgameDeviceViewCache.accept(view(EndgameSystem.RAILGUN, EndgameCode.OK, line("route", "ORBITAL 250000 FE 600 t")));
        pin(screen, EndgameCode.INSUFFICIENT_ENERGY);
        energy[0] = 249999;
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isPresent());
        energy[0] = 250000;
        screen.refreshRefusal();
        assertTrue(notices().current(screen, 100000).isEmpty());
    }

    private static void pin(TestScreen screen, EndgameCode code) throws Exception {
        assertTrue(screen.refusal(message(code)));
        notices().accept(screen, true, true, message(code), 100, true);
    }

    @SuppressWarnings("unchecked")
    private static ScreenMessageState<Component> notices() throws Exception {
        var field = ScreenActionMessages.class.getDeclaredField("NOTICE");
        field.setAccessible(true);
        return (ScreenMessageState<Component>) field.get(null);
    }

    private static final class TestScreen extends EndgameRefusalScreen<AbstractContainerMenu> {
        TestScreen(int[] energy) {
            super(new TestMenu(), new Inventory(null) {
                @Override public Component getDisplayName() { return Component.literal("Inventory"); }
            }, Component.literal("test"), () -> energy[0]);
        }

        @Override protected void renderBg(GuiGraphics graphics, float tick, int x, int y) { }
    }

    private static final class TestMenu extends AbstractContainerMenu {
        TestMenu() {
            super(null, 7);
            var inventory = new SimpleContainer(37);
            for (int slot = 0; slot < 37; slot++) { addSlot(new Slot(inventory, slot, 0, 0)); }
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
