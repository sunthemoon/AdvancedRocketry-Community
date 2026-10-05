# Native fixture corrections

Date: 2026-10-06. Status: implemented-unverified. No release Gate acceptance.

Scope is two existing GameTest files only; production behavior, resources,
registrations, assertion deadlines and test selection are unchanged.

## Observed failures and corrections

- At `7ae88260eaec44cb8692d82cd8d4545715efbddb`, hosted run 37338384386
  completes 482 GameTests with three required failures: cold Tau Ceti flight,
  lamp boundary registration and lamp loot identity. The lamp boundary fixture
  supplies a provider ID in `advancedrocketrycommunity` but an owner handle in
  `station_light_test`. The production registry requires those namespaces to
  match. The corrected local fixture uses `ModIdentity.MOD_ID`; it still tests
  explicit boundary priority and unloaded-chunk refusal without global mutation.
- Native `ApplyExplosionDecay.run` can set a stack's count to zero. Native
  `LootTable.createStackSplitter` forwards a below-max-size stack without first
  filtering empties; `ItemStack.getItem` exposes AIR for an empty stack. The loot
  oracle now explicitly accepts only an untagged zero-count empty result and
  still requires every nonempty result to be the lamp. Ordinary/unit-radius
  count one, radius-four count zero-or-one, seeded replay, both finite outcomes
  and maximum one entry remain required. No production loot change is made.
- At `37ef84c26d33d5f369478bd5060dc97b9dc7fbdd`, hosted run 37338649321
  fails `compileJava`: Java cannot infer `Comparator.naturalOrder()` for
  `TicketType.create` at line 44. An explicit `<UUID>` type witness corrects
  only inference, preserving the comparator, ticket identity and 300-tick expiry.
  This failed cohort supplies no JUnit, DataGen or GameTest execution result.

## Actual verification and limits

Root `git diff --check` passes. Root reads the exact production registry and
uses Java 17 `javap -c -p` to inspect the three native classes above, exit zero
for each. This is bytecode inspection, not compilation or Minecraft execution.
The local mapped Forge 1.20.1-47.4.10 JAR is read-only, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
The initial unqualified `javap` command is unavailable and fails; the fresh
explicit JDK executable succeeds. All new inspection output is under
[the project-parent D evidence directory](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-native-fixture-corrections-20261006-01/).
No class, JAR, world or ZIP copy is retained.

The [different-agent actual-diff review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-fixture-correction-review-20261006-cc7624/REVIEW-01.md)
finds one Low: native `hasTag()` hides tags on empty stacks, so the first new
branch does not enforce its stated untagged condition. The original review and
pins remain unchanged. A separate one-line correction checks `getTag() == null`,
whose native getter directly exposes the tag field; the original failing control
is not called a pass. This corrected line still requires independent recheck.
Committed unfiltered hosted replay remains required. Local C is below 10 GB;
no local build, GameTest or native server starts.
The original failed runs remain unchanged. Tau Ceti and gravity investigations,
full v1.8 implementation, restart/client/visual tests and G0-G9 remain open.
