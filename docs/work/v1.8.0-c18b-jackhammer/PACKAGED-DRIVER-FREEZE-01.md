# C18b packaged continuation harness source freeze

Date: 2026-10-09. Scope was published at e360a5d6 before authoring. This is a
separate external development-test harness, not a gameplay source change.

The [frozen source archive](PACKAGED-DRIVER-01.zip) contains the bounded driver,
eight pure parser/NBT/stop-order tests, the freeze helper, actual test command/log
and relative SHA256SUMS.txt. Those eight tests execute with exit 0; they are not
native or saved-player observations. No packaged process has been launched.

Archive: 9,334 compressed /24,318 uncompressed bytes, six entries.
Archive SHA-256: 9003334443508f58178f2876d20ad3c9b2f67cfb207f21a15ba6a80b6e361fbb.
Driver SHA-256: f1e4f6f194e839c86064e2d88c2ddfa6f68cc0bbf3a57af6b138dac464cb684e.
Manifest SHA-256: c80e2526d259b733395f2ea2d5c6fee9d109687ce684ed4a9c44dcd6814a2c14.
No future correction may replace this frozen archive; preserve failed receipts
and freeze a separate revision if actual checks require code changes.

Source target stays 53523bcf68af203934845521e97af2f824b2958d. Main contains no
tool source yet. Independent actual harness review, publication/consumer build,
two-process native qualification, Root input binding and evidence review remain
unfinished. The [assignment](PACKAGED-DRIVER-TASK-01.md) states exact persistence,
diagnostic, cleanup and Gate boundaries. No item or v1.8 delivery is recorded.
