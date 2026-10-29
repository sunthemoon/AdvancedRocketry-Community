# ADR-076 - A docs/17 waiver for the data-pack reload entries that vanilla and Forge write

```yaml
status: PROPOSED
date: 2026-10-29
owner: sunthemoon
deciders: []
target_version: v1.8.0
slices: [W193]
source: W179-F4, W192 option F4-D, Root decision R161
related: [ADR-064, ADR-072]
expires: at the v1.9.0 Gate
supersedes: ""
```

This is a proposal for the owner. It changes nothing until the owner decides, and it accepts no Required Gate,
version or release. Root decision R161 adopted the W192 recommendation to draft it. W193 wrote it and did not
accept it.

## Context

[docs/17 section 5](../17-V1PLUS-QUALITY-BUDGETS.md) requires a data-pack reload to bound the total number of
definitions, the file size and the aggregation of errors (the row "数据包重载": "定义总数、文件大小、错误聚合上限").
[docs/17 section 10](../17-V1PLUS-QUALITY-BUDGETS.md) requires a new ADR for any change to a threshold.

W179 (finding F4 in the [W179 record](../work/v1.8.0-root-queue/records/W179.md)) loaded a generated pack with 36
classic recipes that the mod's serializer refuses, 5 files that vanilla refuses before any serializer runs, 300
pattern files and several bad item tags. Each refused file logged its own ERROR entry, most with a stack trace, so
the number of entries grew with the number of bad files.

The [W192 proposal](../work/v1.8.0-root-queue/records/W192.md) separated the entries by who writes them. W193
implemented its option F4-A for everything the mod writes:

- The mod's eleven recipe serializers record a refusal and return `null` during a reload. The reload logs one ERROR
  summary of at most 1,024 characters (`RecipeRefusalLedger`).
- The two multiblock pattern catalogs already log one entry per catalog and reload.

The entries that remain per file are written by vanilla's and Forge's own loggers, for data that never reaches a mod
serializer or that is not the mod's. The mod cannot bound their number without changing those loggers.

## Original threshold, failure and real risk

- **Threshold.** The number of entries that a reload writes for refused data is bounded by an aggregation cap, not
  by the number of bad files.
- **Failure.** Vanilla and Forge write one entry per refused file or tag, and some entries carry a stack trace.
- **Real risk.** Log volume grows linearly with the number of bad files in the selected packs, on every reload. Only
  the server operator selects packs. Each entry is a single message with at most one stack trace, except the two list
  messages named below. There is no effect on gameplay, saved data or the network.

## Decision (proposed)

### Scope, exactly

The waiver covers only these entries. Line numbers are real source lines of Forge 47.4.10's userdev jar
`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar`, as mapped by W192 (`W192/vanilla-lines.txt` in the evidence
directory, SHA-256 `bbce06a903f061b3ba781ce413f3fcf2fb6c0ff5a8e8da26d6ccbd317283abb1`).

1. `SimpleJsonResourceReloadListener:47`, "Couldn't parse data file", ERROR with a stack trace, for a file that is
   not JSON, in every JSON resource directory.
2. `RecipeManager:63` (a Forge condition that cannot be evaluated), `RecipeManager:67` (a top-level element that is
   not an object) and `RecipeManager:169` (an unknown or missing recipe type, including a mistyped type in the mod's
   namespace), each logged at `RecipeManager:77`, "Parsing error loading recipe", ERROR with a stack trace.
3. `RecipeManager:77` for the serializers of vanilla and of other mods.
4. `RecipeManager:69`, Forge's INFO line "Skipping loading recipe ... as it's serializer returned null", once for
   each recipe that a mod serializer refuses under W193.
5. `TagLoader:53` and `TagLoader:64`, "Couldn't read tag list", ERROR with a stack trace (by reading, a tag codec
   error logs at both lines), and `TagLoader:105`, "Couldn't load tag ... as it is missing following references".
6. `NamespacedWrapper:386`, Forge's WARN "Not all defined tags for registry ... are present in data pack".

