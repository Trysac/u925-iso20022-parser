package io.github.trysac.iso20022_parser;

import java.util.List;
import java.util.Objects;

/**
 * Represents an ISO 20022 Bank-to-Customer Statement ({@code camt.053.001.08}).
 *
 * @param id the statement identification ({@code Stmt/Id})
 * @param iban the account IBAN ({@code Stmt/Acct/Id/IBAN})
 * @param opening the opening booked balance ({@code OPBD})
 * @param closing the closing booked balance ({@code CLBD})
 * @param entries immutable list of statement entries ({@code Ntry})
 */
public record Statement(
    String id, String iban, Balance opening, Balance closing, List<Entry> entries) {

  /**
   * Validates non-null components and defensively copies the entry list.
   *
   * @param id statement ID
   * @param iban account IBAN
   * @param opening opening balance
   * @param closing closing balance
   * @param entries statement entries
   */
  public Statement {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(iban, "iban");
    Objects.requireNonNull(opening, "opening");
    Objects.requireNonNull(closing, "closing");
    entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
  }
}
