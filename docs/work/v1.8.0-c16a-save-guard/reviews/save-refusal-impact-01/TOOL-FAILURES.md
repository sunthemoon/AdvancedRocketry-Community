# Preserved reviewer tooling failures

All inputs/source/old artifacts remained read-only. Corrections use new helper and output names.

1. Initial PowerShell search used Bash-style brace expansion `machine/{tank,pump,combustion}/*Save.java`; PowerShell returned `ParserError: Missing argument in parameter list`, exit 1 before running that compound command. A later read used three explicit paths. The tool response is preserved in thread context; this entry is a retrospective transcription, not a newly captured raw receipt.
2. Initial historical-disassembly method finder searched `public void saveAllChunks`; actual declaration is `protected void saveAllChunks`. Python raised `StopIteration`, exit 1. Corrected bounded inspection uses the actual declaration. The tool response is preserved in thread context; this entry is a retrospective transcription.
3. [inspect-01.log](inspect-01.log), exit 1: extra closing parenthesis in `inspect01.py`, parser failure before inspection. `inspect02.py` fixes only that typo.
4. [inspect-02.log](inspect-02.log), exit 1: historical manifest uses `entries`, not `files`. `inspect03.py` corrects the manifest reader, final inspection exit 0. Original helper/log remain unchanged.
5. [verify-01.log](verify-01.log), **12 tests / 1 failure / 3 errors**, exit 1: test asserted a non-ternary spelling for Signature limits; closure function assigned to a test class was bound as an instance method. `verify02.py` corrects the actual-source spelling and descriptor binding; limits themselves were not changed.
6. [verify-02.log](verify-02.log), **12 tests / 1 failure**, exit 1: historical `javap -c` represents a method reference as an invokedynamic site and does not print the inferred `BlockEntity.onChunkUnloaded` string. The actual Forge primary patch explicitly contains `BlockEntity::onChunkUnloaded`; the final control checks that primary declaration plus map clear and unload continuation rather than inventing a display spelling. Full failed output is preserved.
7. [verify-03.log](verify-03.log), exit 1 before tests: reviewer wrapper inserted a literal newline into a quoted Python expression. `verify04.py` uses `chr(10)` and produces a separate 12/0/0/0 result.
8. [observe-02.log](observe-02.log), exit 1: a forward-slash manifest key did not match Windows `Path` backslash serialization. `observe03.py` selects the uniquely named, exact-hashed source JAR and writes a separate successful observation result.

These are tooling failures, not production regressions, resource-loss reproductions or passing runtime proof. No assertions/timeouts/budgets in production or existing tests were relaxed.
