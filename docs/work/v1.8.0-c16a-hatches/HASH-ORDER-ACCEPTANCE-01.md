# Native hash key-order technical adoption01

Date:2026-10-04. Status:TECHNICAL_RULE_ACCEPTED,not code/runtime admission.

Root adopts the exact [proposal01](hash-order-proposal-01.zip) after the
[independent review](reviews/hash-order-independent-01.zip) reports0 Critical/
High/Medium/Low under the owner's conditional contract authorization.
For hash-v1 compound key ordering:compare complete unsigned ordinary UTF-8
byte sequences first;only a complete primary tie compares original unsigned
UTF-16 code units,then prefix length. Original native modified-UTF framing is
unchanged. There is no key normalization,extra rejected-string class or
replacement of the existing primary order. Hash-v1 is not yet persisted by
this physical-adapter implementation,so this technical clarification claims
no migration of already written hashes.

Proposal SHA `4234e75e9be3b2a94b235608f23a1acd31b62de05d13a875ed046631ded0e03f`;
ZIP `75c40095dcc906e7a28b22adc23007bc75f7925c5df5899a29db980c3b4dee95`.
Independent report `08ddca8825e307a7aaa9ae8609b4210d441399e9110d83583ec272ba25165819`;
ZIP `9658ad59fb20c29e94716db60d882f725811416ff24df0e154763e7e35e08daa`
(264,801 bytes/110 entries),manifest
`498d88fa6748b83e5b85a9513f4b3190ca2b41f678bda0f2384bc27b6c877948`.
Root independently verifies CRC,safe unique names and every manifest identity.

Actual reviewer evidence:6 bounded Python checks and a fresh copy of the author
model exit0;the existing8-key JDK receipt is crosschecked,not rerun as Java.
40,320 finite permutations yield24 primary-only streams and1 tied-order stream;
nonzero primary comparisons retain their sign. Input79 selected snapshots,
40 named live origins and25 admitted values/tests remain unchanged.

This adopts only the ordering rule. Actual Java/hash framing,shape versus
native-emission eligibility,typed-empty/NaN distinctions,nested Tag.equals
fidelity,private GuardTicket,complete frame/native codecs and stopped-byte
native proof remain separate implementation/review dependencies. No callback,
resource authority,root writer,physical API,ledger or version Gate is admitted.
