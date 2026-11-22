"""Portable K07 contract checks: no external world, Windows command or JVM."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.campaign_prep import k07_planets as k07


class K07Tests(unittest.TestCase):
    def test_pins_agree_with_chain(self):
        self.assertEqual(k07.common.CANDIDATE, k07.chain.C6_COMMIT)
        self.assertEqual(k07.common.JAR_HASH, k07.chain.C6_HASH)

    def test_owner_parameters(self):
        self.assertEqual(k07.owners(k07.OWNER, k07.OTHER), (k07.OWNER, k07.OTHER))
        for pair in ((k07.OWNER, k07.OWNER), ("not-a-uuid", k07.OTHER), (k07.FI05_OWNER, k07.OTHER)):
            with self.assertRaises(ValueError):
                k07.owners(*pair)

    def test_discovery_is_shared_so_fi04_is_required(self):
        for name in ("undiscovered", "pre-sky", "paid-pending"):
            self.assertEqual(k07.source_for(name, Path("inputs")), Path("inputs/K02/FI04/source-world"))
        self.assertIn("FI05", str(k07.source_for("discovered")))
        with self.assertRaises(ValueError):
            k07.source_for("unknown")

    def test_source_seal_detects_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "source-world").mkdir()
            (folder / "source-world/test").write_bytes(b"original")
            k07.chain.seal(folder)
            k07.chain.verify_seal(folder)
            (folder / "source-world/test").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "Sealed member changed"):
                k07.chain.verify_seal(folder)

    def test_source_seal_rejects_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "SHA256SUMS.txt").write_text("0" * 64 + "  ../elsewhere\n")
            with self.assertRaisesRegex(ValueError, "Unsafe checksum"):
                k07.chain.verify_seal(folder)

    def test_values_are_read_from_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "host.jar"
            entries = {"data/arce/celestial_bodies/mars.json": {"gravity": .38},
                       "data/arce/travel_routes/earth_mars.json": {"distance": 80},
                       "data/arce/satellite_definitions/data_satellite.json": {"duration": 200},
                       "assets/arce/celestial_visuals/mars.json": {"schema_version": 1}}
            with zipfile.ZipFile(jar, "w") as archive:
                for name, value in entries.items():
                    archive.writestr(name, json.dumps(value))
            read = k07.values(jar)
            for category in ("bodies", "routes", "satellites", "sky_resources"):
                self.assertEqual(len(read[category]), 1)
            self.assertEqual(next(iter(read["bodies"].values())), {"gravity": .38})

    def test_stable_authority_exact_equality(self):
        state = dict(research_accounts=[dict(balance=40)], bindings={"test": "mars"})
        self.assertTrue(all(k07.comparison(state, copy.deepcopy(state), "discovered").values()))
        after = copy.deepcopy(state)
        after["research_accounts"][0]["balance"] += 120
        with self.assertRaises(ValueError):
            k07.comparison(state, after, "discovered")

    def pending(self):
        return dict(research_accounts=[dict(balance=20, lifetime_earned=120, lifetime_spent=100)],
                    missions=[dict(mission_id=[1, 2, 3, 4], status="claim_pending_discovery")],
                    celestial=dict(bodies=[dict(id="arce:earth", discovered_at=1)]), bindings={"unchanged": True})

    def recovered(self):
        state = self.pending()
        state["missions"][0]["status"] = "claimed"
        state["celestial"]["bodies"].append(dict(id=k07.NS + ":mars", discovered_at=20))
        state["celestial"]["bodies"].sort(key=lambda b: b["id"])
        return state

    def test_pending_recovery_without_second_payment(self):
        before = self.pending()
        self.assertTrue(all(k07.comparison(before, self.recovered(), "paid-pending").values()))
        self.assertEqual(before, self.pending(), "Comparison must not mutate evidence")

    def test_pending_recovery_rejects_payment_or_identity_change(self):
        for field in ("balance", "lifetime_earned", "lifetime_spent"):
            after = self.recovered()
            after["research_accounts"][0][field] += 1
            with self.assertRaises(ValueError):
                k07.comparison(self.pending(), after, "paid-pending")
        after = self.recovered()
        after["missions"][0]["mission_id"] = [4, 3, 2, 1]
        with self.assertRaises(ValueError):
            k07.comparison(self.pending(), after, "paid-pending")

    def test_pending_requires_one_receipt_and_one_new_discovery(self):
        with self.assertRaises(ValueError):
            k07.comparison(self.recovered(), self.recovered(), "paid-pending")
        after = self.recovered()
        after["celestial"]["bodies"].append(dict(id=k07.NS + ":venus"))
        with self.assertRaises(ValueError):
            k07.comparison(self.pending(), after, "paid-pending")

    def test_hazard_plan_honest_about_player_coverage(self):
        plan = k07.hazard_plan()
        self.assertEqual(len(plan["sites"]), 4)
        self.assertTrue(plan["not_covered"])
        self.assertTrue(all(not s["observed_player_damage"] for s in plan["sites"]))
        self.assertEqual(plan["priority"], ["pressure", "heat", "cold", "solar"])
        self.assertEqual(plan["environmental_damage"], 2)

    def test_no_save_mutating_commands(self):
        commands = k07.site_commands()
        self.assertEqual(len(commands), 13)
        for command in commands:
            self.assertNotIn("data modify", command)
            self.assertNotIn("data merge", command)
            self.assertNotIn("{", command)
            self.assertNotIn("gamerule", command)
            self.assertNotIn("remove all", command)
        self.assertEqual(sum("minecraft:stone" in c for c in commands), 1)

    def test_platforms_stay_in_their_chunks(self):
        for site in k07.hazard_plan()["sites"]:
            x, _, z, xx, _, zz = site["platform"]
            self.assertEqual((x // 16, z // 16), (xx // 16, zz // 16))

    def test_host_permissions_parse_inherited_capture_without_running_it(self):
        result = k07.host_properties("difficulty=peaceful\nview-distance=2\n")
        self.assertEqual(result, "difficulty=normal\nview-distance=2\nfunction-permission-level=4\n")

    def test_existing_c6_environment_contract(self):
        base = k07.ROOT / k07.common.JAVA_ROOT
        exposure = (base / "celestial/environment/EnvironmentalExposure.java").read_text()
        for anchor in ("INTERVAL = 20", "DAMAGE = 2.0F", "temperatureKelvin() < 240", "temperatureKelvin() > 330",
                       "pressure() > 2", "solarIntensity() > 1.5"):
            self.assertIn(anchor, exposure)
        player = (base / "celestial/environment/PlayerEnvironmentalService.java").read_text()
        self.assertIn("WARNING_HEARTBEAT = 100", player)
        self.assertIn("!suffocationAttempt", player)

    def test_existing_hook_does_not_flush_claim_synchronously(self):
        source = (k07.ROOT / k07.common.JAVA_ROOT / "satellite/command/SatelliteRegistryWriterCommands.java").read_text()
        claim = source.split('Commands.literal("discovery-claim")', 1)[1].split("event.getDispatcher()", 1)[0]
        self.assertIn("SatelliteRuntime.claim", claim)
        self.assertNotIn(".flush(", claim)
        self.assertIn("00000000-0000-0000-0000-000000129b01", source)

    def test_private_station_uses_registered_route_and_returned_id(self):
        console = unittest.mock.Mock()
        station = "04380000-0000-4000-8000-000000000099"
        console.query.return_value = "Created K07 private Mars id=" + station
        console.all.return_value = ["native rights readback"]
        made = k07.populate(console, "private-station", k07.OWNER)
        self.assertEqual(made["station"], station)
        console.all.assert_called_once_with("arce station admin inspect " + station)

    def test_qualification_takes_no_population_action(self):
        self.assertEqual(k07.populate(None, "pre-sky", k07.OWNER), {})
        self.assertEqual(k07.populate(None, "undiscovered", k07.OWNER), {})

    def test_binding_refusal_does_not_claim_blocked_store(self):
        console = unittest.mock.Mock()
        console.all.side_effect = [["is valid with 12 bodies"], ["is valid with 11 bodies"],
                                  ["Planetary lifetime bindings exceed 128"], ["is valid with 11 bodies"]]
        made = k07.blocked_attempt(console)
        self.assertIn("not a corrupt/blocked SavedData", made["meaning"])
        self.assertEqual(console.query.call_count, 4)

    def test_bad_binding_refusal_not_accepted(self):
        console = unittest.mock.Mock()
        console.all.return_value = ["unknown command"]
        with patch.object(k07.time, "monotonic", side_effect=[0, 61]), self.assertRaises(ValueError):
            k07.blocked_attempt(console)

    def test_catalog_wait_has_bounded_async_reload(self):
        console = unittest.mock.Mock()
        console.all.side_effect = [["previous catalog"], ["is valid with 12 bodies"]]
        with patch.object(k07.time, "sleep") as sleep:
            self.assertEqual(k07.await_catalog(console, "12 bodies"), ["is valid with 12 bodies"])
        sleep.assert_called_once_with(.1)

    def test_only_exact_binding_refusal_error_is_expected(self):
        expected = "[main/ERROR] Rejected planetary catalog; last valid generation remains active: Planetary binding rejection: Planetary lifetime bindings exceed 128"
        self.assertEqual(k07.audit_errors("blocked-binding", [expected])["all_errors"], [expected])
        for name, lines in (("discovered", [expected]), ("blocked-binding", []),
                            ("blocked-binding", [expected, expected]),
                            ("blocked-binding", [expected, "[main/ERROR] unrelated failure"])):
            with self.assertRaises(ValueError):
                k07.audit_errors(name, lines)

    def test_private_station_save_checks_rights(self):
        identity = [1, 2, 3, 4]
        station = next(iter(k07.chain.saved_uuids(identity)))
        actor = next(iter(k07.chain.saved_uuids([4, 3, 2, 1])))
        state = dict(stations=[dict(station_id=identity, owner_id=[4, 3, 2, 1],
                     orbit_body=k07.NS + ":mars", members=[], invitations=[])])
        made = dict(station=station, owner=actor)
        k07.check_creation("private-station", made, {}, state)
        state["stations"][0]["members"].append(dict(id=[1, 1, 1, 1]))
        with self.assertRaises(ValueError):
            k07.check_creation("private-station", made, {}, state)


if __name__ == "__main__":
    unittest.main()
