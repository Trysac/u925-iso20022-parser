package io.github.trysac.iso20022_parser;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents an individual credit transfer transaction ({@code CdtTrfTxInf}) inside a {@code
 * pacs.008.001.08} message.
 *
 * @param endToEndId the end-to-end identification ({@code PmtId/EndToEndId})
 * @param amount the interbank settlement amount ({@code IntrBkSttlmAmt})
 * @param currency the 3-letter ISO currency code ({@code IntrBkSttlmAmt/@Ccy})
 * @param debtor the debtor party ({@code Dbtr} and {@code DbtrAcct})
 * @param creditor the creditor party ({@code Cdtr} and {@code CdtrAcct})
 * @param debtorAgentBic the debtor agent BICFI ({@code DbtrAgt/FinInstnId/BICFI})
 * @param creditorAgentBic the creditor agent BICFI ({@code CdtrAgt/FinInstnId/BICFI})
 * @param remittance unstructured remittance information ({@code RmtInf/Ustrd}), or empty string if
 *     absent
 */
public record Transaction(
    String endToEndId,
    BigDecimal amount,
    String currency,
    Party debtor,
    Party creditor,
    String debtorAgentBic,
    String creditorAgentBic,
    String remittance) {

  /**
   * Validates that all transaction fields are non-null.
   *
   * @param endToEndId end-to-end identifier
   * @param amount transaction amount
   * @param currency currency code
   * @param debtor debtor party
   * @param creditor creditor party
   * @param debtorAgentBic debtor agent BIC
   * @param creditorAgentBic creditor agent BIC
   * @param remittance remittance text
   */
  public Transaction {
    Objects.requireNonNull(endToEndId, "endToEndId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(debtor, "debtor");
    Objects.requireNonNull(creditor, "creditor");
    Objects.requireNonNull(debtorAgentBic, "debtorAgentBic");
    Objects.requireNonNull(creditorAgentBic, "creditorAgentBic");
    Objects.requireNonNull(remittance, "remittance");
  }
}
