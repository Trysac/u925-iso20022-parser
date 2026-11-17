package io.github.trysac.iso20022_parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a statement entry ({@code Ntry}) in an ISO 20022 {@code camt.053.001.08} bank
 * statement.
 *
 * @param amount the entry amount ({@code Amt})
 * @param currency the 3-letter ISO currency code ({@code Amt/@Ccy})
 * @param credit {@code true} if {@code CdtDbtInd} is {@code CRDT}, {@code false} if {@code DBIT}
 * @param bookingDate the booking date ({@code BookgDt/Dt})
 * @param reference the entry reference ({@code NtryRef})
 */
public record Entry(
    BigDecimal amount, String currency, boolean credit, LocalDate bookingDate, String reference) {

  /**
   * Validates non-null components.
   *
   * @param amount entry amount
   * @param currency currency code
   * @param credit credit/debit indicator
   * @param bookingDate booking date
   * @param reference entry reference
   */
  public Entry {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(bookingDate, "bookingDate");
    Objects.requireNonNull(reference, "reference");
  }
}
