# Navigation client checks — not executed

These cases remain deferred under ADR-018. Pure layout/input tests, Forge
network-free actors and packaged operator commands do not satisfy V1 or V2.
Use disposable worlds and matching flight-protocol-7 client/server artifacts.
Record artifact hashes, display/GPU, window size, GUI scale, language and logs.

## Single client / V1

1. Open a fueled rocket console, enter the map, click different nodes, drag,
   scroll, and use previous/next. Record which body is selected and its position
   after each action, including hits outside the clipped viewport.
2. Repeat at the smallest supported GUI window, at multiple GUI scales and in
   English/Chinese. Capture the title, all controls, detail rows, tooltip and
   fuel/status text. Include long custom body IDs and 48-character station names.
3. Load 100- and 128-body valid catalogs, then inspect every node through focus
   controls. Record frame timing and any overlap/clipping or input problems.
4. Select Mars/Venus surfaces and a gas giant's accessible station. Record the
   console choice before launch; test explicit launch and console cancellation.
   Inspect the gas giant's body metadata without a surface selection.
5. Change fuel, rocket state and control permissions. Compare console reasons
   and server launch outcomes. Record boarding/leaving and countdown behavior
   when the map is open, returning to the console to cancel.
6. Reload valid then invalid data while the map is open. Exercise a missing or
   rejected celestial cache, a refresh inside the full-catalog cooldown and a
   menu reopen. Record generation order, disabled/enabled controls, time to
   recovery, and whether cancellation remains available from the console.

## Two real clients / V2

1. Use an owner and nonowner at one rocket. Record visible bodies/stations and
   launch outcomes for both; change station membership while both menus stay open.
2. Revoke or remove the selected station during a countdown. Change the second
   client's editable choice, then cancel from the controlling client's console.
   Record the active plan, selected IDs, actual cancellation target and fuel.
3. Reconnect, reload, change dimensions and replace the rocket while menus or
   refreshes are pending. Record late-packet handling and the IDs of open menus.

Record failures without converting absent observations into PASS. Full GPU,
multiplayer, performance and release acceptance remain separate from the bounded
automated navigation checks.
