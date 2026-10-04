# Charged power-plug removal decision01

Date:2026-10-04. Status:OWNER_DECISION_ACCEPTED; implementation and native
removal/replacement admission remain open.

The owner selects retained charge rather than charge discard:an ordinary
removal carries the plug's existing0..10,000 FE in the dropped plug Item;
placement restores that charge. This resolves Q1 in the preceding technical
adoption. Frozen proposed packets and their earlier pending-Q1 statements are
historical evidence,not authority to discard charge.

The finite capacity,controller-owned Item/Fluid banks,loaded-only checks and
unsupported-input protection are unchanged. Charge must not be duplicated
between a live hatch,its Item and any removal/recovery record. Unsupported
carrier data must not be silently reset or decoded as an empty plug.

Carrier schema,drop/quarantine and placement details require independently
reviewed implementation under the accepted technical contract. Actual native
removal,save/restart,placement,malformed/future Item protection and repeat-cut
tests precede runtime admission. This owner decision alone supplies no such
evidence,no charged-removal writer and no version/physical API Gate.

Special-mode compatibility remains a separate open decision:creative placement
without source consumption,clone/pick operations and removal with disabled
`doTileDrops` cannot be assumed to conserve the selected stored charge. Root
has asked for this narrow clarification;neither charge discard nor duplication
is approved as an interim behavior. Technical preparation and unrelated
Signature/hash checks continue without admitting those operations.
