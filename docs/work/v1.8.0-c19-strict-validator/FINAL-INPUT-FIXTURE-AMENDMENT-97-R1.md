# Task97 source correction R1

Date: 2026-10-10. Original Task97 and Review98 remain unchanged.
Root focused PID16720 exited1 after4.047398 seconds: four tests passed, while
the new mutable-object test raised PermissionError when writing its own copied
read-only Git object on Windows. The complete original command, streams and
before/after equal bindings are retained; no timeout, cleanup or cap failure.

Correct only that test by adding chmod on the first physical copy's object before
the existing write. Keep every comparison/assertion and all original19 bodies.
No source fixture behavior or budget change. This distinct corrected generation
may run the same five once in a fresh runtime through fresh capture_v2.py,
followed by the previously unexecuted original19 once. Old helpers/results are
not overwritten or executed. Fresh source comparison uses a separate result.
Bind this amendment alongside original tasks during subsequent authored commands.
The independent reviewer reviews both generations and tests only the corrected
actual diff after release. All other Task97/Review98 limits and boundaries apply.
