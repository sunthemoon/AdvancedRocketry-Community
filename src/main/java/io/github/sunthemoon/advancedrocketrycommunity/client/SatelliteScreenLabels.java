package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;

/** Display-only builder labels; the preview still refuses another build while the output is occupied. */
final class SatelliteScreenLabels {
    static SatelliteOperationCode builderStatus(SatelliteOperationCode last, SatelliteOperationCode preview) {
        return last == SatelliteOperationCode.SUCCESS && preview != SatelliteOperationCode.OUTPUT_BLOCKED ? preview : last;
    }

    static String builderKey(SatelliteOperationCode code, SatelliteOperationCode preview) {
        if (code != SatelliteOperationCode.SUCCESS) { return code.translationKey(); }
        return "screen.advancedrocketrycommunity.satellite_builder."
                + (preview == SatelliteOperationCode.OUTPUT_BLOCKED ? "output_ready" : "ready");
    }

    private SatelliteScreenLabels() { }
}
