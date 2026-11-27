"""Pointer decisions and the Win32 read-only fallback; no native DLLs or game needed."""
import ctypes as C
import unittest
from itertools import product
from unittest.mock import Mock, patch

from scripts.campaign_client.gates import cursor_captured
from scripts.campaign_client.input import Input
from scripts.campaign_client.session import Session
from scripts.campaign_client.windows import Windows


DESKTOP = (0, 0, 1920, 1080)
CLIENT = (100, 120, 1380, 840)
FLAGS = (None, 0, 1, 2, 3, 4, 0xFFFFFFFF, -1, False, True, "0", 0.0)


class PointerDecisionTests(unittest.TestCase):
    def test_fullscreen_hidden_is_captured_and_shown_is_released(self):
        self.assertIs(True, cursor_captured(DESKTOP, DESKTOP, DESKTOP, 0))
        self.assertIs(False, cursor_captured(DESKTOP, DESKTOP, DESKTOP, 1))

    def test_complete_geometry_and_visibility_decision_table(self):
        # (clip, client, desktop, old answer). None means previously undecidable.
        cases = (
            (CLIENT, CLIENT, DESKTOP, True),
            ((110, 130, 1300, 800), CLIENT, DESKTOP, True),
            ((0, 0, 1380, 840), CLIENT, DESKTOP, False),
            (DESKTOP, CLIENT, DESKTOP, False),
            (DESKTOP, DESKTOP, DESKTOP, None),
            (DESKTOP, (-1, -1, 1921, 1081), DESKTOP, None),
            (DESKTOP, DESKTOP, (-1920, 0, 1920, 1080), True),
            ((-1920, 0, 1920, 1080), DESKTOP, (-1920, 0, 1920, 1080), False),
        )
        for (clip, client, desktop, old_answer), flags in product(cases, FLAGS):
            with self.subTest(clip=clip, client=client, desktop=desktop, flags=flags):
                if old_answer is not None:
                    self.assertIs(old_answer, cursor_captured(clip, client, desktop, flags))
                elif type(flags) is int and (flags == 1 or (flags == 0 and clip == client)):
                    self.assertIs(flags == 0, cursor_captured(clip, client, desktop, flags))
                else:
                    with self.assertRaises(ValueError):
                        cursor_captured(clip, client, desktop, flags)

    def test_omitting_the_signal_still_refuses_fullscreen(self):
        with self.assertRaisesRegex(ValueError, "undecidable"):
            cursor_captured(DESKTOP, DESKTOP, DESKTOP)

    def test_hidden_cannot_claim_a_client_clip_larger_than_the_desktop_clip(self):
        with self.assertRaisesRegex(ValueError, "contradict"):
            cursor_captured(DESKTOP, (-1, 0, 1920, 1080), DESKTOP, 0)

    def test_invalid_rectangles_are_not_rescued_by_visibility(self):
        invalid = ((0, 0, 0, 1080), (0, 0, 1920, 0), (1, 0, 0, 1080),
                   (0, 0, 1920), (0, 0, 1920, 1080, 1), (False, 0, 1920, 1080),
                   (0.0, 0, 1920, 1080))
        for index, rect, flags in product(range(3), invalid, FLAGS):
            with self.subTest(index=index, rect=rect, flags=flags):
                rectangles = [DESKTOP, DESKTOP, DESKTOP]
                rectangles[index] = rect
                with self.assertRaises(ValueError):
                    cursor_captured(*rectangles, flags)


