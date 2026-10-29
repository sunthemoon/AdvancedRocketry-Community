package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RocketI6EntryArchitectureTest {
    @Test void transferServiceKeepsI6EntryOutsideItsSizeBudget() throws Exception {
        var source = Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server");
        assertTrue(Files.readAllLines(source.resolve("RocketTransferService.java")).size() < 800,
                "Extract I6 entry decisions rather than growing the transfer service");
        assertTrue(Files.exists(source.resolve("RocketI6Entry.java")));
    }
}
