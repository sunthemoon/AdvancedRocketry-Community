import csv
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from scripts.campaign_observe import host, process


class ProcessHostTests(unittest.TestCase):
    def test_proc_status_kib_conversion_and_missing(self):
        self.assertEqual(123 * 1024, process.parse_proc_status("Name:\tjava\nVmRSS:\t123 kB\nVmSize:\t456 kB\n"))
        with self.assertRaises(ValueError):
            process.parse_proc_status("Name:\tjava\n")

    def test_proc_stat_uses_right_fields_and_comm_parentheses(self):
        fields = ["0"] * 22
        fields[0], fields[11], fields[12], fields[19] = "S", "120", "30", "12345"
        self.assertEqual({"cpu_seconds": 1.5, "birth_id": "12345"},
                         process.parse_proc_stat("42 (name (space)) " + " ".join(fields), 100))

    def test_pid_validation(self):
        for pid in (0, -1, "java", "123", True):
            with self.assertRaises(ValueError):
                process.valid_pid(pid)

    def test_heap_info_g1_parallel_and_metaspace_exclusion(self):
        g1 = "42:\n garbage-first heap total 262144K, used 12345K [0x0, 0x1)\n Metaspace used 123K, committed 256K\n"
        self.assertEqual({"heap_committed_bytes": 262144 * 1024, "heap_used_bytes": 12345 * 1024},
                         process.parse_heap_info(g1))
        parallel = "PSYoungGen total 10K, used 2K\nParOldGen total 20K, used 3K\nMetaspace total 50K, used 30K\n"
        self.assertEqual({"heap_committed_bytes": 30 * 1024, "heap_used_bytes": 5 * 1024},
                         process.parse_heap_info(parallel))
        with self.assertRaises(ValueError):
            process.parse_heap_info("Metaspace used 50K")

    def test_process_csv_and_pid_reuse(self):
        def reader(pid):
            return {"rss_bytes": 1000, "cpu_seconds": 1.25, "birth_id": "birth"}
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "process.csv"
            count = process.sample_process(123, output, duration=2, reader=reader, scheduler=lambda *a: range(2))
            self.assertEqual(2, count)
            with output.open(newline="") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual("1000", rows[0]["rss_bytes"])
            self.assertEqual("", rows[0]["heap_used_bytes"])
            self.assertTrue(rows[0]["utc"].endswith("Z"))
            responses = iter([reader(123), {**reader(123), "birth_id": "other"}])
            with self.assertRaisesRegex(ValueError, "reused"):
                process.sample_process(123, Path(temp) / "reuse.csv", duration=2,
                                       reader=lambda pid: next(responses), scheduler=lambda *a: range(2))

    def test_only_campaign_server_may_get_automatic_heap_samples(self):
        from scripts.campaign_observe.jdk import CampaignJvm
        handle = Mock(pid=123)
        handle.poll.return_value = None
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "heap.csv"
            with self.assertRaises(ValueError):
                process.sample_process(123, output, duration=1, heap_target=CampaignJvm(handle, "client"))
            target = CampaignJvm(handle, "server")
            with patch("scripts.campaign_observe.jdk.heap_info", return_value="garbage-first heap total 10K, used 3K") as heap:
                count = process.sample_process(123, output, duration=1, heap_target=target,
                    reader=lambda pid: {"birth_id": "b", "rss_bytes": 9, "cpu_seconds": 1}, scheduler=lambda *a: range(1))
                self.assertEqual(1, count)
                heap.assert_called_once_with(target)

    def test_native_current_python_process_without_jvm_attachment(self):
        result = process.read_process(os.getpid())
        self.assertGreater(result["rss_bytes"], 0)
        self.assertGreaterEqual(result["cpu_seconds"], 0)
        self.assertTrue(result["birth_id"])

    def test_meminfo_and_steal_exclude_guest_double_count(self):
        self.assertEqual({"memory_mib": 8192, "swap_mib": 8192},
                         host.parse_meminfo("MemTotal: 8388608 kB\nSwapTotal: 8388608 kB\n"))
        before = "cpu  100 0 0 100 0 0 0 0 50 0\ncpu0 1 2\n"
        after = "cpu  140 0 0 140 0 0 0 20 80 0\n"
        self.assertEqual(20, host.cpu_steal_percent(before, after))
        with self.assertRaises(ValueError):
            host.cpu_steal_percent(before, before)

    def test_linux_host_reads_two_cpu_snapshots(self):
        with tempfile.TemporaryDirectory() as temp:
            proc = Path(temp)
            (proc / "meminfo").write_text("MemTotal: 8388608 kB\nSwapTotal: 8388608 kB\n")
            (proc / "stat").write_text("cpu 100 0 0 100 0 0 0 0\n")
            def sleep(seconds):
                self.assertEqual(1, seconds)
                (proc / "stat").write_text("cpu 140 0 0 140 0 0 0 20\n")
            with patch.object(host, "os", SimpleNamespace(name="posix", cpu_count=lambda: 4)):
                result = host.measure_host("provider", "plan", temp, proc=proc, sleep=sleep)
            self.assertEqual(4, result["vcpu"])
            self.assertEqual(8192, result["swap_mib"])
            self.assertEqual(20, result["cpu_steal_average"])
            self.assertGreater(result["disk_free_gib"], 0)

    def test_host_record_identity_no_overwrite_and_credentials(self):
        run = {"git_commit": "a" * 40, "jar_sha256": "b" * 64, "forge": "47.4.10", "java": "17",
               "command": "bash run.sh nogui", "start_utc": "2026-11-20T00:00:00Z",
               "end_utc": "2026-11-20T01:00:00Z", "exit_code": 0}
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "host.json"
            def write(value):
                return host.write_host_record(output, provider="given", plan="given", disk=temp,
                                              run=value, measure=lambda *a: {"vcpu": 4, "memory_mib": 8192})
            write(run)
            self.assertEqual(run, json.loads(output.read_text())["run"])
            with self.assertRaises(FileExistsError):
                write(run)
            with self.assertRaises(ValueError):
                write({**run, "command": "java --accessToken secret"})
            with self.assertRaises(ValueError):
                write({**run, "end_utc": "2026-11-19T01:00:00Z"})
            with self.assertRaises(ValueError):
                write({**run, "start_utc": "2026-11-20T00:00:00"})
