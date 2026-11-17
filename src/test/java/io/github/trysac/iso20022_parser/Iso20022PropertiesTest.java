package io.github.trysac.iso20022_parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** Property-based tests for {@link Iso20022} invariants. */
class Iso20022PropertiesTest {

  @Property
  void generatedValidGermanIbansPassAndCorruptedCheckDigitsFail(
      @ForAll("tenDigitStrings") String bbanPart) {
    String country = "DE";
    String bban = "37040044" + bbanPart;
    String rearranged = bban + "131400";
    BigDecimal mod = new BigDecimal(rearranged).remainder(BigDecimal.valueOf(97));
    int check = 98 - mod.intValue();
    String checkDigits = String.format("%02d", check);
    String validIban = country + checkDigits + bban;

    assertTrue(Iso20022.isValidIban(validIban));

    int badCheck = (check % 97) + 1;
    if (badCheck == check) {
      badCheck = (check + 1) % 97;
    }
    String invalidIban = country + String.format("%02d", badCheck) + bban;
    assertFalse(Iso20022.isValidIban(invalidIban));
  }

  @Provide
  Arbitrary<String> tenDigitStrings() {
    return Arbitraries.strings().numeric().ofLength(10);
  }

  @Property
  void creditTransferControlSumAndEntryReconciliationHold(
      @ForAll @IntRange(min = 1, max = 100000) int cents1,
      @ForAll @IntRange(min = 1, max = 100000) int cents2) {
    BigDecimal a1 = BigDecimal.valueOf(cents1, 2);
    BigDecimal a2 = BigDecimal.valueOf(cents2, 2);
    BigDecimal opening = BigDecimal.valueOf(500000, 2);
    BigDecimal closing = opening.add(a1).subtract(a2);

    String camt =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08">
          <BkToCstmrStmt>
            <Stmt>
              <Id>STMT-PROP</Id>
              <Acct><Id><IBAN>DE89370400440532013000</IBAN></Id></Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
              </Bal>
              <Bal>
                <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
              </Bal>
              <Ntry>
                <Amt Ccy="EUR">%s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <BookgDt><Dt>2025-02-15</Dt></BookgDt>
                <NtryRef>R1</NtryRef>
              </Ntry>
              <Ntry>
                <Amt Ccy="EUR">%s</Amt>
                <CdtDbtInd>DBIT</CdtDbtInd>
                <BookgDt><Dt>2025-02-15</Dt></BookgDt>
                <NtryRef>R2</NtryRef>
              </Ntry>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
            .formatted(
                opening.toPlainString(),
                closing.toPlainString(),
                a1.toPlainString(),
                a2.toPlainString());

    List<ValidationIssue> issues = Iso20022.validate(camt.getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of(), issues);
  }
}
