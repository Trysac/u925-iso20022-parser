package io.github.trysac.iso20022_parser;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents an opening or closing balance ({@code Bal}) in an ISO 20022 {@code camt.053.001.08}
 * bank statement.
 *
 * @param type the balance type code ({@code OPBD} or {@code CLBD})
 * @param amount the balance amount ({@code Amt})
 * @param currency the 3-letter ISO currency code ({@code Amt/@Ccy})
 * @param credit {@code true} if {@code CdtDbtInd} is {@code CRDT}, {@code false} if {@code DBIT}
 */
public record Balance(String type, BigDecimal amount, String currency, boolean credit) {

  /**
   * Validates non-null components.
   *
   * @param type balance type code
   * @param amount balance amount
   * @param currency currency code
   * @param credit credit/debit indicator
   */
  public Balance {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
  }
}
