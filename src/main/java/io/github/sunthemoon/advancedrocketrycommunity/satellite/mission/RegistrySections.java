package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.List;

/**
 * ADR-074 item 2: references to the registry's immutable records and its two clock values, copied from the live maps
 * (never views) on the owner thread. The lists are unsorted; sorting belongs to whoever encodes them. At most 20,480
 * references (design section 0).
 */
public record RegistrySections(long logicalGameTime, long lastObservedGameTime, List<SatelliteState> satellites,
                               List<MissionState> missions, List<ResearchAccount> accounts,
                               List<AsteroidInstance> instances, List<GroundSurveyJob> groundSurveys) {
    public RegistrySections {
        // List.copyOf returns an already immutable copy unchanged, so a capture copies each section once.
        satellites = List.copyOf(satellites);
        missions = List.copyOf(missions);
        accounts = List.copyOf(accounts);
        instances = List.copyOf(instances);
        groundSurveys = List.copyOf(groundSurveys);
    }
}
