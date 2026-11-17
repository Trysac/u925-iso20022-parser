package io.github.trysac.iso20022_parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit and acceptance tests for {@link Iso20022}. */
class Iso20022Test {

  static final String PACS008_SUBSET_XSD =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                 targetNamespace="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08"
                 xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08"
                 elementFormDefault="qualified">
        <xs:element name="Document">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="FIToFICstmrCdtTrf">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="GrpHdr">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="MsgId" type="xs:string"/>
                          <xs:element name="CreDtTm" type="xs:dateTime"/>
                          <xs:element name="NbOfTxs" type="xs:string"/>
                          <xs:element name="CtrlSum" type="xs:decimal" minOccurs="0"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:any minOccurs="1" maxOccurs="unbounded" processContents="lax"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:schema>
      """;

  private static byte[] readFixture(String name) throws IOException {
    return Files.readAllBytes(Path.of("src/test/resources/fixtures", name));
  }

  @Test
  void parsesPacs008Fixture() throws IOException {
    byte[] xml = readFixture("pacs008.xml");
    CreditTransfer ct = Iso20022.parseCreditTransfer(xml);

    assertEquals("MSG-0001", ct.msgId());
    assertEquals(Instant.parse("2025-02-15T10:30:00Z"), ct.creationDateTime());
    assertEquals(1, ct.numberOfTransactions());
    assertEquals(new BigDecimal("1250.50"), ct.controlSum());
    assertEquals(1, ct.transactions().size());

    Transaction tx = ct.transactions().get(0);
    assertEquals("E2E-0001", tx.endToEndId());
    assertEquals(new BigDecimal("1250.50"), tx.amount());
    assertEquals("EUR", tx.currency());
    assertEquals("DE89370400440532013000", tx.debtor().iban());
    assertEquals("Example Debtor GmbH", tx.debtor().name());
    assertEquals("COBADEFFXXX", tx.creditorAgentBic());
    assertEquals("COBADEFFXXX", tx.debtorAgentBic());
    assertEquals("Example Creditor AG", tx.creditor().name());
    assertEquals("DE89370400440532013000", tx.creditor().iban());
    assertEquals("Invoice 2025-0001", tx.remittance());

    assertTrue(Iso20022.validate(xml).isEmpty());
  }

  @Test
  void parsesCamt053FixtureAndDetectsBalanceMismatch() throws IOException {
    byte[] xml = readFixture("camt053.xml");
    Statement stmt = Iso20022.parseStatement(xml);

    assertEquals("STMT-0001", stmt.id());
    assertEquals("DE89370400440532013000", stmt.iban());
    assertEquals("OPBD", stmt.opening().type());
    assertEquals(new BigDecimal("1000.00"), stmt.opening().amount());
    assertEquals("EUR", stmt.opening().currency());
    assertTrue(stmt.opening().credit());

    assertEquals("CLBD", stmt.closing().type());
    assertEquals(new BigDecimal("750.00"), stmt.closing().amount());
    assertEquals("EUR", stmt.closing().currency());
    assertTrue(stmt.closing().credit());

    assertEquals(2, stmt.entries().size());
    Entry first = stmt.entries().get(0);
    assertEquals(new BigDecimal("150.00"), first.amount());
    assertEquals("EUR", first.currency());
    assertTrue(first.credit());
    assertEquals(LocalDate.of(2025, 2, 15), first.bookingDate());
    assertEquals("REF-0001", first.reference());

    Entry second = stmt.entries().get(1);
    assertEquals(new BigDecimal("400.00"), second.amount());
    assertFalse(second.credit());
    assertEquals("REF-0002", second.reference());

    assertTrue(Iso20022.validate(xml).isEmpty());

    String mismatchedXml = new String(xml, StandardCharsets.UTF_8).replace("750.00", "800.00");
    List<ValidationIssue> issues =
        Iso20022.validate(mismatchedXml.getBytes(StandardCharsets.UTF_8));
    assertEquals(1, issues.size());
    assertEquals("balance_mismatch", issues.get(0).code());
    assertEquals("/Document/BkToCstmrStmt/Stmt/Bal[2]", issues.get(0).location());
  }

  @Test
  void detectsSupportedNamespacesAndRejectsUnsupported() throws IOException {
    assertEquals("pacs.008.001.08", Iso20022.detect(readFixture("pacs008.xml")));
    assertEquals("camt.053.001.08", Iso20022.detect(readFixture("camt053.xml")));

    byte[] otherNs =
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\"><CstmrCdtTrfInitn/></Document>"
            .getBytes(StandardCharsets.UTF_8);
    Iso20022Exception ex = assertThrows(Iso20022Exception.class, () -> Iso20022.detect(otherNs));
    assertEquals("unsupported_message", ex.code());

    byte[] wrongRoot = "<NotDocument/>".getBytes(StandardCharsets.UTF_8);
    Iso20022Exception rootEx =
        assertThrows(Iso20022Exception.class, () -> Iso20022.detect(wrongRoot));
    assertEquals("missing_element", rootEx.code());

    byte[] wrongTypeForPacs = readFixture("camt053.xml");
    Iso20022Exception mismatch1 =
        assertThrows(Iso20022Exception.class, () -> Iso20022.parseCreditTransfer(wrongTypeForPacs));
    assertEquals("unsupported_message", mismatch1.code());

    byte[] wrongTypeForCamt = readFixture("pacs008.xml");
    Iso20022Exception mismatch2 =
        assertThrows(Iso20022Exception.class, () -> Iso20022.parseStatement(wrongTypeForCamt));
    assertEquals("unsupported_message", mismatch2.code());
  }

  @Test
  void validatesBusinessRulesWithXPathLocations() throws IOException {
    String base = new String(readFixture("pacs008.xml"), StandardCharsets.UTF_8);

    String broken =
        base.replace("<NbOfTxs>1</NbOfTxs>", "<NbOfTxs>2</NbOfTxs>")
            .replace("<CtrlSum>1250.50</CtrlSum>", "<CtrlSum>9999.99</CtrlSum>")
            .replace(
                "<IntrBkSttlmAmt Ccy=\"EUR\">1250.50</IntrBkSttlmAmt>",
                "<IntrBkSttlmAmt Ccy=\"eu1\">1250.123456</IntrBkSttlmAmt>")
            .replaceFirst("DE89370400440532013000", "DE89370400440532013001")
            .replaceFirst("COBADEFFXXX", "COBA1EFF");

    List<ValidationIssue> issues = Iso20022.validate(broken.getBytes(StandardCharsets.UTF_8));
    List<String> codes = issues.stream().map(ValidationIssue::code).toList();

    assertTrue(codes.contains("invalid_iban"));
    assertTrue(codes.contains("invalid_bic"));
    assertTrue(codes.contains("invalid_currency"));
    assertTrue(codes.contains("invalid_amount"));
    assertTrue(codes.contains("count_mismatch"));
    assertTrue(codes.contains("control_sum_mismatch"));

    ValidationIssue ibanIssue =
        issues.stream().filter(i -> i.code().equals("invalid_iban")).findFirst().orElseThrow();
    assertEquals(
        "/Document/FIToFICstmrCdtTrf/CdtTrfTxInf[1]/DbtrAcct/Id/IBAN", ibanIssue.location());
    assertTrue(ibanIssue.line() > 0);

    ValidationIssue bicIssue =
        issues.stream().filter(i -> i.code().equals("invalid_bic")).findFirst().orElseThrow();
    assertEquals(
        "/Document/FIToFICstmrCdtTrf/CdtTrfTxInf[1]/DbtrAgt/FinInstnId/BICFI", bicIssue.location());
  }

  @Test
  void validatesAgainstCallerSuppliedSubsetSchema(@TempDir Path tempDir) throws IOException {
    Path schemaPath = tempDir.resolve("pacs008-subset.xsd");
    Files.writeString(schemaPath, PACS008_SUBSET_XSD, StandardCharsets.UTF_8);

    byte[] validXml = readFixture("pacs008.xml");
    List<ValidationIssue> validIssues = Iso20022.validateSchema(validXml, schemaPath);
    assertTrue(validIssues.isEmpty());

    String missingMsgId =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
          <FIToFICstmrCdtTrf>
            <GrpHdr><CreDtTm>2025-02-15T10:30:00Z</CreDtTm><NbOfTxs>1</NbOfTxs></GrpHdr>
            <CdtTrfTxInf/>
          </FIToFICstmrCdtTrf>
        </Document>
        """;
    List<ValidationIssue> invalidIssues =
        Iso20022.validateSchema(missingMsgId.getBytes(StandardCharsets.UTF_8), schemaPath);
    assertFalse(invalidIssues.isEmpty());
    ValidationIssue first = invalidIssues.get(0);
    assertEquals("schema", first.code());
    assertEquals(4, first.line());
  }

  @Test
  void rejectsHostileAndMalformedInputs() {
    byte[] xxe =
        ("<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE Document [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">&xxe;</Document>")
            .getBytes(StandardCharsets.UTF_8);
    Iso20022Exception dtdEx = assertThrows(Iso20022Exception.class, () -> Iso20022.detect(xxe));
    assertEquals("doctype_forbidden", dtdEx.code());

    byte[] oversized = new byte[10_485_761];
    Iso20022Exception sizeEx =
        assertThrows(Iso20022Exception.class, () -> Iso20022.detect(oversized));
    assertEquals("input_too_large", sizeEx.code());

    StringBuilder deepBuilder = new StringBuilder();
    deepBuilder.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">");
    for (int i = 0; i < 64; i++) {
      deepBuilder.append("<n>");
    }
    for (int i = 0; i < 64; i++) {
      deepBuilder.append("</n>");
    }
    deepBuilder.append("</Document>");
    byte[] deepXml = deepBuilder.toString().getBytes(StandardCharsets.UTF_8);
    Iso20022Exception deepEx =
        assertThrows(Iso20022Exception.class, () -> Iso20022.detect(deepXml));
    assertEquals("too_deep", deepEx.code());

    byte[] malformed = "<Document><unclosed></Document>".getBytes(StandardCharsets.UTF_8);
    Iso20022Exception malEx =
        assertThrows(Iso20022Exception.class, () -> Iso20022.detect(malformed));
    assertEquals("malformed", malEx.code());

    Iso20022Exception emptyEx =
        assertThrows(Iso20022Exception.class, () -> Iso20022.detect(new byte[0]));
    assertEquals("malformed", emptyEx.code());
  }

  @Test
  void handlesOptionalFieldsAndEdgeCases(@TempDir Path tempDir) throws IOException {
    String base = new String(readFixture("pacs008.xml"), StandardCharsets.UTF_8);
    String noOptional =
        base.replace("<CtrlSum>1250.50</CtrlSum>", "")
            .replace("<RmtInf>\n        <Ustrd>Invoice 2025-0001</Ustrd>\n      </RmtInf>", "");
    CreditTransfer ct = Iso20022.parseCreditTransfer(noOptional.getBytes(StandardCharsets.UTF_8));
    assertNull(ct.controlSum());
    assertEquals("", ct.transactions().get(0).remittance());

    String negativeAmt = base.replace("1250.50", "-10.00");
    List<ValidationIssue> negIssues =
        Iso20022.validate(negativeAmt.getBytes(StandardCharsets.UTF_8));
    assertTrue(negIssues.stream().anyMatch(i -> i.code().equals("invalid_amount")));

    String hugeAmt = base.replace("1250.50", "1234567890123456789.00");
    List<ValidationIssue> hugeIssues = Iso20022.validate(hugeAmt.getBytes(StandardCharsets.UTF_8));
    assertTrue(hugeIssues.stream().anyMatch(i -> i.code().equals("invalid_amount")));

    Iso20022Exception missingXsd =
        assertThrows(
            Iso20022Exception.class,
            () ->
                Iso20022.validateSchema(
                    base.getBytes(StandardCharsets.UTF_8), tempDir.resolve("missing.xsd")));
    assertEquals("malformed", missingXsd.code());

    Iso20022Exception longMsg =
        new Iso20022Exception("malformed", "x".repeat(250), new RuntimeException("cause"));
    assertEquals(200, longMsg.getMessage().length());
    assertEquals("malformed", longMsg.code());
  }
}
