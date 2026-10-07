# HUD helper: bounded interface disposition after independent source review

Date: 2026-10-07. Status: source-reviewed / interface-adopted for integration;
patch not applied, Root binding not implemented, runtime qualification open.
This supersedes pending source-review/interface-decision wording only in
ASSIGNMENT-01, TASK-01 and ADOPTION-01; historical process facts stay intact.

## Evidence and decision authority

Root fully reads the independent actual helper/test/proposal
[REVIEW-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-hud-claude-source-independent-20261007-r1-4a862c/REVIEW-01.md)
(`b19e44`, exit 0), SHA-256
`7781065e65856f8f5815daf87b5c58567d8e9c5a6bc9e1ddbd809bf6719a9453`.
It reports no scoped Critical/High/Medium functional geometry finding and one
Low: two public bounds and one package-private candidate entry exceed the
original frozen interface. Its 28 static controls/18 read-only Git commands
do not constitute executed Java, renderer or runtime acceptance.

On 2026-10-07 the owner relayed Claude's A/B response in this conversation.
Claude recommends a technical note for arrangements, offers either treatment
of the constants, acknowledges execution deviations and waits for registration.
That is the author's opinion, not a new owner contract decision. Root chooses
A under the previously recorded owner authorization:
"授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认".

## Narrow additional interface

Root explicitly accepts `public static final int MIN_OFFSET = -4096` and
`MAX_OFFSET = 4096`, and package-private immutable candidate entry:

```java
static List<Layout> arrangements(int screenWidth, int screenHeight,
    PanelSettings environment, PanelSettings oxygen,
    Size environmentNormal, Size oxygenNormal,
    Size environmentCompact, Size oxygenCompact);
```

Their meanings remain CONTRACT-02's
inclusive offset limits and at most seven ordered layout candidates. This
allows config to share bounds and same-package tests to inspect candidate
coordinates/order/work, without an extra public geometry entry or mutation.
No placement, config, visibility, oxygen/API, save/network or gameplay semantic
change. Production consumers use the validated place entry; arrangements is
an internal/test seam, not a second public placement API.

The independent reviewer notes that the authored selection sweep takes its
candidate list from the implementation. Root will add an independently written
candidate generator in the integration tests, exercising the contract without
calling arrangements to construct the oracle. Internal candidate inspection
also remains for invariants not distinguished by final placement alone.
This is a future Root test obligation, not a claim of new tests executed.

The original sealed PATCH-01 and three authored postimages are eligible for
the next declared Root integration task, not applied or delivered by this
metadata decision. Patch SHA-256:
`357ddb4e4cb07640a9c205e168fdd4d9b43be9fef4000e778f29133301e1a478`.
Helper/test SHA-256:
`ba7a4415a6ffd63e45334e48d4de1ace161b714c1c32e0b372c58c3768cfccf6` /
`5402593d74f3ddf921bac5ddc9179d9c8d99a670d0d08db8198f7b86f271c948`.
All author evidence and the original Low finding remain unchanged. No successor
author patch or JVM authorization is necessary or issued for this choice.

## Remaining work and preserved limits

Root must register a fresh isolated integration task, apply the reviewed helper,
add independent-oracle tests and implement ClientConfig/LifeSupportHud bindings;
then obtain different-agent review of the actual combined diff and execute
committed-source build/JUnit Platform, twice DataGen/clean, full native and
V1/V2. Direct ConfigValue.set validation is not promised; no saturation, hidden
text truncation or mismatch between compact measurement/drawing is adopted.
The author-proposed fragments are not accepted wholesale.

Late registration, archive checkout, unauthorized author JVM/cache checks and
missing raw per-mutant traces remain process/evidence limits, not retroactively
approved or formal qualification. The eight settings, parent C18d, all other
HUD units, content ledger, CSV readiness and v1.8 G0-G9 remain unqualified.
