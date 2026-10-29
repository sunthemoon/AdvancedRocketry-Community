"""Pin diagnostic measurement boundaries without starting a native JVM."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/W206NativeWarmup.java"


class ObserverBoundaryTest(unittest.TestCase):
    def test_tick_capture_buffers_without_serializing_or_writing(self):
        source = JAVA.read_text(encoding="utf-8")
        capture = source.split("private void captureTick(", 1)[1].split("private Collection", 1)[0]
        self.assertIn("tickRows.add(", capture)
        self.assertNotIn("write(ticks", capture)
        self.assertNotIn("toJson", capture)

    def test_recording_cost_and_transition_observation_are_reported(self):
        source = JAVA.read_text(encoding="utf-8")
        self.assertIn('"recorder_tail_ns"', source)
        self.assertIn('"transition_observer_ns"', source)
        finish = source.split("private void finish()", 1)[1]
        self.assertIn("tickRows", finish)


if __name__ == "__main__":
    unittest.main()
