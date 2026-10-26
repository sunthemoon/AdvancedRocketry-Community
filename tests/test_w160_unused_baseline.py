"""R113's declaration is confined to the unused-world downgrade start."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w160_unused_roundtrip as runner


ID = "advancedrocketrycommunity:rocket_monitoring_station"


def event(level, message, *body):
    return [f"[22:33:31] [main/{level}] [ne.mi.re.GameData/REGISTRIES]: {message}", *body]


def declaration_events():
    return [
        event("ERROR", "Unidentified mapping from registry minecraft:block", f"\t{ID}: 1135", ""),
        event("ERROR", "Unidentified mapping from registry minecraft:item", f"\t{ID}: 1504", ""),
        event("WARN", "Forge Mod Loader detected missing registry entries.", "",
              "There are 2 missing entries in this save.",
              "If you continue the missing entries will get removed.",
              "A world backup will be automatically created in your saves directory.", "", ""),
        event("WARN", "Missing minecraft:item:", f"    {ID}", "",
              "Missing minecraft:block:", f"    {ID}", "", ""),
        event("ERROR", "There are unidentified mappings in this world - we are going to attempt to process anyway"),
    ]


def flatten(events):
    return [line for lines in events for line in lines]


class UnusedBaselineLogTests(unittest.TestCase):
    def test_exact_retained_declaration_passes_only_on_downgrade(self):
        result = runner.audit_log(flatten(declaration_events()), 3)
        self.assertEqual({"block_mapping": 1, "item_mapping": 1, "summary": 1,
                          "item_warning": 1, "block_warning": 1, "attempt": 1}, result["observed"])
        self.assertEqual(3, result["raw"]["error_count"])

    def test_optional_block_warning_and_attempt_may_be_absent(self):
        events = declaration_events()[:4]
        events[3] = event("WARN", "Missing minecraft:item:", f"    {ID}")
        result = runner.audit_log(flatten(events), 3)
        self.assertEqual(0, result["observed"]["block_warning"])
        self.assertEqual(0, result["observed"]["attempt"])

    def test_block_warning_can_have_its_own_header(self):
        events = declaration_events()
        events[3] = event("WARN", "Missing minecraft:item:", f"    {ID}")
        events.insert(4, event("WARN", "Missing minecraft:block:", f"    {ID}"))
        self.assertEqual(1, runner.audit_log(flatten(events), 3)["observed"]["block_warning"])

    def test_missing_required_event_fails(self):
        for index in range(4):
            with self.subTest(event=index):
                events = declaration_events(); del events[index]
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log(flatten(events), 3)

    def test_missing_id_or_summary_line_fails(self):
        for index, line in ((0, 1), (1, 1), (2, 2), (3, 1)):
            with self.subTest(event=index, line=line):
                events = declaration_events(); del events[index][line]
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log(flatten(events), 3)

    def test_duplicate_events_and_listed_ids_fail(self):
        for index in range(5):
            with self.subTest(event=index):
                events = declaration_events(); events.append(events[index][:])
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log(flatten(events), 3)
        for index in (0, 1, 3):
            with self.subTest(listed_id=index):
                events = declaration_events(); events[index].insert(2, events[index][1])
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log(flatten(events), 3)

    def test_extra_missing_id_and_another_mod_fail(self):
        for identity in ("advancedrocketrycommunity:sealed_rocket_block", "anothermod:rocket_monitoring_station"):
            for index in (0, 1, 3):
                with self.subTest(identity=identity, event=index):
                    events = declaration_events()
                    events[index].insert(2, f"\t{identity}: 999" if index < 2 else f"    {identity}")
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.audit_log(flatten(events), 3)
            with self.subTest(replacement=identity):
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log([s.replace(ID, identity) for s in flatten(declaration_events())], 3)

    def test_wrong_missing_entry_count_fails(self):
        for count in (0, 1, 3, 22):
            with self.subTest(count=count):
                lines = [s.replace("2 missing entries", f"{count} missing entries") for s in flatten(declaration_events())]
                with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(lines, 3)

    def test_declaration_even_warn_only_fails_in_starts_one_and_two(self):
        for start in (1, 2):
            runner.audit_log([], start)
            for events in ([e] for e in declaration_events()):
                with self.subTest(start=start, event=events[0][0]):
                    with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(flatten(events), start)
            with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(flatten(declaration_events()), start)

    def test_wrong_origin_or_level_fails(self):
        for index in range(5):
            for before, after in (("main/", "Server thread/"), ("ne.mi.re.GameData/REGISTRIES", "other/REGISTRIES"),
                                  ("/WARN]", "/INFO]") if index in (2, 3) else ("/ERROR]", "/WARN]")):
                with self.subTest(event=index, replacement=after):
                    events = declaration_events(); events[index][0] = events[index][0].replace(before, after)
                    with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(flatten(events), 3)

    def test_unexpected_continuations_and_other_diagnostics_are_not_suppressed(self):
        for index in range(5):
            events = declaration_events(); events[index].append("java.lang.IllegalStateException: unrelated")
            with self.subTest(event=index):
                with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(flatten(events), 3)
        for extra in ("[22:33:31] [main/ERROR] [minecraft/Server]: unrelated failure",
                      "[22:33:31] [main/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: unexpected warning",
                      "[22:33:31] [main/FATAL] [minecraft/Server]: unrelated failure"):
            with self.subTest(extra=extra):
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.audit_log(flatten(declaration_events()) + [extra], 3)

    def test_invalid_start_is_rejected(self):
        for start in (0, 4):
            with self.assertRaises(runner.smoke.SmokeError): runner.audit_log([], start)


if __name__ == "__main__":
    unittest.main()
