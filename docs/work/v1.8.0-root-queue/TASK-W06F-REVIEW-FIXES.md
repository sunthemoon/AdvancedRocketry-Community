# TASK W06F: fix the W06 independent review findings

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w06-motion` (at the W06 candidate `fcc03f37`; hosted run 38199855784 passed) |
| Branch | `codex/v1.8.0-w06-motion` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W06-review/REVIEW.md`, SHA-256 `9bdd12238f18c32df093a42df5bb9c610aeafe78852b1a53b34928409d0c71e0`. Verdict BLOCK: H1, M1, M2, L1. |
| Record | `docs/work/v1.8.0-root-queue/records/W06F.md` |

Read the whole review first.

## Root's decisions

Fix each finding in its own commit, together with its tests.

1. **H1 (unpaid motion is not enforced).** The mod does not add a general movement
   anti-cheat. Instead, enforce paid flight through vanilla's own floating check, which is
   active on the default `allow-flight=false`:
   - Add a Forge Access Transformer entry (`src/main/resources/META-INF/accesstransformer.cfg`;
     see whether the build already declares one) that makes
     `ServerGamePacketListenerImpl.aboveGroundTickCount` accessible. Root authorizes this
     one AT entry and the build line it needs, if the line is missing. An AT changes
     visibility only; it is not a coremod.
   - On each server tick in which the jetpack service actually **spends hydrogen** for a
     thrust or hover correction for that player, reset that player's
     `aboveGroundTickCount` (and the vehicle counter if it applies) to 0.
   - Never reset it otherwise. A client that floats without paying is then kicked by
     vanilla exactly as today.
   - With `allow-flight=true`, vanilla trusts client flight whatever this mod does. Record
     this as a limitation in the ADR-066 §5.2 implementation note, as one sentence, and in
     your record.
   - Test with a GameTest: a paid hover keeps the counter at 0 across ticks; an unpaid float
     (no hydrogen, or an expired intent) lets the counter grow.
2. **M1 (equipment replaced with the same module kinds).**
   - Track an equipment revision or the worn-stack identity for HEAD and CHEST. A replaced
     stack resets the HOVER reference and the session state.
   - An ordinary hydrogen debit on the same stack does not count as a replacement.
   - Test: swap a HEAD and a CHEST for the same kinds; the reference resets and the gas
     accounting is exact.
3. **M2 (visors ignore the server switch).**
   - Send the client a bounded, server-authorized equipment-enabled flag. Reuse an existing
     S2C sync if one fits; otherwise add the smallest addition to W06's channel, bounded and
     versioned with it.
   - Gate both visor effects on that flag. Clear them when it turns off while equipped,
     and when a player joins a disabled server.
   - The CLIENT `atmosphericNausea` opt-out stays independent.
   - Test the server side: the flag is sent at login and on change.
4. **L1 (missing coverage).** Add the review's refusal case that fails **after** native
   deserialization (the 256-node envelope without Damage). Compare the whole raw input
   with the saved refusal. Keep the two existing W03 cases.

## Tests and commands

- The tests above.
- The whole unit suite once. Each new class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- A general movement anti-cheat.
- The laser gun.
- Charging stations.
- Status files and the ledger.
