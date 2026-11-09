package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/** W328: the startup lines with the given prefixes, as "LEVEL message", captured from the root logger for one block. */
final class StartupLogLines extends org.apache.logging.log4j.core.appender.AbstractAppender implements AutoCloseable {
    private final org.apache.logging.log4j.core.Logger root =
            (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
    private final List<String> prefixes;
    final List<String> lines = new CopyOnWriteArrayList<>();

    StartupLogLines(String... prefixes) {
        super("w328-startup-lines", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
        this.prefixes = List.of(prefixes);
        start(); root.addAppender(this);
    }

    @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
        String line = event.getMessage().getFormattedMessage();
        if (prefixes.stream().anyMatch(line::startsWith)) { lines.add(event.getLevel() + " " + line); }
    }

    @Override public void close() { root.removeAppender(this); stop(); }

    /** Every entry under a directory with its bytes, so a test can show that nothing was created, removed or changed. */
    static Map<String, String> tree(Path directory) throws Exception {
        var entries = new TreeMap<String, String>();
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk.toList()) {
                entries.put(directory.relativize(path).toString(),
                        Files.isRegularFile(path) ? HexFormat.of().formatHex(Files.readAllBytes(path)) : "<dir>");
            }
        }
        return entries;
    }
}
