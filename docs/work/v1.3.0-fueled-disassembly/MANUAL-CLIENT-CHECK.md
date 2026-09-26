# Fueled disassembly: client check

Status: NOT_RUN. Scheduled with the deferred real-client campaign under ADR-018.
Automated command/component tests do not establish chat layout or real clicks.

## Behavior

Sneak-use a rocket with the main hand. Empty rockets retain immediate disassembly.
A rocket containing fuel offers a chat action that shows the amount to discard.
Clicking the action fills the command input; submitting the command is a separate
step. Fuel is not refunded. Do nothing to retain the rocket. Offers expire after
200 server ticks and are invalidated by logout/restart or changes to bound state.

## Setup and recording

Use a disposable world, the exact tested host JAR, an ordinary non-operator owner
and a second non-operator account. Record JAR hashes, locale, server tick health,
account permissions, cargo slots and fuel units before/after each action. Retain
screenshots of the prompt/input/result and the relevant server receipts. Do not
reuse a survival world for disposal checks.

| Action | Record |
|---|---|
| Main-hand sneak-use a fueled cargo rocket in `en_us`, then `zh_cn` | Actual text, amount, wrapping, characters and inventory/fuel state |
| Repeat the interaction and use the offhand without submitting a command | Token identity, remaining-time display and rocket/cargo state |
| Click the chat action without pressing Enter | Input contents, whether anything executed, rocket/cargo state |
| Submit the suggested command while nearby | Command result, disposal receipt, fuel amount, restored cargo and entity count |
| Resubmit the same command | Result and material counts |
| Offer again on a fresh fixture; separately change fuel, move more than eight blocks away, or wait over 200 server ticks | Result of the old command and material state |
| Submit the owner's token using the other account; attempt operator diagnostics as a non-operator | Command availability, authorization result and material state |
| Repeat with zero fuel, an occupied restoration area, and after logout/restart | Observed behavior and material/journal state |

Do not label this checklist PASS without actual observations. Full V1/V2 and
release acceptance remain separate from this bounded interaction check.
