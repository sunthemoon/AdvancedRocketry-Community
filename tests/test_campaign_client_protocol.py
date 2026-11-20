import copy
import json
import unittest

from scripts.campaign_client.protocol import load_session, read_request, validate_request, validate_server
from test_campaign_client_support import Fixture


class ProtocolTests(Fixture):
    def request(self, ops, mode="launch"):
        return validate_request({"id": "test", "ops": ops}, mode, ["TesterOne"])

    def test_legacy_protocol_and_all_actions(self):
        ops = [{"op": "key", "key": "F3"}, {"op": "keydown", "key": "W"}, {"op": "keyup", "key": 87},
               {"op": "combo", "keys": ["CTRL", "A"]}, {"op": "text", "text": "hello"},
               {"op": "click", "x": 1, "y": 2}, {"op": "move", "x": 1, "y": 2},
               {"op": "scroll", "x": 1, "y": 2, "notches": -1}, {"op": "resize", "width": 640, "height": 480},
               {"op": "screenshot", "name": "scene"}, {"op": "sleep", "seconds": 0},
               {"op": "server", "command": "list", "wait": "players"},
               {"op": "client_wait", "pattern": "joined"}, {"op": "status"}, {"op": "close_client"}, {"op": "stop"}]
        self.assertEqual(ops, self.request(ops)["ops"])

    def test_action_count_bounds(self):
        self.request([{"op": "status"}] * 200)
        for ops in ([], [{"op": "status"}] * 201):
            with self.assertRaises(ValueError):
                self.request(ops)

    def test_invalid_action_fields_bounds_and_types(self):
        invalid = [{"op": "key", "key": "wat"}, {"op": "key", "key": True},
                   {"op": "key", "key": "W", "hold": float("nan")},
                   {"op": "text", "text": "x" * 257}, {"op": "text", "text": "a\nb"},
                   {"op": "sleep", "seconds": 121}, {"op": "status", "tail": -1},
                   {"op": "server", "command": "stop\rlist"}, {"op": "status", "player": "Other"},
                   {"op": "resize", "width": 1, "height": 720}, {"op": "click", "x": -1, "y": 2},
                   {"op": "screenshot", "name": "../bad"}, {"op": "status", "accessToken": "x"},
                   {"op": "unknown"}, {"op": "combo", "keys": []}]
        for op in invalid:
            with self.subTest(op=op), self.assertRaises(ValueError):
                self.request([op])

    def test_attach_close_refuses_entire_batch(self):
        with self.assertRaisesRegex(ValueError, "cannot close"):
            self.request([{"op": "key", "key": "T"}, {"op": "close_client"}], "attach")

    def test_oversized_request(self):
        path = self.root / "request.json"
        path.write_bytes(b" " * (256 * 1024 + 1))
        with self.assertRaises(ValueError):
            read_request(path, "attach", ["TesterOne"])

    def test_valid_session_and_bounds(self):
        self.assertEqual("attach", load_session(self.dump_session())["mode"])
        for field, value in (("time_limit_seconds", 21601), ("time_limit_seconds", 0),
                             ("input_interval_seconds", 0.049), ("singleplayer", True)):
            spec = copy.deepcopy(self.session_spec)
            self.session_spec[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                load_session(self.dump_session())
            self.session_spec = spec
        self.session_spec["time_limit_seconds"] = 21600
        load_session(self.dump_session())

    def test_at_most_two_clients_and_no_duplicate_pid(self):
        for count in (0, 2, 3):
            self.session_spec["clients"] = [dict(self.attach_spec, game_dir=str(self.game))] * count
            with self.assertRaises(ValueError):
                load_session(self.dump_session())

    def test_launch_is_offline_loopback_only(self):
        self.session_spec["mode"] = "launch"
        self.session_spec["clients"] = [{k: self.attach_spec[k] for k in ("player_name", "language", "gui_scale", "window_size")}]
        with self.assertRaises(ValueError):
            load_session(self.dump_session())
        self.settings["online-mode"] = False
        load_session(self.dump_session())
        self.settings["server-ip"] = "0.0.0.0"
        with self.assertRaises(ValueError):
            load_session(self.dump_session())

    def test_server_uuid_and_jvm_property_validation(self):
        validate_server(self.settings)
        for value in ("not-uuid", "12345678123442348234123456789abc", "12345678-1234-4234-8234-123456789abg"):
            self.settings["whitelist"][0]["uuid"] = value
            with self.assertRaises(ValueError):
                validate_server(self.settings)
        self.settings["whitelist"] = []
        for props in ({"bad=name": "x"}, {"ok": "a\nb"}, {"ok": "a\rb"}, {"ok": "a\0b"}, {"ok": 3}):
            self.settings["jvm_properties"] = props
            with self.assertRaises(ValueError):
                validate_server(self.settings)


if __name__ == "__main__":
    unittest.main()
