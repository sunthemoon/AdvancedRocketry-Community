"""Synthetic halt/thread oracles only; actual Moon cuts are recorded separately."""
import unittest

from scripts import run_v180_first_event_moon_cuts as moon


class MoonCutBoundaryTest(unittest.TestCase):
    @staticmethod
    def process(case, thread, event="null"):
        class Process:
            lines = [f"ARCE_RELEASE_TEST first_event_cut id={case} boundary={moon.CASES[case][0]} "
                     f"event={event} thread={thread}"] * 2

            @staticmethod
            def finish(timeout):
                return 75
        return Process()

    def test_writer_force_cut_requires_the_writer_thread_and_deduplicates_its_marker(self):
        entry = moon.halted("FM2b", self.process("FM2b", "ARCE-rocket-journal"), 1)
        self.assertEqual("ARCE-rocket-journal", entry["thread"])
        self.assertEqual("null", entry["event"])
        with self.assertRaises(moon.smoke.SmokeError):
            moon.halted("FM2b", self.process("FM2b", "Server thread"), 1)

    def test_unknown_publication_cut_requires_the_server_thread(self):
        event = "00000000-0000-4000-8000-000000000001"
        entry = moon.halted("FM6a", self.process("FM6a", "Server thread", event), 1)
        self.assertEqual(event, entry["event"])
        with self.assertRaises(moon.smoke.SmokeError):
            moon.halted("FM6a", self.process("FM6a", "ARCE-rocket-journal", event), 1)


if __name__ == "__main__":
    unittest.main()
