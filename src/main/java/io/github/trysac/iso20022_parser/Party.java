package io.github.trysac.iso20022_parser;

import java.util.Objects;

/**
 * Represents a debtor or creditor party in an ISO 20022 transaction.
 *
 * @param name the party name ({@code Nm})
 * @param iban the party account IBAN ({@code Id/IBAN})
 */
public record Party(String name, String iban) {

  /**
   * Validates that party components are non-null.
   *
   * @param name the party name
   * @param iban the party IBAN
   */
  public Party {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(iban, "iban");
  }
}