It does not cover:

- any entry that the mod's own code writes, including the recipe refusal summary, the pattern catalogs and the
  lookup-time logs (`ClassicMachineManager.resolve` and `DeferredProcessDefinition`), which stay under docs/17;
- a crash, a FATAL entry, or an entry from any other vanilla or Forge site.

### The bound that still holds

- Each entry is one message with at most one stack trace. In W179's run the largest such entry was 3,088 bytes.
- There is at most one entry per refused file and reload. For tags, it is one per pack resource, and two for a tag
  codec error (from reading, not measured).
- The number of entries is bounded by the files of the selected packs, which only the server operator installs.
- Two messages join a list without a cap: missing tag references (`TagLoader:105`) and vanished tags
  (`NamespacedWrapper:386`). Their length grows with the data, not only their number.

### Data

From W179's isolated run `b4b2-1` (`W179/heavy/b4b2-1.latest.log` in the evidence directory, SHA-256
`4455d59ca39c4602bf56ff4ad6fe1254070da6b68eb9442d22a0ecc5126fc222`), measured by W192 with
`W192/tools/entry_sizes.py` (`W192/w179-b4b2-1-entry-sizes.txt`, SHA-256
`0dfb73842f51fbbb850492c85fb8184971751ebb0dc100690b672e42cb90b924`), for one over-limit pack and its first reload:

| Source | ERROR entries | Log lines | Bytes |
|---|---|---|---|
| Top-level array, `RecipeManager:67` | 1 | 22 | 2,989 |
| Unparsable files, `SimpleJsonResourceReloadListener:47` | 2 | 51 | 5,958 |
| Tags, `TagLoader:64` and `:105` | 2 | 24 | 2,496 |
| **Waived ERROR entries in total** | **5** | **97** | **11,443** |

The restoring reload adds one WARN from `NamespacedWrapper:386` (223 bytes). Under W193, the same pack also logs 36
INFO lines from `RecipeManager:69`; they replace 36 ERROR entries of 167,403 bytes with stack traces.

### Temporary, with owner, expiry and the condition that ends it

- **Owner:** sunthemoon (the project owner) decides acceptance. Root drafts and maintains the ADR.
- **Temporary:** yes.
- **Expiry:** at the v1.9.0 Gate.
- **At that Gate,** re-measure with W179's pack and `DataPackOverLimitReloadGameTests`.
  - The waiver ends when the platform offers bounded reload diagnostics, or when an accepted ADR allows a hook that
    bounds these entries without hiding other mods' errors.
  - Otherwise it may be renewed only by a new ADR revision with the new data.
- **Review:** as docs/17 section 10 item 6 requires, a session other than the implementer reviews it before the
  owner decides.

## Alternatives considered

- **A Log4j filter on vanilla's loggers (W192 F4-E):** rejected. It would hide vanilla's and other mods' errors, and
  it would change the logging configuration that the operator owns.
- **A mixin or coremod on RecipeManager, TagLoader or the JSON listener:** not proposed. AGENTS.md section 3.3 requires
  an ADR showing that Forge events and access transformers cannot solve the problem, and the risk here is log volume
  only.
- **A placeholder recipe for a refused file (W192 F4-B):** rejected for v1.8. It changes the recipe serializer's
  network format and the protocol, and it covers only the mod's serializers.
- **A directory of the mod's own with a bounded reader (W192 F4-C):** rejected for v1.8. It changes the pack data
  format, and it covers only the mod's recipes.

## Verification

`DataPackOverLimitReloadGameTests` keeps exact expectations for every waived entry that W179's pack causes:
`w179-recipe-top-level-array`, `w179-recipe-file-unparsable`, `w179-tag-unreadable`, `w179-tag-missing-reference`
and `w179-tag-no-longer-defined` in `scripts/gametest_expected_log.json`, so the log check fails when their number
changes. The test itself asserts Forge's INFO line exactly once for each recipe that the mod refuses, because the log
check does not read INFO entries.
