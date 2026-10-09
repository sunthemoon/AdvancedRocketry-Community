# Offline JSON core and diagnostic contract disposition52

Date: 2026-10-09. **NORMATIVE INTERNAL CONTRACT FROZEN; IMPLEMENTATION AND
QUALIFICATION OPEN.** Root is sole integrator. Baseline b80fb950; qualified
observation source remains d57ecda1, with no production sleep/dimension/air/
spawn/time/API/schema change. This is a narrow prerequisite, not whole-driver
adoption or evidence that any observation or D1 runtime row has executed.

Later [adoption69](MEMORY-WINDOW-ADOPTION-69.md) expressly amends only the traced
measurement-window interpretation under ADR-069 after actual owner decision07
and independent review. The original requirements below remain historical;
numeric limits and all executable/resource/runtime qualification remain required.
The windowed traced peak is not a complete birth-to-exit traced inventory.

## Normative documents and independent review

The complete 271-line /19096-byte proposal in
[candidate37](OFFLINE-JSON-CORE-PROPOSAL-37.zip), SHA256
f3658d5c631a04dfc6e053883f6ab56843fc441e163d78561f86041a862ee6f0,
plus the complete 244-line /15960-byte PROPOSED-DIAGNOSTIC-CONVENTION-01.md in
[addendum43](DIAGNOSTIC-CONVENTION-43.zip), SHA256
69f6d6b85379b23d67566ead27db5d75e6ef039066e28614f7767ebf29298048,
are normative for the separately assigned internal generic decoder and its tests.
Historical PROPOSED labels inside immutable packets remain unchanged; this
disposition supersedes only their adoption state. Addendum43 supplements missing
diagnostic conventions without reopening input, API, numeric or resource limits.

Root completely reads the actual stable reports/proposals before acknowledgments
and actual one-time custody seals. Independent40's Low R01 is addressed at the
normative level by additive43 and independent49's complete 316-line /24827-byte
[report](OFFLINE-CONVENTION-INDEPENDENT-49.zip), SHA256
81ed06d8e26a0999d2f2da5b7254d93c8b01da3bc10e52f33dd8bbd367f7228f.
That review identifies no new material diagnostic correction in the static scope;
it is not actual-source, executable, resource or native testing. R49-01 is an
existing medium qualification dependency and remains open, separately from R01.

## Frozen implementation boundaries

- Exact bytes, at most524288, one compact object root and exact EOF; no external
  whitespace, BOM repair, wrapper, multiple root, prefix/suffix or full-text clone.
- Checked iterative stack: inclusive depth64, value nodes65536, global names65536,
  names per object64, string/name4096 UTF-16 units, number lexeme64 ASCII bytes.
  Strict canonical UTF-8, paired surrogate decoding and decoded-name duplicate
  rejection precede value admission. Numbers remain inert JsonNumber records;
  no numeric expansion or source-int/float conversion.
- Frozen API/results/error shapes from37, bounded ordinal locations and original
  byte offsets; mutable successful trees are exclusively owned. No partial tree
  on refusal, attacker text in diagnostics, recursive parser, cache or schema.
- Addendum43 fixes exact-type preflight sentinel metrics and precedence; atomic
  admission reservations; contiguous committed-prefix consumption; complete
  scalar/escape admission atoms and partial maxima; error slot/key-span ownership.
  Recognized introducers in grammar-permitted slots govern reservation; invalid
  lookahead does not commit bytes. The complete37/43 documents, not this summary,
  specify every refusal field and boundary assertion.
- Absolute exact-int monotonic deadline, entry ceiling60 seconds; checks at entry,
  before allocation, each4096 bytes and before success return. This is cooperative,
  not OS preemption. Unexpected exceptions/OOM/host kill fail qualification rather
  than being fabricated successful refusals.
- Unchanged finite resource contract: at most12 fresh case processes; each traced
  peak and native process-lifetime peak <=268435456 bytes, including interpreter,
  input and result; fixed180-second external and60-second decode limits; split
  streams262144 bytes and case leaf4 MiB. No counter substitution or waiver.

Only scripts/sleep_observation_json_core.py and its paired test file may be
implemented in a separately assigned isolated worktree after this freeze commits.
The pure functional sub-slice does not claim full decoder qualification; it keeps
all resource cases and independently reviewed platform measurement prerequisites.
No third production module, native-driver integration or source-aware schema.

