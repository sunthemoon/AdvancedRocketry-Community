# C18c-01 contract review disposition

Date: 2026-10-04. Integrator: Root. Scope: only the six inventory tutorials.
Status: CONTRACT_FROZEN / IMPLEMENTATION_PENDING. No content unit is delivered.

## Independent review and adoption

The exact original task (SHA-256
`769f4e5d78b99bff9ed3e348aa9db3a4d061f2020e8ed6074dded0afd20f6a9d`)
was reviewed against fixed baseline
`85afa4139f74dd71f6963596b0d498ac6a0ebcfc`, accepted ADR-066 section 6.2,
ADR-061 and the existing registrations/recipes. No Critical, High, Medium or
Low findings were identified. Twelve independent static/primary controls pass;
39 named inputs were rechecked without drift. No Java/native execution was
claimed. Failed reviewer extraction and packaging attempts remain preserved.

The frozen external report is
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/acti-1f8799afbe/REVIEW-01.md`,
16,293 bytes, SHA-256
`b26781cfcfb9203a34bf96a95c04a648c49fbcc92fab6694746c18a22507fb4f`.
The internal manifest is 4,006 bytes, SHA-256
`bb176845cbae6f1cef8b6744900734c6db4c6a240667de63cd578c39459e2d2c`.
Its 25 payload files total 569,913 bytes; there is no runtime/source ZIP.

Root adopts this unchanged six-goal leaf for isolated implementation under the
user's prior conversation authorization: "授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认".
This is not a new ADR decision, risk acceptance, runtime permission, ledger
delivery or version Gate approval. Major semantic changes still require a
separate decision. Updating task checkpoint metadata does not expand scope.

## Verification conditions and write ownership

Tests must use the actual vanilla inventory listener on a connected
non-FakePlayer ServerPlayer. Forge FakePlayer is explicitly excluded by the
award implementation. Existing ConnectedTestPlayers is a server-connected
mock, not real-client/V1/V2 evidence. The suit is one current-inventory
criterion with four distinct predicates, not four cumulatively latched criteria.

The packaged restart must preserve and reload the same player's criterion
timestamps, inspect earned progress before new acquisition and distinguish
disk reload from inventory re-earning. Targets may be removed before stop
through ordinary inventory changes. Check per-player isolation and empty
rewards for these six definitions; unrelated vanilla/recipe advancements may
legitimately react to the same items.

The delegated worker's disjoint new Java files are:

- `progression/classic/ClassicInventoryAdvancements.java`;
- `datagen/V180ClassicAdvancementData.java`;
- `datagen/V180ClassicAdvancementLanguage.java`;
- `gametest/ClassicInventoryAdvancementGameTests.java`;
- the corresponding `progression/classic/ClassicInventoryAdvancementsTest.java`
  and `ClassicInventoryAdvancementResourcesTest.java` under `src/test/java`.

All are under the existing `io/github/sunthemoon/advancedrocketrycommunity`
package. The worker additionally owns only this task directory's PROGRESS and
HANDOFF. Root owns BootstrapDataGenerators, V180LanguageProvider, generated
resources, registries, ledger/status and every commit/push. A packaged-player
fixture or hook is not included in the worker write scope and requires separate
bounded review. Shared persistence and the remaining C18 work stay open.
