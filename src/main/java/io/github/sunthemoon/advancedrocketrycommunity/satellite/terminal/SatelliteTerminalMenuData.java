package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import java.util.UUID;
import net.minecraft.world.inventory.ContainerData;

final class SatelliteTerminalMenuData implements ContainerData {
    private final SatelliteTerminalBlockEntity terminal;
    private final UUID viewerId;

    SatelliteTerminalMenuData(SatelliteTerminalBlockEntity terminal, UUID viewerId) {
        this.terminal = terminal;
        this.viewerId = viewerId;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> terminal.energyStored();
            case 1 -> SatelliteTerminalBlockEntity.ENERGY_CAPACITY;
            case 2 -> terminal.lastResultId();
            case 3 -> terminal.selectedTargetIndex();
            case 4 -> terminal.missionDurationSeconds();
            case 5 -> terminal.researchBalanceLow(viewerId);
            case 6 -> terminal.missionStatusId();
            case 7 -> terminal.remainingSeconds();
            case 8 -> terminal.selectedTargetDiscovered();
            case 9 -> terminal.ownedBy(viewerId);
            case 10 -> terminal.researchBalanceHigh(viewerId);
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        // All menu data is read-only and server authoritative.
    }

    @Override
    public int getCount() {
        return SatelliteTerminalBlockEntity.MENU_DATA_COUNT;
    }
}
