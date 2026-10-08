# Powder geometry test interval correction

Root, 2026-10-08. Original author source remains committed unchanged at
31fef56e054ec92d2bdc1e8c443f452f2b5b8e0c. The first real Java 17 command on
de009a79f8dab80b0fb04f328c7d797f5e3139f9 was:
`gradlew.bat compileAdapterTestJava test --tests '*V180ThermiteDataTest' --no-daemon --stacktrace`.
Main, isolated adapter and test compilation passed. Eleven JUnit cases ran;
ten passed and the powder geometry case failed at row 15, the transparent
bottom margin. Gradle exited 1. XML, complete log, source SHA and exit are
preserved in the external source-execution packet before any overwrite.

The intended low mound occupies rows 8 through 14, not its transparent border.
Limit the nondecreasing-width comparison to those seven rows and explicitly
require zero opaque pixels in row 15. Existing nonzero-pixel margin checks,
heap width/count, PNG/CRC/alpha/palette and torch geometry assertions remain.
No production pixels, recipe, light, timing, numeric contract or test budget
changes. This fixes inconsistent test intervals, not a failing product by
weakening its contract. Separate independent source/test review is still due.

Root also replaces its three newly introduced deprecated ResourceLocation
constructor calls with the existing tryParse convention for literal IDs. The
earlier compiler warnings are preserved; unrelated baseline warnings are not
edited. Nine native tests have now been brought in unchanged from separately
pushed worker commit 5ef94e5f2339794e432ec254abcc9116c086ffcf after release.
Their compilation and execution remain unverified at this record's creation.

Evidence: D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-thermite-source-execution-20261008-01/
BUILD-TEST-01.log, BUILD-TEST-01.exit.txt, TEST-THERMITE-01.xml and commit record.
No delivery, ledger, R-021 or Required Gate decision is made here.
