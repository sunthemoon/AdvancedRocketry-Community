package io.github.sunthemoon.advancedrocketrycommunity.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ADR-046 UI-01: the station, team and warp controls add no packet. Every channel in the main sources,
 * its protocol version and its registered message list must equal the committed table.
 * ADR-064 section 14 adds exactly one required message-empty menu admission channel. ADR-066 section 5.2 adds the
 * suit motion channel with its fixed jetpack intent and paid impulse, and ADR-065 section 11.1 the three-message
 * {@code classic_controls} channel; R92 adds monitor kind 4 at protocol 2 without changing that message table.
 */
final class NetworkProtocolPinTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Pattern CHANNEL = Pattern.compile("\\.named\\(ModIdentity\\.id\\(\"([a-z0-9_./-]+)\"\\)\\)");
    private static final Pattern PROTOCOL = Pattern.compile("PROTOCOL_VERSION\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE = Pattern.compile(
            "messageBuilder\\(\\s*(\\w+)\\.class,\\s*(\\d+),\\s*NetworkDirection\\.(\\w+)\\s*\\)");
    private static final Pattern ANY_CHANNEL = Pattern.compile(
            "ChannelBuilder|newSimpleChannel|newEventChannel|EventNetworkChannel|registerMessage\\(");

    @Test
    void everyChannelAndMessageMatchesTheCommittedTable() throws IOException {
        List<String> actual = new ArrayList<>();
        List<Path> channelSources = new ArrayList<>();
        List<String> emptyChannels = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!ANY_CHANNEL.matcher(source).find()) {
                    continue;
                }
                Matcher channel = CHANNEL.matcher(source);
                Matcher protocol = PROTOCOL.matcher(source);
                assertTrue(channel.find() && !channel.find(), "One named channel per source: " + file);
                assertTrue(protocol.find() && !protocol.find(), "One protocol version per source: " + file);
                channel.reset().find();
                protocol.reset().find();
                if ("classic_controls".equals(channel.group(1))) {
                    assertEquals("2", protocol.group(1), "R92 monitor protocol must reject version-1 peers");
                }
                Matcher message = MESSAGE.matcher(source);
                int messages = 0;
                while (message.find()) {
                    // Every handler runs on the main thread (ADR-047 relies on it for message ordering).
                    int end = source.indexOf(".add()", message.end());
                    assertTrue(end > 0 && source.substring(message.end(), end).contains(".consumerMainThread("),
                            "Not handled on the main thread: " + message.group(1) + " in " + file);
                    actual.add(String.join("|", ModIdentity.id(channel.group(1)).toString(), protocol.group(1),
                            message.group(2), message.group(1), message.group(3)));
                    messages++;
                }
                if ("machine_menu".equals(channel.group(1))) {
                    assertEquals("1", protocol.group(1), "Menu layout admission protocol changed: " + file);
                    assertEquals(0, messages, "Menu admission channel must remain message-empty: " + file);
                    emptyChannels.add(channel.group(1) + "|" + protocol.group(1));
                } else {
                    channelSources.add(file);
                    assertTrue(messages > 0, "A channel without messages: " + file);
                }
                assertEquals(source.split("messageBuilder\\(", -1).length - 1, messages,
                        "A message registration the pin table cannot read (for example a non-literal index): " + file);
            }
        }
        // Six earlier channel sources, plus W06's suit_motion and W07's classic_controls.
        assertEquals(8, channelSources.size(), "Channel sources changed: " + channelSources);
        assertEquals(List.of("machine_menu|1"), emptyChannels);
        assertEquals(expected(), actual.stream().sorted().toList());
    }

    private static List<String> expected() throws IOException {
        try (InputStream table = NetworkProtocolPinTest.class.getResourceAsStream("/network-protocols.txt")) {
            assertNotNull(table, "The committed protocol table is missing");
            return new String(table.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void r102KeepsProtocolTwoWithUnavailableMonitorRequirements() throws IOException {
        var kind = io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind.ROCKET_MONITOR;
        assertEquals(4, kind.wireId()); assertEquals(21, kind.valueCount());
        for (int index : new int[] {13, 18, 20}) {
            assertEquals(-1, kind.minimum(index)); assertEquals(2_048_000, kind.maximum(index));
        }
        assertEquals(3, expected().stream().filter(line -> line.startsWith(ModIdentity.id("classic_controls") + "|2|")).count());
    }
}
