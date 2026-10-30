package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Source-contract regression: inspect the actual dispatch, evaluating its selectors without a live server. */
class EscrowProbeRoutingTest {
    private static final Path RUNTIME = Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowRuntime.java");
    private static final String ITEM = "EscrowItemCutProbe.selected(true, System.getProperty(EscrowItemCutProbe.SCENARIO), testCase)";
    private static final String ITEM_BRANCH = "            } else if (" + ITEM + ") {\n"
            + "                // Item cuts already ran above; never create the synthetic fluid release fixture for them.\n";
    // W142's fluid cargo cuts (C-S4, C-S5), merged beside W143: their own exclusive branch ahead of the fallback.
    private static final String CARGO = "runtime.cargoCuts != null && EscrowCargoCutProbe.accepts(testCase)";
    private static final String CARGO_BRANCH = "            } else if (" + CARGO + ") {\n"
            + "                runtime.cargoCuts.tick();\n";
    private static final Pattern BRANCH = Pattern.compile("(?m)^ {12}(?:\\} else )?(?:if \\(([^\\n]+)\\) )?\\{$");
    private static final Pattern TICK = Pattern.compile("runtime\\.(\\w+)\\.tick\\(\\);");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void runtimePreservesTheHistoricalDispatchExceptForTheExclusiveItemBranch() throws Exception {
        String runtime = Files.readString(RUNTIME).replace("\r\n", "\n");
        String actual = dispatch(runtime);
        // Extracted verbatim from ab858afc4, before R77's generic allowlist. No Git access is needed to run this test.
        String before = resource("escrow-probe-dispatch-before-r77.txt");
        assertEquals(1, actual.split(Pattern.quote(ITEM_BRANCH), -1).length - 1);
        assertEquals(1, actual.split(Pattern.quote(CARGO_BRANCH), -1).length - 1);
        assertEquals(before, actual.replace(ITEM_BRANCH, "").replace(CARGO_BRANCH, ""),
                "Dispatch other than the item and cargo-cut branches must retain the old unconditional fallback");
        assertEquals(1, runtime.lines().filter(line -> line.trim().equals(
                "if (runtime.itemCutProbe != null) { runtime.itemCutProbe.tick(); }")).count());
        assertTrue(actual.indexOf(ITEM_BRANCH) < actual.lastIndexOf("            } else {"));
        String generic = Files.readString(RUNTIME.resolveSibling("EscrowReleaseProbe.java"));
        assertTrue(generic.contains("EscrowTags.require(permitsCase(System.getProperty(EscrowItemCutProbe.CASE)),"),
                "The generic constructor must use the tested exclusion guard");
    }

    @Test void everyInventoriedCaseRetainsItsProbeOrSelectsOnlyTheItemProbe() throws Exception {
        var before = branches(resource("escrow-probe-dispatch-before-r77.txt"));
        var after = branches(dispatch(Files.readString(RUNTIME).replace("\r\n", "\n")));
        var seen = new HashSet<String>(); var items = new HashSet<String>();
        for (String line : resource("escrow-probe-cases.tsv").lines().filter(line -> !line.startsWith("#")).toList()) {
            String[] fields = line.split("\t"); assertEquals(2, fields.length); assertTrue(seen.add(fields[0]));
            String name = fields[0];
            for (String scenario : List.of("start", "recover")) {
                boolean item = EscrowItemCutProbe.selected(true, scenario, name);
                boolean cargo = EscrowCargoCutProbe.accepts(name);
                assertFalse(item && cargo, "A case selected both cut probes: " + name);
                assertEquals(item ? "itemCutProbe" : cargo ? "cargoCuts" : route(before, name, scenario), route(after, name, scenario),
                        name + "/" + scenario);
                assertEquals(!item, EscrowReleaseProbe.permitsCase(name), "Constructor exclusion: " + name);
                if (item) {
                    items.add(name);
                    assertEquals(1, after.stream().filter(branch -> branch.condition() != null
                            && matches(branch.condition(), name, scenario)).count(), "Item selected another specialized probe: " + name);
                }
            }
        }
        assertEquals(610, seen.size()); // R203: adds the native sticky representative under unchanged producer rules.
        assertTrue(seen.contains(EscrowLoaderS6Probe.E3));
        var expected = new HashSet<>(EscrowItemSettlementCuts.CASES.keySet()); expected.addAll(EscrowItemRecoveryCuts.CASES);
        assertEquals(expected, items); assertEquals(18, items.size());
    }

