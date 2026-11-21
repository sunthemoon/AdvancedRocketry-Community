package io.github.sunthemoon.arceintenttest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolPinTest {
    @Test void eightChannelsHaveOnlyThreeLegalClientToServerMessages() throws Exception {
        List<String> lines;
        try (var stream = getClass().getResourceAsStream("/network-protocols.txt")) {
            assertNotNull(stream);
            lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.startsWith("#") && !line.isBlank()).toList();
        }
        assertEquals(16, lines.size());
        assertEquals(8, lines.stream().map(line -> line.split("\\|")[0]).distinct().count());
        Map<String, String> c2s = lines.stream().map(line -> line.split("\\|"))
                .filter(row -> row[4].equals("PLAY_TO_SERVER"))
                .peek(row -> assertEquals("0", row[2]))
                .collect(Collectors.toMap(row -> row[0], row -> row[1]));
        assertEquals(Map.of("advancedrocketrycommunity:rocket_flight", "10",
                "advancedrocketrycommunity:classic_controls", "3", "advancedrocketrycommunity:suit_motion", "2"), c2s);
        for (var which : IntentCase.values()) {
            assertEquals(Integer.toString(which.protocol()), c2s.get(which.channel()));
        }
    }
}