class PointerAdapterTests(unittest.TestCase):
    def adapter(self, clip=DESKTOP, client=DESKTOP, desktop=DESKTOP, flags=0):
        adapter = Windows.__new__(Windows)
        adapter.k, adapter.u = Mock(), Mock()

        def require(result):
            if not result:
                raise OSError("Win32 read failed")
            return result

        adapter.require = require

        def get_clip(pointer):
            pointer._obj.left, pointer._obj.top, pointer._obj.right, pointer._obj.bottom = clip
            return 1

        def client_origin(handle, pointer):
            pointer._obj.x, pointer._obj.y = client[:2]
            return 1

        def get_cursor(pointer):
            self.assertEqual(C.sizeof(pointer._obj), pointer._obj.cbSize)
            pointer._obj.flags = flags
            return 1

        adapter.u.GetClipCursor.side_effect = get_clip
        adapter.u.ClientToScreen.side_effect = client_origin
        adapter.size = Mock(return_value=(client[2] - client[0], client[3] - client[1]))
        metrics = dict(zip((76, 77, 78, 79),
                           (desktop[0], desktop[1], desktop[2] - desktop[0], desktop[3] - desktop[1])))
        adapter.u.GetSystemMetrics.side_effect = metrics.__getitem__
        adapter.u.GetCursorInfo.side_effect = get_cursor
        adapter.u.GetForegroundWindow.return_value = 456
        return adapter

    def test_fullscreen_reads_initialized_cursor_info_without_input(self):
        for flags in (0, 1):
            with self.subTest(flags=flags):
                adapter = self.adapter(flags=flags)
                self.assertIs(flags == 0, adapter.cursor_captured(456))
                adapter.u.GetCursorInfo.assert_called_once()
                self.assertEqual(2, adapter.u.GetClipCursor.call_count)
                self.assertEqual(2, adapter.u.GetForegroundWindow.call_count)
                self.assertTrue(all(call[0] in {"GetClipCursor", "ClientToScreen", "GetSystemMetrics",
                                               "GetCursorInfo", "GetForegroundWindow"}
                                    for call in adapter.u.mock_calls), adapter.u.mock_calls)
                adapter.k.assert_not_called()
                self.assertEqual([], adapter.k.mock_calls)

    def test_decidable_clips_do_not_read_cursor_visibility_or_foreground(self):
        for clip, client, desktop, answer in (
                (CLIENT, CLIENT, DESKTOP, True), (DESKTOP, CLIENT, DESKTOP, False),
                ((0, 0, 1380, 840), CLIENT, DESKTOP, False),
                (DESKTOP, DESKTOP, (-1920, 0, 1920, 1080), True)):
            with self.subTest(clip=clip, client=client, desktop=desktop):
                adapter = self.adapter(clip, client, desktop)
                adapter.u.GetCursorInfo.side_effect = AssertionError("Unneeded visibility read")
                self.assertIs(answer, adapter.cursor_captured(456))
                adapter.u.GetCursorInfo.assert_not_called()
                adapter.u.GetForegroundWindow.assert_not_called()
                self.assertEqual(1, adapter.u.GetClipCursor.call_count)

    def test_failed_cursor_info_read_refuses(self):
        adapter = self.adapter()
        adapter.u.GetCursorInfo.side_effect = None
        adapter.u.GetCursorInfo.return_value = 0
        with self.assertRaises(OSError):
            adapter.cursor_captured(456)

    def test_missing_cursor_info_api_refuses(self):
        adapter = self.adapter()
        del adapter.u.GetCursorInfo
        with self.assertRaises(AttributeError):
            adapter.cursor_captured(456)

    def test_suppressed_combined_and_unknown_flags_refuse(self):
        for flags in (2, 3, 4, 0xFFFFFFFF):
            with self.subTest(flags=flags):
                with self.assertRaises(ValueError):
                    self.adapter(flags=flags).cursor_captured(456)

    def test_foreground_missing_or_changed_refuses(self):
        for fronts in ((999,), (0,), (456, 999), (456, 0)):
            with self.subTest(fronts=fronts):
                adapter = self.adapter()
                adapter.u.GetForegroundWindow.side_effect = fronts
                with self.assertRaisesRegex(ValueError, "front"):
                    adapter.cursor_captured(456)
                if len(fronts) == 1:
                    adapter.u.GetCursorInfo.assert_not_called()

    def test_clip_changed_during_cursor_info_read_refuses(self):
        adapter = self.adapter()
        original = adapter.u.GetClipCursor.side_effect

        def get_cursor(pointer):
            pointer._obj.flags = 1
            def changed_clip(pointer):
                original(pointer)
                pointer._obj.right -= 1
                return 1
            adapter.u.GetClipCursor.side_effect = changed_clip
            return 1

        adapter.u.GetCursorInfo.side_effect = get_cursor
        with self.assertRaisesRegex(ValueError, "clip.*changed"):
            adapter.cursor_captured(456)

    def test_clip_reread_failure_refuses(self):
        adapter = self.adapter()

        def get_cursor(pointer):
            pointer._obj.flags = 1
            adapter.u.GetClipCursor.side_effect = lambda pointer: 0
            return 1

        adapter.u.GetCursorInfo.side_effect = get_cursor
        with self.assertRaises(OSError):
            adapter.cursor_captured(456)

    def test_hidden_signal_contradicting_client_clip_refuses(self):
        adapter = self.adapter(client=(-1, 0, 1920, 1080), flags=0)
        with self.assertRaisesRegex(ValueError, "contradict"):
            adapter.cursor_captured(456)

    def test_bad_rectangle_does_not_trigger_fallback(self):
        adapter = self.adapter(clip=(0, 0, 0, 0))
        with self.assertRaisesRegex(ValueError, "Invalid rectangle"):
            adapter.cursor_captured(456)
        adapter.u.GetCursorInfo.assert_not_called()

    def session(self, adapter):
        session = Session.__new__(Session)
        session.backend, session.server = adapter, None
        session.sleep, session.check_time = Mock(), Mock()
        input_ = Input(adapter, 456, Mock(), 0.05, session.sleep, "attach")
        session.clients = {"Tester": {"input": input_, "process": None}}
        return session, input_

    def test_require_screen_uses_both_fullscreen_states_without_input(self):
        for flags in (0, 1):
            with self.subTest(flags=flags):
                adapter = self.adapter(flags=flags)
                session, input_ = self.session(adapter)
                self.assertEqual({"screen_open": flags == 1}, session.perform(
                    {"op": "require_screen", "open": flags == 1}))
                adapter.u.PostMessageW.assert_not_called()
                self.assertEqual(set(), input_.held)

    def test_close_screen_sends_nothing_when_fullscreen_pointer_is_hidden(self):
        adapter = self.adapter(flags=0)
        session, input_ = self.session(adapter)
        self.assertEqual({"screen_was_open": False, "closed": False}, session.perform({"op": "close_screen"}))
        adapter.u.PostMessageW.assert_not_called()
        self.assertEqual(set(), input_.held)

    def test_close_screen_sends_one_esc_when_fullscreen_pointer_is_shown(self):
        adapter = self.adapter(flags=1)
        session, input_ = self.session(adapter)

        def key(key):
            self.assertEqual("ESC", key)
            adapter.u.GetCursorInfo.side_effect = lambda pointer: setattr(pointer._obj, "flags", 0) or 1

        with patch.object(input_, "key", side_effect=key) as send_key:
            self.assertEqual({"screen_was_open": True, "closed": True}, session.perform({"op": "close_screen"}))
        send_key.assert_called_once_with("ESC")

    def test_unknown_fullscreen_visibility_stops_screen_gates_before_input(self):
        for op in ({"op": "require_screen", "open": True}, {"op": "require_screen", "open": False},
                   {"op": "close_screen"}):
            with self.subTest(op=op):
                adapter = self.adapter(flags=2)
                session, input_ = self.session(adapter)
                with patch.object(input_, "key") as key:
                    with self.assertRaisesRegex(ValueError, "undecidable"):
                        session.perform(op)
                    key.assert_not_called()
                adapter.u.PostMessageW.assert_not_called()

    def test_status_reports_fullscreen_visibility_and_null_for_unreadable_state(self):
        for flags, expected in ((0, True), (1, False), (2, None), (3, None), (4, None)):
            with self.subTest(flags=flags):
                adapter = self.adapter(flags=flags)
                session, _ = self.session(adapter)
                self.assertIs(expected, session.perform({"op": "status"})["pointer_captured"])
                adapter.u.PostMessageW.assert_not_called()

    def test_status_does_not_read_visibility_for_background_window(self):
        adapter = self.adapter()
        adapter.u.GetForegroundWindow.return_value = 999
        session, _ = self.session(adapter)
        self.assertIsNone(session.perform({"op": "status"})["pointer_captured"])
        adapter.u.GetCursorInfo.assert_not_called()


class CursorBindingTests(unittest.TestCase):
    def test_cursor_info_binding_has_structure_pointer_and_bool_result(self):
        from ctypes import wintypes as W
        from scripts.campaign_client.windows import CURSORINFO

        user = Mock()
        with patch.object(C, "WinDLL", side_effect=[Mock(), user], create=True), \
                patch.object(C, "WINFUNCTYPE", C.CFUNCTYPE, create=True):
            Windows()
        self.assertEqual([C.POINTER(CURSORINFO)], user.GetCursorInfo.argtypes)
        self.assertIs(W.BOOL, user.GetCursorInfo.restype)


if __name__ == "__main__":
    unittest.main()
