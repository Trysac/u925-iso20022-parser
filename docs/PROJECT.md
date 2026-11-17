# Project record

## Objective

Parse and validate ISO 20022 pacs.008 and camt.053 messages with hardened XML parsing, XSD validation and typed extraction of payment and statement fields.

## Scope

- Parse `pacs.008.001.08` and `camt.053.001.08` XML messages.
- Hardened XML parsing (size, depth, entity prohibition).
- Business rule validation (IBAN, BIC, currency, amount bounds, reconciliation).
- XSD validation against user-supplied schema.

## Out of Scope

- Generation or writing of ISO 20022 messages.
- Verification of digital signatures (e.g., XMLDSig).
- Support for other ISO 20022 message types or versions.
- ISO 4217 currency list checks beyond the 3-letter format constraint.

## Effort metrics

| Metric | Value |
|---|---|
| Tokens (input / output) | 371881 / 45553 |
| Hours (human) | pending measurement |
| Cost (USD) | 0.9893 |

## Factory run u925 (measured from API usage)

| Date (UTC) | Phase | Model | Batch | Input | Output | Cache write | Cache read | Cost (USD) |
|---|---|---|---|---|---|---|---|---|
| 2026-11-17 | generation | google/gemini-4-argon | yes | 7753 | 21389 | 0 | 0 | 0.4588 |
| 2026-11-17 | generation | google/gemini-4-argon | yes | 7782 | 21453 | 0 | 0 | 0.4602 |
| 2026-11-17 | fix-1 | google/gemini-3.8-flash | no | 49995 | 604 | 0 | 281299 | 0.0609 |
| 2026-11-17 | docs | google/gemini-3.1-flash-lite | no | 25052 | 2107 | 0 | 0 | 0.0094 |

Total: $0.9893. Prices: vertex line, as measured by its adapter. Providers serve the current version of each model id, so the date tells which version answered; versions, launch stages and price sources per line are in the factory's model register (docs/MODELS.md).
