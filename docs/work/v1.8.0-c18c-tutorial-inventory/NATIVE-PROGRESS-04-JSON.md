# Bounded JSON syntax consumer: author progress

Date: 2026-10-05. Task: C18c-01-NATIVE04-JSON. Author: delegated worker
`c16a04_fluids`. Phase: ready for independent actual-source review; no delivery
or independent acceptance.

## Checkout and exclusive scope

The registered worktree is `D:/GitHub/arce-v180-c18c-json-20261005`, branch
`codex/v1.8.0-inventory-json`, base
`e091f1b0abb2dc2068c8e7cf0f6e97e48d3aaa03`. The frozen contract's earlier code
baseline is `95c43b8588a33c51bf0c7017ea92beedd48833e1`; the author does not
move HEAD, stage, commit or push. Root remains the sole integrator.

Only these four new files are writable: `scripts/classic_inventory_fixture_json.py`,
`scripts/test_classic_inventory_fixture_json.py`, this progress record, and
`NATIVE-HANDOFF-04-JSON.md`. All existing modules, central files and governance
remain read-only. Effective governance is Root's live 12,047-byte `AGENTS.md`,
SHA-256 `1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`;
the shorter worktree copy is not used as the effective policy.

## Implemented development candidate

The byte consumer uses a bounded recursive-descent syntax parser. Node and
depth charges precede token decoding or child descent. Entry framing is checked
during construction. Ordered object pairs, arrays, numbers and observations
are immutable. The five frozen role budgets are unchanged. Errors use only
fixed codes and roles, with suppressed chaining. Integer lexemes are retained
without integer conversion; fraction/exponent projections are explicitly lossy.

Stats entry-framing errors use `JSON_ENTRIES`; wrong root container uses
`JSON_ROOT`. These select existing fixed diagnostic codes, not new schemas.
No file reader, CLI, native fixture, record validator, writer, receipt or
gameplay behavior is implemented. No Java or native process is authorized.

## Evidence

Fresh loose evidence and process-local temporary files are confined to
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-json-author-8c6f1a290d`.
`INTAKE-01.json` records the clean registered checkout, 17 named input pins and
Python 3.13.15. The first JSON run (`checks-01`) exited 1: 48 tests and two
fixture failures. The sentinel `\\bad` contains a valid JSON backspace escape;
it was replaced with actually invalid `\\qbad`, with a direct sentinel control.
The production parser has remained byte-identical since that first run. Raw
failure logs and tested postimages remain in separate immutable run folders.

`checks-02` exited 0 with 122 tests. After three additional number-boundary
controls, final `checks-03` exited 0 with 125 tests: 52 new JSON, 39 unchanged
phase01 and 34 unchanged properties-input tests. There were no failures,
errors or skips. Fresh structure controls also passed 1,000 seeded valid
documents against independent structural counts and standard-library syntax
observations. This does not establish native compatibility.

All 17 named input pins remain unchanged. Existing tracked-file diff is empty;
only the four authorized new paths are untracked. The properties tests created
two new owned D fixture directories. A no-follow checked cleanup removed their
308 entries and 198,750 regular-file bytes, retaining platform observations,
precheck, result and raw log. No old cleanup target or source world was touched.

Independent actual-source review and Root's fixed-code-commit replay are still
required. This task does not close C18c delivery, R-021, the ledger or G0-G9.
