package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Map;

/**
 * W294 (finding 3-7): the star map's quote is the route's fuel requirement in the rocket's own units, the number the
 * flight console shows as "ROUTE NEEDS"; it is not millibuckets (ADR-065 D1-A keeps abstract fuel and Fluid apart, and
 * a Fluid rocket's actual debit is scaled from it). The v1.4 key with "mB" stays in its historical output.
 */
public final class V180StarMapLanguage {
    public static final String ROUTE_QUOTE = "starmap.advancedrocketrycommunity.route_quote";

    private V180StarMapLanguage() { }

    public static Map<String, String> entries(boolean chinese) {
        return Map.of(ROUTE_QUOTE, chinese ? "%s · 航程需要燃料 %s" : "%s · Route needs %s fuel");
    }
}
