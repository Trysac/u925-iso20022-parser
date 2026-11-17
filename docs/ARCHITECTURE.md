# Architecture

## Overview

`iso20022-parser` provides a zero-dependency Java 21 reader and validator for the two core ISO 20022 cash management and payment messages:

- `pacs.008.001.08` (`FIToFICstmrCdtTrf`)
- `camt.053.001.08` (`BkToCstmrStmt`)

## Components

- `Iso20022`: Entry point exposing `detect`, `parseCreditTransfer`, `parseStatement`, `validate`, and `validateSchema`.
- `CreditTransfer`, `Transaction`, `Party`: Immutable records modeling `pacs.008.001.08`.
- `Statement`, `Balance`, `Entry`: Immutable records modeling `camt.053.001.08`.
- `ValidationIssue`: Immutable record carrying `code`, XPath-like `location`, 1-based XML `line`, and `message`.
- `Iso20022Exception`: Typed `IllegalArgumentException` subclass with a machine-readable `code()`.
