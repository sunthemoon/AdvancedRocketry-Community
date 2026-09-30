# C7 closure: second independent review and ADR-049/ADR-050 revision 4

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`2e0dbb7` (the answer to the first review, recorded in
[the round-1 dispositions](../v1.6.0-c7-review/VERIFICATION.md)). No Gate,
candidate or tag.

## Round 2

The same independent, read-only reviewer read the full diff `d15dfcc..2e0dbb7`,
the registry fixture, the seven new GameTests, the new scan unit test, the
dispositions and both revision-4 amendments. In its own copy of the tree it ran
the satellite, scan, resource and protocol-pin unit tests: 43 suites, 118
tests, 0 failures. Its report and log are archived in `independent-review.zip`.

**Verdict: C7 can close.**

- C7-H1 and C7-M1..M5 are resolved.
- Of the Lows:
  - L1–L7 and L11 are resolved (L7 by documentation);
  - L9 is partial for the receiver, answered below as R2-L2;
  - L10 and L12 are resolved by documentation;
  - L8 is deferred to C8a-1, and the reviewer accepted the deferral.
- Two small test gaps remain and do not block closure: the terminal's menu
  click path is reached through `handleButton`, and "no chunk tickets" is
  inferred from `hasChunk`.

ADR-049 revision 4: items 1–6, 8 and 10 accepted; items 7, 9 and 11 accepted
with changes. ADR-050 revision 4: all five items accepted, with three
conditions recorded in the text.

## Dispositions of the round-2 findings

| Finding | Disposition |
|---|---|
| R2-L1 A third-party payload assembly lost the chosen target | **Fixed.** The terminal writes the bound chip before it takes the payload, so the selected definition does not change during assembly. New GameTest `assemblyKeepsTheChosenTargetOfAThirdPartyPayload` (a payload whose targets are Earth then the Moon: the Moon stays selected) |
| R2-L2 Item 7 overstated the receiver load check | **Worded.** Item 7 now says that a receiver root loads only control chips, and that a chip that no longer decodes as a `solar` identity stays and shows as unavailable (loading stricter would quarantine a whole receiver over one chip) |
| R2-L3 Item 9's refusal list read as complete | **Worded.** It names `STORAGE_BUDGET`, `CATALOG_UNAVAILABLE`, `UNAUTHORIZED`, `UNSUPPORTED_DATA` and `IDENTITY_CONFLICT`, and states that every answer other than `SUCCESS` and `IDEMPOTENT` keeps the package |
| R2-L4 A receiver broken while the registry is blocked | **Worded** in item 7: only an operator can clear its links once the registry is repaired |
| Note: builder menu data grew from 11 to 12 slots | Accepted as is: v1.6 is unreleased, so no mixed builds exist to protect |
| ADR-050 item 1 condition (every change must be marked) | Recorded in the text as a C8 review rule, to be tested in C8a-1 |
| ADR-050 item 3 condition (`target_body` for every kind) | Recorded in the text; C8b supplies it |
| ADR-050 item 5 condition (rate limits before Gate evidence) | Recorded in the text; C8a-1 delivers them |

## Contract state

- ADR-049 and ADR-050 are now **revision 4, ACCEPTED** (2026-10-01), with an
  acceptance record for revision 4 in ADR-049 and the accepted amendment in
  both.
- The v1.6 version document and `CURRENT_VERSION.md` move from phase
  `CONTRACT_FROZEN` to `IMPLEMENTING`. `CURRENT_VERSION.md` now shows the
  runtime identity `1.20.1-1.6.0-dev`, which has been current since C7a. The
  completion plan marks C7 done and points to C8a-1.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 4m52s. **1,116 JUnit tests / 209 suites executed**, 0 failures. **279 required GameTests** passed, including the new R2-L1 test. DataGen rewrote nothing (generated diff empty) |

The log has the same 18 intentional ERROR lines as the round-1 fix run, and 0
FATAL. The repository validators, run on the staged tree after packaging, are
listed in `packaging/out/validation.log` in the evidence directory:
`validate_repository.py --require-approved-identity`,
`validate_v1plus_planning.py`, `validate_bootstrap_provenance.py`,
`python -m unittest tests.test_v1plus_planning` and `git diff --cached --check`,
all exit 0.
