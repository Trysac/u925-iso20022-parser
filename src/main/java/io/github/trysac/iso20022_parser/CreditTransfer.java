package io.github.trysac.iso20022_parser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents an ISO 20022 FI-to-FI Customer Credit Transfer ({@code pacs.008.001.08}) message.
 *
 * @param msgId the group header message identification ({@code GrpHdr/MsgId})
 * @param creationDateTime the creation timestamp ({@code GrpHdr/CreDtTm})
 * @param numberOfTransactions the declared number of transactions ({@code GrpHdr/NbOfTxs})
 * @param controlSum the declared control sum ({@code GrpHdr/CtrlSum}), or {@code null} if omitted
 * @param transactions immutable list of credit transfer transactions ({@code CdtTrfTxInf})
 */
public record CreditTransfer(
    String msgId,
    Instant creationDateTime,
    int numberOfTransactions,
    BigDecimal controlSum,
    List<Transaction> transactions) {

  /**
   * Validates non-null components and defensively copies the transaction list.
   *
   * @param msgId group header message ID
   * @param creationDateTime creation timestamp
   * @param numberOfTransactions declared transaction count
   * @param controlSum declared control sum (nullable if not present in message)
   * @param transactions list of transactions
   */
  public CreditTransfer {
    Objects.requireNonNull(msgId, "msgId");
    Objects.requireNonNull(creationDateTime, "creationDateTime");
    transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions"));
  }
}
