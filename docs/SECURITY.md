# Security & Threat Model

## Defended Threats

- **XML External Entity (XXE) & Entity Expansion**: `XMLInputFactory` disables DTDs and entity replacement. `DOCTYPE` declarations or entity references immediately throw `doctype_forbidden`.
- **External Schema/DTD Resolution**: `SchemaFactory` and `Validator` explicitly set `ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` to empty strings.
- **Resource Exhaustion**: Inputs larger than 10,485,760 bytes are rejected before parsing (`input_too_large`). Nesting depth exceeding 64 levels is rejected during streaming (`too_deep`).
- **Sensitive Data Leakage**: Exception messages are sanitized and truncated to 200 characters to prevent dumping sensitive XML payload content into logs.

## Not Defended

- **Message Authenticity**: Cryptographic message signatures (e.g., `XMLDSig`) are not parsed or verified by this library.
