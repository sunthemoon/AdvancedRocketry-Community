"""W208 evidence checks; an observation ceiling is never a product failure or kill permission."""
from sealed_cut_oracle import elect, require_inventory

CASES = ("small-quiet", "near-quiet", "near-traffic", "near-c1-unknown", "near-c3-unknown",
         "near-c1-expiry", "near-c3-expiry", "near-interrupted")
BOUND = 64 * 1024 * 1024
EFFECTS = ("C2_STAGE_BEFORE", "C4_DISCARD_BEFORE", "UNSEAL_PUBLISH_BEFORE")


def observation_status(completed, ceiling):
    return "COMPLETED" if completed else "NOT_COMPLETED" if ceiling else "OBSERVING"


def may_reopen(exit_code):
    return exit_code is not None


def near_qualified(root_bytes, ballast):
    return ballast == 255 and .99 * BOUND <= root_bytes <= BOUND


def check_events(events):
    for event in events:
        if event["label"] in EFFECTS and event.get("authorized") is not True:
            raise ValueError("Effect without checked acknowledgement: " + event["label"])


def conservation(phase, entities, sealed, native, retained):
    observed = elect(phase, entities, sealed, native, [], retained=retained)
    require_inventory(observed, {"w208-original": 7})
    return observed
