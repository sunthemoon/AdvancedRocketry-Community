import dataclasses
import unittest
from unittest.mock import Mock

from scripts.campaign_client.attach import AttachedClient, WindowIdentity
from scripts.campaign_client.cells import load_cell
from scripts.campaign_client.input import Input
from test_campaign_client_support import FakeWindows, Fixture


class AttachTests(Fixture):
    def setUp(self):
        super().setUp()
        self.backend = FakeWindows()
        self.cell = load_cell(self.cell_path)

    def attach(self):
        return AttachedClient(self.attach_spec, self.cell, self.backend)

    def test_one_window_records_identity_inventory_and_label(self):
        attached = self.attach()
        attached.verify()
        self.assertEqual("owner-one", attached.record["account_label"])
        self.assertEqual(1, len(attached.record["mods"]))
        self.assertEqual(123, attached.record["process"]["pid"])
        self.assertIn("verified_at", attached.record)
        self.assertIn("last_verified_at", attached.record)

    def test_zero_and_several_windows(self):
        original = self.backend.found[0]
        for windows in ([], [original, dataclasses.replace(original, handle=789)]):
            self.backend.found = windows
            with self.assertRaisesRegex(ValueError, "exactly one"):
                self.attach()

    def test_wrong_title_hidden_class_or_owner(self):
        original = self.backend.found[0]
        for change in ({"title": "Other"}, {"visible": False}, {"class_name": "ConsoleWindowClass"}, {"pid": 999}):
            self.backend.found = [dataclasses.replace(original, **change)]
            with self.assertRaises(ValueError):
                self.attach()

    def test_process_name_and_missing_process(self):
        self.backend.identity = dataclasses.replace(self.backend.identity, name="notepad.exe")
        with self.assertRaises(ValueError):
            self.attach()
        self.backend.identity = None
        with self.assertRaises(ValueError):
            self.attach()

    def test_changed_creation_time_refused(self):
        attached = self.attach()
        self.backend.identity = dataclasses.replace(self.backend.identity, created=self.backend.identity.created + 1)
        with self.assertRaisesRegex(ValueError, "Process identity"):
            attached.verify()

    def test_changed_handle_refused(self):
        attached = self.attach()
        self.backend.found = [dataclasses.replace(self.backend.found[0], handle=777)]
        with self.assertRaisesRegex(ValueError, "Window identity"):
            attached.verify()

    def test_foreground_required_and_no_automatic_focus(self):
        attached = self.attach()
        self.backend.front = 777
        with self.assertRaisesRegex(ValueError, "foreground"):
            attached.verify()
        attached.verify(foreground=False)
        self.assertEqual([], self.backend.events)

    def test_attach_can_never_close(self):
        with self.assertRaisesRegex(ValueError, "cannot close"):
            self.attach().close()

    def test_latest_user_not_first_user(self):
        path = self.game / "logs/latest.log"
        with path.open("a", encoding="utf-8") as stream:
            stream.write("[INFO] Setting user: SomeoneElse\n")
        with self.assertRaisesRegex(ValueError, "player name"):
            self.attach()

    def test_stale_log_wrong_size_and_options(self):
        self.backend.identity = dataclasses.replace(self.backend.identity, created=200000000000000000)
        with self.assertRaisesRegex(ValueError, "predates"):
            self.attach()
        self.backend = FakeWindows()
        self.attach_spec["window_size"] = [960, 540]
        with self.assertRaisesRegex(ValueError, "size"):
            self.attach()
        self.attach_spec["window_size"] = [1280, 720]
        self.attach_spec["language"] = "zh_cn"
        with self.assertRaisesRegex(ValueError, "options"):
            self.attach()

    def test_input_checks_each_event_and_refuses_without_focus(self):
        attached = self.attach()
        driver = Input(self.backend, attached.handle, attached.verify, 0.05, lambda _: None, "attach")
        self.backend.front = 777
        with self.assertRaises(ValueError):
            driver.perform({"op": "text", "text": "hello"})
        self.assertEqual([], self.backend.events)

    def test_inputs_minimum_pacing_and_held_keys(self):
        guard = Mock()
        sleep = Mock()
        driver = Input(self.backend, 456, guard, 0.05, sleep, "attach")
        driver.perform({"op": "keydown", "key": "W"})
        self.assertIn(87, driver.held)
        driver.perform({"op": "keyup", "key": "W"})
        self.assertFalse(driver.held)
        self.assertEqual(2, guard.call_count)
        self.assertTrue(any(call.args[0] > 0 for call in sleep.call_args_list))
        self.assertFalse(any(event[0] == "focus" for event in self.backend.events))

    def test_combo_text_mouse_and_resize(self):
        driver = Input(self.backend, 456, lambda: None, 0.05, lambda _: None, "attach")
        for op in ({"op": "combo", "keys": ["CTRL", "A"]}, {"op": "text", "text": "a中"},
                   {"op": "click", "x": 10, "y": 20}, {"op": "move", "x": 10, "y": 20},
                   {"op": "scroll", "x": 10, "y": 20, "notches": 1},
                   {"op": "resize", "width": 960, "height": 540}):
            driver.perform(op)
        self.assertFalse(driver.held)
        self.assertTrue(self.backend.events)

    def test_pacing_shared_across_clients(self):
        pacer = [float("-inf")]
        sleep = Mock()
        first = Input(self.backend, 456, lambda: None, 0.05, sleep, "attach", pacer)
        second = Input(self.backend, 456, lambda: None, 0.05, sleep, "attach", pacer)
        first.key_state(87, True)
        second.key_state(87, False)
        self.assertIs(first.pacer, second.pacer)
        self.assertTrue(sleep.called)

    def test_native_screenshot_only(self):
        driver = Input(self.backend, 456, lambda: None, 0.05, lambda _: None, "attach")
        directory = self.game / "screenshots"
        directory.mkdir()
        def f2(key, hold=0.08):
            self.assertEqual("F2", key)
            (directory / "native.png").write_bytes(b"native screenshot test")
        driver.key = f2
        output = self.root / "evidence.png"
        result = driver.screenshot(self.game, output)
        self.assertEqual("native Minecraft F2 screenshot", result["source"])
        self.assertEqual((directory / "native.png").read_bytes(), output.read_bytes())
        with self.assertRaises(ValueError):
            driver.screenshot(self.game, output)


if __name__ == "__main__":
    unittest.main()
