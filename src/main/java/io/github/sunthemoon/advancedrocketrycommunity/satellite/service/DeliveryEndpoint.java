package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import java.util.UUID;

/** A loaded delivery terminal as the directory and the rebind command see it (ADR-051 sections 5 and 9). */
public interface DeliveryEndpoint {
    UUID terminalId();

    /** Whether the terminal ID was seen in a chunk load or chunk save tag (ADR-051 section 5). */
    boolean terminalIdPersisted();
}
