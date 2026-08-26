# Codex Task 03 — Audit the current version independently

Act as a skeptical reviewer. Read the current version document and all claimed evidence.

Do not begin by fixing code. First verify:

- requirements were not silently weakened;
- commands were actually run;
- tests assert meaningful behavior;
- dedicated server and restart claims have evidence;
- client requests are not treated as authority;
- no arbitrary chunk loading exists;
- variable NBT/network/world scans have limits;
- persistent objects have schema versions;
- rollback/recovery paths preserve blocks, items, fluids, entities, and passengers;
- common code does not load client classes;
- imported files have exact provenance;
- no later-version scope was added;
- no Critical/High issue remains.

Re-run relevant commands and inspect diffs/logs.

Output:

1. findings ordered by severity, with file/line references;
2. missing tests and evidence;
3. Gate-by-Gate result;
4. minimal remediation plan;
5. recommendation: `BLOCKED`, `IN_PROGRESS`, or `READY_FOR_HUMAN_APPROVAL`.

Do not mark the version PASSED and do not create a release.