    @Test void legacyStoreFaultAndUnenumeratedNamesStillReachTheGenericFallback() throws Exception {
        var after = branches(dispatch(Files.readString(RUNTIME).replace("\r\n", "\n")));
        for (String name : List.of("E1", "E3", "", "OLDER_RUNNER_CASE", "FUTURE_NATIVE_CASE", "IS_UNKNOWN", "IR_UNKNOWN")) {
            for (String scenario : List.of("start", "recover", "older-scenario")) {
                assertEquals("probe", route(after, name, scenario));
                assertTrue(EscrowReleaseProbe.permitsCase(name));
            }
        }
    }

    private static String resource(String name) throws Exception {
        try (var stream = EscrowProbeRoutingTest.class.getResourceAsStream("/" + name)) {
            return new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
    private static String dispatch(String source) {
        int start = source.indexOf("            if (testCase.equals(EscrowFlightBenchmarkProbe.CASE)) {");
        assertTrue(start >= 0);
        int end = source.indexOf("\n        }\n    }\n", start); assertTrue(end > start);
        return source.substring(start, end) + "\n";
    }
    private record Branch(String condition, String target) { }
    private static List<Branch> branches(String source) {
        var heads = BRANCH.matcher(source).results().toList(); var result = new ArrayList<Branch>();
        for (int index = 0; index < heads.size(); index++) {
            var head = heads.get(index); String condition = head.group(1);
            String body = source.substring(head.end(), index + 1 < heads.size() ? heads.get(index + 1).start() : source.length());
            var tick = TICK.matcher(body);
            String target;
            if (tick.find()) { target = tick.group(1); assertFalse(tick.find(), "Multiple dispatch targets"); }
            else if ("testCase.startsWith(\"SR_\")".equals(condition)) { target = "settlementCompletionProbe"; }
            else if ("testCase.startsWith(\"SO_\")".equals(condition)) { target = "settlementProbe"; }
            else if (ITEM.equals(condition)) { target = "itemCutProbe"; }
            else { throw new AssertionError("Unrecognized dispatch body: " + body); }
            result.add(new Branch(condition, target));
        }
        assertFalse(result.isEmpty()); assertNull(result.get(result.size() - 1).condition(), "Fallback must be unconditional");
        return result;
    }
    private static String route(List<Branch> branches, String name, String scenario) {
        return branches.stream().filter(branch -> branch.condition() == null || matches(branch.condition(), name, scenario))
                .findFirst().orElseThrow().target();
    }
    private static boolean matches(String expression, String name, String scenario) {
        boolean result = false;
        for (String term : expression.split(" \\|\\| ")) { result |= term(term, name, scenario); }
        return result;
    }
    private static boolean term(String term, String name, String scenario) {
        if (term.equals("testCase.equals(EscrowFlightBenchmarkProbe.CASE)")) { return name.equals(EscrowFlightBenchmarkProbe.CASE); }
        if (term.equals("testCase.startsWith(EscrowLoaderReleaseProbe.PREFIX)")) { return name.startsWith(EscrowLoaderReleaseProbe.PREFIX); }
        if (term.equals("EscrowCargoReconciliationProbe.accepts(testCase)")) { return EscrowCargoReconciliationProbe.accepts(name); }
        if (term.equals(ITEM)) { return EscrowItemCutProbe.selected(true, scenario, name); }
        if (term.equals(CARGO)) { return EscrowCargoCutProbe.accepts(name); }
        var literal = Pattern.compile("testCase\\.(equals|startsWith)\\(\"([^\"]+)\"\\)").matcher(term);
        assertTrue(literal.matches(), "Unrecognized dispatch predicate: " + term);
        return literal.group(1).equals("equals") ? name.equals(literal.group(2)) : name.startsWith(literal.group(2));
    }
}
