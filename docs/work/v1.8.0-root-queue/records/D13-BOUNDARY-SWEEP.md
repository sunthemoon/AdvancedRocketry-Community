# D13: log sweep for the atmosphere-boundary callback budget

Root: Claude. Date: 2026-10-31. Root decision R226.

## Question

W172's inventory (Q34, D13) asks whether the startup rejection of `advancedrocketrycommunity:force_field_boundary`
recurred under load. The rejection comes from the 5 ms wall-clock budget per atmosphere-boundary callback
(`AtmosphereBoundaryRegistry.MAX_CALLBACK_NANOS = 5_000_000L`, line 25; the rejection at lines 107 to 109). The
known occurrences are two local runs:

- W01's crash on 2026-10-12 at 00:18;
- W11b1's first whole native selection on 2026-10-13: [W11B1](W11B1.md) line 315, K7's first process.

W223's brief recommends closing the watch item if a complete sweep finds no recurrence, without changing the 5 ms
value or creating a waiver.

## Method and scope

The pattern is the rejection message, "Atmosphere boundary compilation rejected provider", or the provider ID.

| Sweep | Scope | Result |
|---|---|---|
| Hosted | Every completed run of `v180-development.yml` created from 2026-10-12 to 2026-10-31 10:49 UTC, on all branches: 388 runs and 386 job logs, of which 385 were read (job 124491823188 returned HTTP 404) | No match |
| Local | Every `*.log` and `*.txt` file under `D:/GitHub/ARCE-Task-Evidence` modified since 2026-10-12: 30,562 files (archives not opened) | One match: the known W11b1 failure, `root-queue/W11b1/continuation-01-20261013/native-delivery-final/K7-restart-0.log` line 17, written 2026-10-13 09:03 |

The local match is the positive control: the search finds the known occurrence. W01's crash log of 2026-10-12 is no
longer in the evidence tree, so it is not counted.

Tools and results, outside the repository, with SHA-256:

- the hosted sweep `root-queue/tools/log_sweep.py` (`ee42ff8b…f0b3aa`); it reads each job log in memory and saves
  none of them;
- `root-queue/D13/sweep-v180-development.json` (`3d08c028…ebfd73f6`);
- `root-queue/D13/sweep-console.txt` (`60469efe…bd9fa0a`);
- the local sweep `root-queue/D13/local_sweep.py` (`ec33b753…eeb6b1f`);
- `root-queue/D13/sweep-local.json` (`f5b00a01…a695aef7286`).

## Result

The rejection has not recurred since 2026-10-13 09:03 in any retained hosted or local log. Since then, both kinds of
run have started the server under load many times: GameTest batches, native campaigns and the four-shard CI runs.

## Decision

- The D13 watch item closes on this evidence.
- The 5 ms value is unchanged. No waiver or budget ADR is proposed.
- If the rejection appears again, D13 reopens. The next step would then be W172's suggestion: an ADR that measures
  the callback's thread CPU time with the 5 ms value unchanged.

## Limits

- Logs that runs deleted, or that were never kept, are not covered.
- Real-client and reference-machine runs in the final campaign are still to come.

This approves no Gate.
