"""Diagnostic export only: explicit source-level elapsed spans, never a runtime transformer."""
from __future__ import annotations
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re

PACKAGE = "io/github/sunthemoon/advancedrocketrycommunity"
PROBE = "io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.W212Timing"
STARTUP_TARGET = "resourceescrow/EscrowRelaunchClasses.java"
TARGETS = {
    STARTUP_TARGET: [("static void initialize()", "startup.initialize")],
    "resourceescrow/EscrowGovernedPreparation.java": [
        ("public static boolean begin(RocketEntity rocket,", "preparation.begin")],
    "resourceescrow/EscrowGovernedLaunch.java": [
        ("static EscrowGovernedLaunch prepare(", "launch.prepare"),
        ("EscrowLedger preflight(", "launch.preflight"),
        ("synchronized RocketTransferRecord record(", "launch.record")],
    "resourceescrow/EscrowFlightCheckpoint.java": [
        ("private boolean begin(RocketEntity rocket, EscrowFlightAuthority.Launch launch,", "checkpoint.begin")],
    "resourceescrow/EscrowFlightSource.java": [
        ("EscrowFlightSource(RocketEntity rocket, java.util.function.BooleanSupplier boundary)", "source.capture")],
    "resourceescrow/EscrowGovernedEnvelope.java": [
        ("public static CompoundTag capture(", "source.envelope"),
        ("public static CompoundTag comparison(", "envelope.comparison")],
    "resourceescrow/EscrowNativeEnvelope.java": [
        ("public static String digest(", "envelope.digest")],
    "resourceescrow/EscrowTags.java": [("static void bound(", "nbt.bound")],
    "resourceescrow/EscrowTransferEnvelope.java": [
        ("public static EscrowTransferEnvelope capture(", "transfer.capture"),
        ("public CompoundTag encode()", "transfer.encode"),
        ("public synchronized void validate(", "transfer.validate")],
    "rocket/flight/persistence/RocketFlightNbtCodec.java": [
        ("public static RocketFlightDecodeResult decode(", "flight.decode"),
        ("private static RocketFuelState decodeFuel(", "fuel.decode"),
        ("public static CompoundTag encode(", "flight.encode")],
    "rocket/model/RocketPropulsionFuel.java": [("public String resourceSignature()", "fuel.signature")],
    "rocket/flight/persistence/RocketTransferJournalCodec.java": [
        ("static CompoundTag encodeRecord(", "journal.encodeRecord")],
    "rocket/flight/persistence/RocketTransferSavedData.java": [
        ("public void validateNewRecord(", "journal.validateNewRecord")],
}


def body_end(source, start):
    # Ignore lexical braces in Java comments and string/character literals.
    token = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]', re.S)
    depth = 0
    for match in token.finditer(source, start):
        if match.group() == "{": depth += 1
        elif match.group() == "}":
            depth -= 1
            if depth == 0: return match.start()
    raise ValueError("Unclosed method")


def wrap(source, anchor, label):
    if source.count(anchor) != 1:
        raise ValueError(f"Expected exactly one declaration: {anchor}")
    start = source.index("{", source.index(anchor))
    end = body_end(source, start)
    scope = f'{PROBE}.startup()' if label == "startup.initialize" else f'{PROBE}.open("{label}")'
    return (source[:start + 1] + f'\n        try (var w212Span = {scope}) {{'
            + source[start + 1:end] + "\n        }\n    " + source[end:])


def instrument(export, output):
    if not (export / ".w206-owned").is_file():
        raise ValueError("Only an owned, disposable native export may be instrumented")
    manifest, patch = [], []
    for relative, spans in TARGETS.items():
        path = export / "src/main/java" / PACKAGE / relative
        # The committed before-remedy source predates W215; do not add a startup step to that control.
        if relative == STARTUP_TARGET and not path.exists():
            continue
        before_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        before = path.read_text(encoding="utf-8")
        after = before
        for anchor, label in spans: after = wrap(after, anchor, label)
        path.write_text(after, encoding="utf-8")
        manifest.append(dict(path=relative, before=before_hash,
                             after=hashlib.sha256(path.read_bytes()).hexdigest(), spans=spans))
        patch.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True), relative, relative))
    path = export / "src/main/java" / PACKAGE / "rocket/server/W206NativeWarmup.java"
    source = path.read_text(encoding="utf-8")
    before = source
    before_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    source = source.replace("this.server = server;", f"{PROBE}.start(); this.server = server;", 1)
    source = source.replace("done = true; unsubscribe.run();", f'{PROBE}.write(root.resolve("w212-spans.json"));\n        done = true; unsubscribe.run();', 1)
    for anchor, label in [("private void step()", "fixture.step"), ("private void snapshot(", "observer.transition"),
                          ("private RocketFlightRequestResult request(", "request.apiAndEvent")]:
        source = wrap(source, anchor, label)
    path.write_text(source, encoding="utf-8")
    relative = "rocket/server/W206NativeWarmup.java"
    manifest.append(dict(path=relative, before=before_hash, after=hashlib.sha256(path.read_bytes()).hexdigest()))
    patch.extend(difflib.unified_diff(before.splitlines(True), source.splitlines(True), relative, relative))
    output.mkdir(parents=True, exist_ok=True)
    (output / "instrumentation.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (output / "instrumentation.patch").write_text("".join(patch), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    instrument(args.export, args.output)
