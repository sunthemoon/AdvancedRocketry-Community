package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import java.util.function.IntSupplier;
import net.minecraft.world.inventory.ContainerData;

final class ElectrolyzerMenuData implements ContainerData {
    private final IntSupplier progress;
    private final IntSupplier totalProcessingTicks;
    private final IntSupplier energy;
    private final IntSupplier water;
    private final IntSupplier status;

    ElectrolyzerMenuData(
            IntSupplier progress,
            IntSupplier totalProcessingTicks,
            IntSupplier energy,
            IntSupplier water,
            IntSupplier status
    ) {
        this.progress = progress;
        this.totalProcessingTicks = totalProcessingTicks;
        this.energy = energy;
        this.water = water;
        this.status = status;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> progress.getAsInt();
            case 1 -> totalProcessingTicks.getAsInt();
            case 2 -> energy.getAsInt();
            case 3 -> ElectrolyzerBlockEntity.ENERGY_CAPACITY;
            case 4 -> water.getAsInt();
            case 5 -> ElectrolyzerBlockEntity.WATER_CAPACITY;
            case 6 -> status.getAsInt();
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        // Server-authoritative fields are read-only on the server menu.
    }

    @Override
    public int getCount() {
        return ElectrolyzerBlockEntity.MENU_DATA_COUNT;
    }
}
