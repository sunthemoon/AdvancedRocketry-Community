# Independent manual scenario decomposition review93

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Frozen review assignment.

You are a read-only delegated worker. Do not delegate, use Claude, send OpenPet
events, modify source/contract/status files, move HEAD, stage, commit or clean
other work. Read AGENTS.md and mandatory project/version/parallel governance,
MANUAL-DECOMPOSITION-TASK-92.md and proposed ADR071. Review the actual diff in
D:/GitHub/arce-v180-manual-decomposition-20261010-92 against committed base
f2587b54b753d77bb985e652612003eee54cc769. Base unchanged source is in
D:/GitHub/arce-v180-manual-fixture-20261010-86. Root is sole source implementer.

Write only fresh external leaf D:/GitHub/ARCE-Task-Evidence/v1.8.0/
c19-manual-decomposition-review-20261010-93 and fresh sibling runtimes prefixed
c19-manual-decomposition-runtime-20261010-93-. Do not execute old evidence helpers.
Own source analysis and command capture helpers may be written in the fresh leaf.
Every read/file <=1 MiB, combined command streams <=256 KiB, entire leaf <=4 MiB.
Check C:/D: >=10 GiB before authored execution. Use pinned Python
D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe, SHA256
85b71d8c6ec1905935f74be0c9869aae198d00e98f39df699ec66f9c5a84cecd.
Analyses use -I -X utf8 -B; authored commands use -X utf8 -B.

Inspect actual source, dependency direction, discovery identity, method/helper
preservation, lifecycle composition, isolation, class sizes and new assertions.
Report findings by severity with actual file/line references. Do not prefill a
verdict. Bind base/current HEAD/tree/full status/index/all Python inputs/new text
ID fixture/task/own runners/executable before and after reads and commands.
Keep all execution helpers and source frozen while commands run. Root will send
the execution release after its own authored workload ends; until then perform
only read-only analysis and runner preparation. After release run each once:
python -X utf8 -B -m unittest tests.test_manual_evidence_organization -v
python -X utf8 -B -m unittest tests.test_collect_v002_manual_evidence -v
No other target execution or retry. Each original authored deadline is 180 seconds,
captured from the original Popen with argv/PID/UTC/wait/full raw pipes. Only that
owned child may be terminated; distinct <=10s postdeadline wait/drain cannot
qualify the command. Retain failures and nonempty runtime uninspected; remove only
fresh empty own runtimes nonrecursively. No descendants or unowned queries/kills.

Deliver complete REPORT.md, raw command receipts/streams, static evidence and exact
SHA256SUMS.txt/SEAL.json inventory, <=4 MiB. Report any helper/source/binding
mutation explicitly. State final source/assigned-input read cutoff; then never
read them again. Supply manifest/report/seal hashes and all actual command results.
No Main integration, ADR acceptance, Gate, delivery or whole-suite approval.
