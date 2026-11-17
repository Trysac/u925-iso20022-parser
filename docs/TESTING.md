# Testing

## Running

```sh
mvn test                  # JUnit 5 and jqwik tests, with a JaCoCo report in target/site/jacoco/
java scripts/Check.java   # every gate, as CI and the factory run it
```

## Test Coverage

- `Iso20022Test`: Acceptance and unit tests for parsing, business rule validation (IBAN, BIC, amount, etc.), and schema validation. Tests hostile input scenarios including XXE, oversized files, and deep nesting.
- `TextTest`: Unit tests for normalization logic.
- `Iso20022PropertiesTest`: Property-based tests verifying IBAN logic and control sum/statement reconciliation invariants.
- `TextPropertiesTest`: Property-based tests for `Text.normalize` invariants.
