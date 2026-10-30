"""S6 raw loader evidence. Unreadable stores never become zero-valued resources.

An E3 cut can expose either exact prefix at each independent native store. The
record still owns the entire component: a raw sum is not spendable accounting.
"""
from __future__ import annotations

from pathlib import Path
import loader_cut_oracle as legacy
from run_dedicated_server_smoke import SmokeError

E3 = "LF_S6_E3_CACHE_CLOSE"


def inspect(world: Path) -> dict:
    try:
        return dict(read_status="KNOWN", native=legacy.inspect(world))
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None,
                    error=f"{type(failure).__name__}: {failure}")


def check_e3(image, restart, identity=None):
    legacy.require(restart in (0, 1, 2), "Invalid restart index")
    legacy.require(image.get("read_status") == "KNOWN" and isinstance(image.get("native"), dict),
                   "UNKNOWN native read cannot establish availability or zero loss")
    disk = image["native"]
    try:
        legacy.require(len(disk["loaders"]) == 1, "E3 requires exactly one real fuel loader")
        tank = disk["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Amount"]
        bank = disk["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["amount"]
        prefixes = {(5000, 0): "K2", (4000, 0): "K4", (5000, 1000): "K5", (4000, 1000): "K6"}
        legacy.require((tank, bank) in prefixes, "Changed raw quantity; not an exact native BEFORE/AFTER image")
        prefix = prefixes[tank, bank]
        legacy.require(disk["ledger"] is not None, "Missing durable L1 ledger")
        observed = legacy.check(disk, "LF_M_" + ("K7" if restart else prefix), restart)
        # Legacy identity omits the ledger because its runner compares it separately.
        observed["ledger"] = disk["ledger"]["ledger_id"]
        legacy.require(identity is None or observed == identity, "Changed native identity across restarts")
        return dict(prefix=prefix, raw_total=tank + bank,
                    available_total=5000 if restart else None,
                    loss=0 if restart else None, identity=observed)
    except (KeyError, TypeError, IndexError) as failure:
        raise SmokeError(f"Malformed/missing raw loader state: {failure}") from failure


def check_observations(lines: list[str], restart: int) -> None:
    """Require the selected propagated failure, not merely a checkpoint label."""
    import re
    if restart:
        check_recovery_observations(lines)
        legacy.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines),
                       "Recovery logged ERROR/FATAL")
        return
    wanted = r"ARCE_LOADER_S6_E3 records=1 pending=1 issued=1 io_failures=1 reason=BARRIER_IO proof=false"
    legacy.require(sum(re.search(wanted + r"(?:\s|$)", line) is not None for line in lines) == 1,
                   "Missing exact propagated E3 failure/retained authority")
    legacy.require(sum("ARCE_LOADER_S6_CACHE regions=256 own_region_cached=false" in line for line in lines) == 1,
                   "Missing selected cache-close observation")
    errors = [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]
    legacy.require(len(errors) == 1 and "Failed to store chunk [4, 4]" in errors[0],
                   "E3 native diagnostics differ from its one own-store failure: " + repr(errors))
    legacy.require(any("java.nio.channels.ClosedChannelException" in line for line in lines),
                   "E3 did not propagate the selected closed channel")


def check_recovery_observations(lines: list[str]) -> None:
    """Check every tick's partition independently of the JVM observer's verdict."""
    def fields(line, marker):
        return dict(token.split("=", 1) for token in line.split(marker, 1)[1].strip().split())
    rows = [fields(line, "ARCE_LOADER_S6_ACCOUNT ") for line in lines if "ARCE_LOADER_S6_ACCOUNT " in line]
    results = [fields(line, "ARCE_LOADER_S6_RESULT ") for line in lines if "ARCE_LOADER_S6_RESULT " in line]
    legacy.require(rows and len(results) == 1, "Missing/repeated S6 accounting or completion")
    ticks, known = [], []
    try:
        for row in rows:
            ticks.append(int(row["tick"]))
            if row["known"] == "false":
                legacy.require(all(row[k] == "UNKNOWN" for k in ("raw", "available", "held")),
                               "UNKNOWN observation converted to numeric resources")
                continue
            legacy.require(row["known"] == "true", "Invalid observation read status")
            known.append(row)
            numbers = {key: int(row[key]) for key in ("records", "source", "destination", "source_available",
                       "source_held", "destination_available", "destination_held", "in_transit")}
            s, d = numbers["source"], numbers["destination"]
            legacy.require(numbers["records"] in (0, 1), "Wrong recovery record count")
            if numbers["records"]:
                legacy.require(row["q"] == "1000" and row["spendable"] == "UNKNOWN",
                               "Changed record or tentative credit counted as spendable")
                legacy.require(s in (4000, 5000) and d in (0, 1000), "Resource image exceeds record authority")
                expected = (4000, 1000 if s == 5000 else 0, 0, 1000 if d == 1000 else 0,
                            1000 if (s, d) == (4000, 0) else 0)
            else:
                legacy.require(row["q"] == "null" and row["spendable"] == "5000" and (s, d) == (4000, 1000),
                               "Changed completed resource total or held amount without record")
                expected = (4000, 0, 1000, 0, 0)
            actual = tuple(numbers[k] for k in ("source_available", "source_held", "destination_available",
                                               "destination_held", "in_transit"))
            legacy.require(actual == expected and s == actual[0] + actual[1] and d == actual[2] + actual[3],
                           "Raw native does not equal available plus record-derived held")
        legacy.require(ticks == list(range(ticks[0], ticks[-1] + 1)), "Missing or repeated recovery tick")
        result = results[0]
        legacy.require(known and rows[-1] == known[-1] and known[-1]["records"] == "0"
                       and result == dict(cut=E3, observations=str(len(known)), total="5000", records="0",
                                          markers="0", source_epoch="1", destination_epoch="1"),
                       "Completion lacks exact final resources, epochs or observation count")
    except (KeyError, ValueError, TypeError) as failure:
        raise SmokeError(f"Malformed S6 accounting observation: {failure}") from failure
