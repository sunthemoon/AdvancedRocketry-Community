"""One native longest-reason representative, not a replacement for capacity tests."""
import loader_s6_oracle as s6
from loader_cut_oracle import require
from loader_s6_flight import projection
from run_dedicated_server_smoke import SmokeError

CASE = "LF_S6_STICKY_RESERVATION"

def check(image, restart, identity=None):
    require(restart in (0, 1, 2), "Invalid sticky restart")
    result = s6.check_e3(image, 0, generations=(3, 3, 3))
    require(result["prefix"] == "K6", "Sticky must retain the proved real fill")
    disk = s6.normalize_ids(image["native"])
    try:
        root = disk["ledger"]; operation = root["records"][0]
        require(operation["reason"] == "JOURNAL_GUARD_MISMATCH" and operation["phase"] == "REPAIR"
                and "settlement" not in operation, "Sticky changed its undecided longest repair reason")
        observed = dict(ledger=root, loaders=disk["loaders"], rocket=projection(disk["rocket"]))
        require(not restart or identity is not None, "Sticky restart lacks its complete initial identity")
        require(identity is None or observed == s6.normalize_ids(identity), "Sticky changed its complete persisted identity")
        return dict(identity=observed, raw_total=5000, available_total=None, loss=None)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed sticky hold: {failure}") from failure

def check_logs(lines, restart):
    require(restart in (0, 1, 2), "Invalid sticky log restart")
    actual = [line[line.index("ARCE_LOADER_S6_STICKY_"):].strip() for line in lines if "ARCE_LOADER_S6_STICKY_" in line]
    require(actual == ["ARCE_LOADER_S6_STICKY_STATE cut=" + CASE + " generation=3 records=1 pending=1 reason=JOURNAL_GUARD_MISMATCH tank=4000 bank=1000 epochs=1,1 markers=1,1",
                       "ARCE_LOADER_S6_STICKY_" + ("RESULT" if restart else "PAUSED") + " cut=" + CASE],
            "Missing, duplicate or changed checked sticky observations")
