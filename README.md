# iso20022-parser

Hardened, dependency-free Java 21 library for parsing and validating ISO 20022 `pacs.008.001.08` (FI-to-FI Customer Credit Transfer) and `camt.053.001.08` (Bank-to-Customer Statement) XML messages.

## Problem

Payment and treasury systems receive ISO 20022 XML messages and must reject malformed or hostile files before processing. Standard XML parsers are vulnerable to entity expansion attacks, and simple hand-written parsing often misses schema-level business rules like IBAN checksums or balance reconciliation.

## Usage

```java
import io.github.trysac.iso20022_parser.*;
import java.nio.file.Files;

byte[] xml = Files.readAllBytes(Path.of("message.xml"));
String type = Iso20022.detect(xml);
// Parse messages into immutable records
CreditTransfer ct = Iso20022.parseCreditTransfer(xml);
// Run business validation
List<ValidationIssue> issues = Iso20022.validate(xml);
```

## API Summary

- `Iso20022.detect(byte[] xml)`: Detects message type or throws `Iso20022Exception`.
- `Iso20022.parseCreditTransfer(byte[] xml)`: Returns `CreditTransfer` record or throws.
- `Iso20022.parseStatement(byte[] xml)`: Returns `Statement` record or throws.
- `Iso20022.validate(byte[] xml)`: Returns `List<ValidationIssue>` for business rules.
- `Iso20022.validateSchema(byte[] xml, Path xsd)`: Runs XSD validation.

**Limits**: Maximum 10 MiB input size; maximum 64 element nesting depth.
**Errors**: `Iso20022Exception` (codes: `unsupported_message`, `doctype_forbidden`, `input_too_large`, `too_deep`, `malformed`, `missing_element`).

## Why not Prowide ISO 20022

Prowide provides a massive, generated model of all ISO 20022 messages. This library is a minimal, dependency-free alternative focused on hardened parsing and specific business validation for two common message types.

See [docs/SECURITY.md](docs/SECURITY.md) for threat model details.
