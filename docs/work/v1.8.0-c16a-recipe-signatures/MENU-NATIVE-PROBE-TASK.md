# C16a-01-MENU-N1 actual Forge registry observation

Date:2026-10-04.Status:Root-integrated and actual Forge observation passed.

Outcome:inside an actual Forge GameTest launcher,observe the already installed
required menu advertisement and both native channel-validation directions.
The independent plain-JUnit registration probe remains32 cases/one failure:
Forge EventListenerHelper requires an event constructor supplied by native
transformation,so that probe did not reach its advertisement/refusal assertions.
This different runtime observation neither overwrites nor skips that failure.

Write boundary:one new `gametest/RecipeMenuChannelGameTests.java`,no production,
channel registration,protocol,resource or authority changes.Root Temp is the
author boundary;actual-source review is a different agent before integration.
Pinned Forge47.4.10 package-private methods are accessed reflectively only by
the test.Reflection failure fails the test,without fallback or relaxed checks.

The launcher must already have run common setup.Do not register/reset/mutate
the global network registry or synthesize an unregistered-before-setup state.
Clone advertised maps and alter only the local supplied menu version;check
matching,missing,malformed,ABSENT and vanilla cases in both directions,with
other channel advertisements unchanged.No real peer connects or client menu
renders here;actual handshake disconnection/player/V1/V2 remain separate.

Static review is allowed.No Java/native launch until a separate Root grant.
Include exact source pins,diff,commands/results and original failed probe
identity;independent actual-diff review precedes Root integration.Full native
test execution must be observed,not inferred from compilation.

- [x] One-file source proposal and independent actual-source review.
- [x] Root compile and actual Forge test;original failure preserved.
- [ ] Real peers/client menu evidence,not supplied by this test.

Source18 adds exactly4,019 bytes,source SHA256
`8452cbb77f9b4a34ab0cb8c3535b9b9e0f60814a450699cab488d72b71397af5`.
Root clean build/test/DataGen exits0;fullGT09 exits0 with464 required cases and
the `machine_menu_channel` batch runs its one test. Its16 deliberate native
version refusals emit ERROR logs;full-run total61 is preserved. Reflection uses
the pinned actual Forge methods and never registers or mutates a channel.
