package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-051 sections 5 and 10: observation bounds, audit-line bounds, and no chunk tickets in satellite code. */
final class DeliveryRuntimeTest {
    @Test
    void observationsAreConsumedOnceAtTheirPositionAndBounded() {
        TerminalObservations observations = new TerminalObservations();
        UUID terminal = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        observations.record(terminal, new BlockPos(1, 2, 3), Set.of(receipt));
        assertEquals(Optional.empty(), observations.consume(terminal, new BlockPos(9, 9, 9)),
                "a terminal elsewhere does not take another position's observation");
        assertEquals(Set.of(receipt), observations.consume(terminal, new BlockPos(1, 2, 3)).orElseThrow().receipts());
        assertEquals(Optional.empty(), observations.consume(terminal, new BlockPos(1, 2, 3)));
        for (int index = 0; index <= TerminalObservations.MAX_ENTRIES; index++) {
            observations.record(new UUID(0L, index), BlockPos.ZERO, Set.of());
        }
        assertEquals(TerminalObservations.MAX_ENTRIES, observations.size());
        assertEquals(Optional.empty(), observations.consume(new UUID(0L, 0L), BlockPos.ZERO), "the oldest is dropped");
    }

    @Test
    void auditLinesAreBoundedAndDigestTheReward() {
        List<RewardEntry> reward = List.of(new RewardEntry(new ResourceLocation("minecraft", "iron_ore"), 30));
        String line = DeliveryAudit.line("REBIND_CONFLICT", UUID.randomUUID(), UUID.randomUUID(), Long.MAX_VALUE,
                UUID.randomUUID(), reward, "old=" + UUID.randomUUID());
        assertTrue(line.startsWith("ARCE_MISSION_DELIVERY event=REBIND_CONFLICT mission="));
        assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= DeliveryAudit.MAX_BYTES);
        assertTrue(line.contains(" reward=" + DeliveryAudit.digest(reward)));
        assertEquals(16, DeliveryAudit.digest(reward).length());
        assertTrue(DeliveryAudit.line("PURGE", UUID.randomUUID(), null, 1L, UUID.randomUUID(), List.of(), "")
                .contains(" terminal=none epoch=1 ") );
        assertThrows(IllegalArgumentException.class, () -> DeliveryAudit.line("claim", UUID.randomUUID(), null, 1L,
                UUID.randomUUID(), reward, ""));
        assertThrows(IllegalArgumentException.class, () -> DeliveryAudit.line("CLAIM", UUID.randomUUID(), null, 1L,
                UUID.randomUUID(), reward, "note=Uppercase"));
    }

    @Test
    void satelliteCodeNeverTakesAChunkTicket() throws IOException {
        Pattern tickets = Pattern.compile("addRegionTicket|ForgeChunkManager|setChunkForced|TicketType");
        Path root = Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> offenders = files.filter(path -> path.toString().endsWith(".java")).filter(path -> {
                try {
                    return tickets.matcher(Files.readString(path)).find();
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();
            assertEquals(List.of(), offenders, "ADR-051: resource missions hold zero chunk tickets");
        }
    }
}
