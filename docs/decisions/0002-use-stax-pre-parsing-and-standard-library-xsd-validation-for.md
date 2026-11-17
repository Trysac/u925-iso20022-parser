# Use StAX Pre-Parsing and Standard Library XSD Validation for ISO 20022 Messages

Status: accepted (generated, pending human review)

## Context

Payment and treasury systems must parse and validate ISO 20022 pacs.008.001.08 and camt.053.001.08 XML documents without exposing the JVM to XXE, entity expansion, deep nesting, or floating-point rounding errors, and without pulling in heavyweight generated model dependencies.

## Decision

Use javax.xml.stream (StAX) with DTD and external entities disabled, an upfront 10 MiB byte limit, and a 64-level element depth limit to build a compact line-aware element tree. Extract immutable Java records using BigDecimal for all monetary values, apply deterministic business rules with XPath-like locations, and run javax.xml.validation against caller-supplied XSDs with ACCESS_EXTERNAL_DTD and ACCESS_EXTERNAL_SCHEMA set to the empty string.

## Consequences

The library has zero external runtime dependencies, blocks XXE and DoS payloads deterministically before schema validation or extraction, and preserves exact decimal arithmetic and line numbers for all validation issues.
