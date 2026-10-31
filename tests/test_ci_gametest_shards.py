import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from scripts import ci_gametest_shards as shards


def test_case(batch, name):
    return {"batch": batch, "name": name, "structure": "example:empty", "required": True}


class PartitionTests(unittest.TestCase):
    def setUp(self):
        self.inventory = [test_case(f"batch_{i}", f"case_{j}") for i in range(20) for j in range(2)]
        self.plan = shards.partition(self.inventory)

    def test_partition_is_complete_disjoint_and_order_independent(self):
        shards.validate_partition(self.inventory, self.plan)
        self.assertEqual(self.plan, shards.partition(list(reversed(self.inventory))))
        self.assertEqual(40, sum(len(tests) for tests in self.plan.values()))

    def test_missing_batch_fails(self):
        victim = self.inventory[0]["batch"]
        plan = {key: [t for t in tests if t["batch"] != victim] for key, tests in self.plan.items()}
        with self.assertRaisesRegex(ValueError, "partition"):
            shards.validate_partition(self.inventory, plan)

    def test_duplicated_batch_fails(self):
        plan = copy.deepcopy(self.plan)
        owner = shards.shard_for("batch_0")
        plan[(owner + 1) % shards.SHARD_COUNT] += [t for t in self.inventory if t["batch"] == "batch_0"]
        with self.assertRaisesRegex(ValueError, "partition"):
            shards.validate_partition(self.inventory, plan)

    def reports(self):
        return [{"shard": key, "inventory": self.inventory, "executed": tests,
                 "complete": True, "failed": [], "native_exit": 0,
                 "bundle_sha256": "a" * 64} for key, tests in self.plan.items()]

    def test_exact_union_passes(self):
        self.assertEqual(40, shards.check_union(self.reports(), "a" * 64)["tests"])

    def test_missing_test_in_union_fails(self):
        reports = self.reports()
        next(r for r in reports if r["executed"])["executed"].pop()
        with self.assertRaisesRegex(ValueError, "executed"):
            shards.check_union(reports, "a" * 64)

    def test_duplicate_execution_fails(self):
        reports = self.reports()
        reports[0]["executed"] += reports[0]["executed"][:1]
        with self.assertRaises(ValueError):
            shards.check_union(reports, "a" * 64)

    def test_failed_cancelled_timed_out_or_absent_shard_fails(self):
        for field, value in (("native_exit", 1), ("complete", False), ("failed", ["case"]),
                             ("job_result", "cancelled"), ("job_result", "failure"),
                             ("bundle_sha256", "b" * 64)):
            with self.subTest(field=field, value=value):
                reports = self.reports()
                reports[0][field] = value
                with self.assertRaises(ValueError):
                    shards.check_union(reports, "a" * 64)
        with self.assertRaises(ValueError):
            shards.check_union(self.reports()[:-1], "a" * 64)

    def test_different_full_inventory_fails(self):
        reports = self.reports()
        reports[0]["inventory"] = self.inventory[:-1]
        with self.assertRaisesRegex(ValueError, "inventory"):
            shards.check_union(reports, "a" * 64)

    def test_new_batch_is_assigned_by_sha256_rule(self):
        self.assertEqual(int.from_bytes(hashlib.sha256(b"new_batch").digest()[:4], "big") % 4,
                         shards.shard_for("new_batch"))
        inventory = self.inventory + [test_case("new_batch", "new_case")]
        shards.validate_partition(inventory, shards.partition(inventory))

    def test_duplicate_inventory_identity_fails(self):
        with self.assertRaises(ValueError):
            shards.partition(self.inventory + self.inventory[:1])


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.manifest = {"schema": 1, "expectations": [
            {"id": "exact", "counts": {"one": 2, "two": 3}},
            {"id": "startup", "max": 1, "batches": ["<startup>"]},
            {"id": "global", "max": 8},
        ]}

    def test_slice_preserves_patterns_and_counts(self):
        sliced = shards.slice_manifest(self.manifest, {"one"})
        self.assertEqual({"one": 2}, sliced["expectations"][0]["counts"])
        self.assertEqual(self.manifest["expectations"][1:], sliced["expectations"][1:])
        self.assertEqual({"one": 2, "two": 3}, self.manifest["expectations"][0]["counts"])

    def test_union_warning_ceiling_is_not_multiplied(self):
        with self.assertRaisesRegex(ValueError, "global"):
            shards.check_global_ceilings(self.manifest, [{"global": 5}, {"global": 4}])
        shards.check_global_ceilings(self.manifest, [{"global": 4}, {"global": 4}])

    def test_documented_once_per_start_warning_keeps_per_process_ceiling(self):
        entry = {"id": "w44-first-moon-landing-startup-baseline", "max": 1}
        shards.check_global_ceilings({"expectations": [entry]}, [{entry["id"]: 1}] * 4)