## Platform measurement remains a separate dependency

[Actual44](PLATFORM-COUNTER-44.zip) reports three named local Windows CPython
3.13.15 self-query probes. Its peak-working-set API is not a maximum of sampled
current values, but the last self query precedes serialization/shutdown/exit.
The report's complete303 lines /19739 bytes have SHA256
c86b95c534cfb14981c57714e8957501c60573b797bc9b017cd5d2752092fedb.
U01 and R49-01 remain open; U02 covers unimplemented decoder/resource/native/
portable/hard-allocation qualification. Working set, private commit, all native
allocation and traced blocks are not interchangeable measures.

Separate Task50 completes exactly two own-parent/original-handle child cohorts
and a custody-only seal. Root reads all307 lines /20304 bytes, SHA256
17bc576fa3d566ca85a91b8c3b8a9b13b3b98937b783d0a822a064830a835f38,
before acknowledgment. Actual post-exit native peaks are21204992 /37601280 bytes.
These are local finite observations, not resource-budget acceptance or a
retroactive amendment of44. Independent55 is assigned against fixed b80/d57;
its actual report and Root disposition remain pending. No platform/helper is
selected and no strict process-lifetime requirement is narrowed here.

## Actual custody and publication volume

Four new archives preserve originals, all covered-member hashes, actual declared
seal schemas/exclusions, embedded seal-stream hashes where present, Task44's
separate excluded command/raw receipt, safe paths, CRC and exact payload bytes.
Archives include the excluded files too. Root45's original failed packaging
receipt and43/44/49 preparation/transport gaps remain; custody cannot repair them.

| Archive | Compressed /expanded bytes | SHA256 | Covered files /bytes |
|---|---:|---|---:|
| [DIAGNOSTIC-CONVENTION-43.zip](DIAGNOSTIC-CONVENTION-43.zip) | 155242 /317032 | 6416cbca3acc15ca38c35a45c689002f910bcd2b5e3aeb39034dccf1ec0032d4 | 75 /307482 |
| [PLATFORM-COUNTER-44.zip](PLATFORM-COUNTER-44.zip) | 137119 /270884 | 0f1963fb08494e58d32a82b7ccef73fe34d18da8f391844e147548e03c6c5f4b | 92 /259336 |
| [OFFLINE-CORE-ROOT-REVIEW-45.zip](OFFLINE-CORE-ROOT-REVIEW-45.zip) | 53847 /144756 | 4bbeb6c8638102028f124f61dd67134dc0dc75163081c4aed8c0d3a1faa3ed00 | 61 /138577 |
| [OFFLINE-CONVENTION-INDEPENDENT-49.zip](OFFLINE-CONVENTION-INDEPENDENT-49.zip) | 257199 /533339 | 4d42b3b610ce8d69bf20216742e978768eec8bf5c4470cf53c5c5808e69d9e6e | 173 /514813 |

Only manifest self and SEAL-RESULT.json are excluded in43/45/49. Task44 additionally
excludes custody-seal-28.command.json/stdout.txt/stderr.txt, verified separately.
Actual40 sleep ZIPs total13596605 compressed /67429275 expanded bytes. Unchanged
65011712 reserve gives78608317 compressed-plus /132440987 conservative expanded-plus:
admission still fails before loose/later sets. This is not an atomic launch
inventory. No deletion, category change, budget increase, hard quota or launch.

## Remaining acceptance and tests

Decoder and tests are NOT_IMPLEMENTED/NOT_EXECUTED. No Gradle clean build/test,
DataGen/generated diff, GameTest, native Unicode, dedicated/restart, actual20
observations, thirteen D1 rows or real-client test executes in disposition52.
Source schema/Gson/framing/NBT/provenance, whole driver/runtime/helper/quota,
capture/log/recipe attribution and R-021 save/log/recovery acceptance remain open.
B1/R1/M1/M2, station/Space/old-world/client/item/asset/hatch policies and evidence
remain open. Ledger is186 PLANNED /154 REVIEW; ADR-068 stays PROPOSED and R-021 OPEN.
**All current-version Required Gates satisfied: NO.** No release/tag/waiver.

Next v1.8-only tasks are a separate two-file pure functional implementation and
independent55 terminal-counter assessment, followed by actual-source review and
the unchanged finite resource qualification. These do not replace the remaining
full sleep and version acceptance requirements.
