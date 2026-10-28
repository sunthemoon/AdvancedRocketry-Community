package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMenuAuthorityProbes;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayMenuAuthorityProbe;
import java.util.ArrayList;
import java.util.List;

/** W179: every probe of the menu-authority matrix, family by family. */
public final class MenuAuthorityProbes {
    private MenuAuthorityProbes() {
    }

    public static List<MenuAuthorityProbe> all() {
        List<MenuAuthorityProbe> probes = new ArrayList<>();
        probes.addAll(MenuAuthorityMachineProbes.all());
        probes.addAll(MenuAuthorityEndgameProbes.all());
        probes.addAll(MenuAuthorityStationProbes.all());
        probes.addAll(MenuAuthorityRocketProbes.all());
        probes.add(SolarArrayMenuAuthorityProbe.probe());
        probes.addAll(ClassicMenuAuthorityProbes.all());
        return List.copyOf(probes);
    }
}