class ArtifactTests(unittest.TestCase):
    def test_observer_has_minecraft_1201_pack_metadata(self):
        root = Path(__file__).resolve().parents[1]
        metadata = json.loads((root / "scripts/ci_gametest/resources/pack.mcmeta").read_text(encoding="utf-8"))
        self.assertEqual(15, metadata["pack"]["pack_format"])
        self.assertTrue(metadata["pack"]["description"].strip())

    def test_restore_checks_identity_and_exact_runtime_contents(self):
        import zipfile
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = root / "bundle"
            bundle.mkdir()
            for name in shards.RUNTIME_SETS:
                with zipfile.ZipFile(bundle / f"{name}.jar", "w") as jar:
                    jar.writestr("example/Fixture.class", b"class bytes")
            (bundle / "release.jar").write_bytes(b"release bytes")
            shards.seal(bundle, "a" * 40)
            shards.restore(bundle, root, "a" * 40)
            shards.verify_runtime(bundle, root, "a" * 40)
            (root / "build/classes/java/main/example/Fixture.class").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "runtime"):
                shards.verify_runtime(bundle, root, "a" * 40)
            with self.assertRaises(ValueError):
                shards.verify_bundle(bundle, "b" * 40)
            (bundle / "release.jar").write_bytes(b"wrong")
            with self.assertRaises(ValueError):
                shards.verify_bundle(bundle, "a" * 40)

    def test_unsafe_or_duplicate_jar_entries_fail(self):
        import warnings
        import zipfile
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "unsafe.jar"
            for entries in (("../escape.class",), ("/escape",), ("C:/escape",), ("same", "same")):
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    with zipfile.ZipFile(path, "w") as jar:
                        for entry in entries:
                            jar.writestr(entry, b"bytes")
                with self.assertRaises(ValueError):
                    shards.jar_entries(path)


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        from test_check_gametest_log import line, LAUNCH, SHUTDOWN, BATCH_LOGGER, SERVER_LOGGER
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.manifest = self.root / "manifest.json"
        shards.write_json(self.manifest, {"schema": 1, "expectations": [{
            "id": "startup", "level": "WARN", "logger": "fixture/", "message": "fixture warning",
            "max": 1, "batches": ["<startup>"], "test": "environment", "reason": "Test fixture."
        }]})
        self.inventory = [test_case(f"batch_{i}", f"case_{j}") for i in range(20) for j in range(2)]
        for index, tests in shards.partition(self.inventory).items():
            folder = self.root / f"shard-{index}"
            report = {"schema": 1, "shard": index, "inventory": self.inventory, "executed": tests,
                      "complete": True, "failed": [], "bundle_sha256": "a" * 64}
            shards.write_json(folder / "result.json", report)
            (folder / "native-exit.txt").write_text("0\n")
            lines = [LAUNCH, line("WARN", "fixture/", "fixture warning")]
            for batch in sorted({t["batch"] for t in tests}):
                lines.append(line("INFO", BATCH_LOGGER, f"Running test batch '{batch}:1' (2 tests)..."))
            lines += [line("INFO", SERVER_LOGGER, f"========= {len(tests)} GAME TESTS COMPLETE ========="),
                      line("INFO", SERVER_LOGGER, f"All {len(tests)} required tests passed :)"), SHUTDOWN]
            path = folder / "logs/latest.log"
            path.parent.mkdir()
            path.write_text("\n".join(lines) + "\n")

    def check(self, index=0):
        import contextlib
        import io
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return shards.check_shard(self.root / f"shard-{index}", self.manifest)

    def test_native_logs_and_terminal_callbacks_agree(self):
        reports = [self.check(index)[0] for index in range(4)]
        self.assertEqual(40, shards.check_union(reports, "a" * 64)["tests"])

    def test_unexpected_warn_still_fails_unchanged_checker(self):
        path = self.root / "shard-0/logs/latest.log"
        text = path.read_text().replace("fixture warning", "unknown warning")
        path.write_text(text)
        with self.assertRaisesRegex(ValueError, "log check failed"):
            self.check()

    def test_log_test_count_and_batch_coverage_must_equal_inventory(self):
        path = self.root / "shard-0/logs/latest.log"
        path.write_text(path.read_text().replace("(2 tests)", "(1 tests)", 1))
        with self.assertRaisesRegex(ValueError, "counts differ"):
            self.check()

    def test_missing_native_exit_and_incomplete_logs_fail(self):
        (self.root / "shard-0/native-exit.txt").unlink()
        with self.assertRaises(OSError):
            self.check()
        path = self.root / "shard-1/logs/latest.log"
        path.write_text(path.read_text().rsplit("\n", 2)[0] + "\n")
        with self.assertRaises(ValueError):
            self.check(1)

    def test_missing_manifest_batch_fails_not_silently_dropped(self):
        manifest = shards.read_json(self.manifest)
        manifest["expectations"].append({"id": "lost", "counts": {"unregistered_batch": 1}})
        shards.write_json(self.manifest, manifest)
        with self.assertRaisesRegex(ValueError, "absent batches"):
            self.check()

    def test_failed_native_receipt_or_report_fails_local_check(self):
        (self.root / "shard-0/native-exit.txt").write_text("1\n")
        with self.assertRaisesRegex(ValueError, "native shard"):
            self.check()
        path = self.root / "shard-1/result.json"
        report = shards.read_json(path)
        report["complete"] = False
        shards.write_json(path, report)
        with self.assertRaisesRegex(ValueError, "native shard"):
            self.check(1)

    def test_union_cli_rejects_cancelled_matrix(self):
        import contextlib
        import io
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, shards.main([
                "union", "--evidence", str(self.root), "--bundle", str(self.root),
                "--commit", "a" * 40, "--job-result", "cancelled",
            ]))


if __name__ == "__main__":
    unittest.main()
