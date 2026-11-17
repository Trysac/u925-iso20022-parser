package io.github.trysac.iso20022_parser;

import java.util.Objects;

/**
 * Represents a business-rule or XML schema validation issue.
 *
 * @param code machine-readable issue code (for example {@code invalid_iban}, {@code invalid_bic},
 *     {@code invalid_currency}, {@code invalid_amount}, {@code count_mismatch}, {@code
 *     control_sum_mismatch}, {@code balance_mismatch}, or {@code schema})
 * @param location XPath-like location of the offending element, or empty string for schema errors
 * @param line 1-based XML line number where the element or schema issue occurred
 * @param message human-readable description of the validation failure
 */
public record ValidationIssue(String code, String location, int line, String message) {

  /**
   * Validates non-null components.
   *
   * @param code issue code
   * @param location element path
   * @param line line number
   * @param message issue description
   */
  public ValidationIssue {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(message, "message");
  }
}
