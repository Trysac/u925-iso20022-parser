package io.github.trysac.iso20022_parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Hardened parser, detector, and validator for ISO 20022 {@code pacs.008.001.08} and {@code
 * camt.053.001.08} XML messages.
 */
public final class Iso20022 {

  /** Maximum permitted XML input size in bytes (10 MiB). */
  public static final int MAX_INPUT_BYTES = 10_485_760;

  /** Maximum permitted XML element nesting depth. */
  public static final int MAX_DEPTH = 64;

  /** Supported ISO 20022 message identifier for FI-to-FI Customer Credit Transfer. */
  public static final String PACS_008 = "pacs.008.001.08";

  /** Supported ISO 20022 message identifier for Bank-to-Customer Statement. */
  public static final String CAMT_053 = "camt.053.001.08";

  private static final String NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:";
  private static final Pattern BIC_PATTERN =
      Pattern.compile("^[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");
  private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
  private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
  private static final BigDecimal MOD_97 = BigDecimal.valueOf(97);

  private Iso20022() {}

  /**
   * Detects the ISO 20022 message identifier from the {@code Document} namespace while verifying
   * full well-formedness and security limits (size, DOCTYPE prohibition, and max nesting depth).
   *
   * @param xml raw UTF-8 XML bytes
   * @return {@code "pacs.008.001.08"} or {@code "camt.053.001.08"}
   * @throws Iso20022Exception if the input violates security limits, is malformed, or declares an
   *     unsupported namespace
   */
  public static String detect(byte[] xml) {
    XmlNode root = parseTree(xml);
    return detectFromRoot(root);
  }

  /**
   * Parses an ISO 20022 {@code pacs.008.001.08} FI-to-FI Customer Credit Transfer XML document.
   *
   * @param xml raw UTF-8 XML bytes
   * @return the extracted {@link CreditTransfer}
   * @throws Iso20022Exception if the document is not a valid {@code pacs.008.001.08} message or
   *     misses required fields
   */
  public static CreditTransfer parseCreditTransfer(byte[] xml) {
    XmlNode root = parseTree(xml);
    String msgType = detectFromRoot(root);
    if (!PACS_008.equals(msgType)) {
      throw new Iso20022Exception(
          "unsupported_message", "Expected " + PACS_008 + " but got " + msgType);
    }
    return extractCreditTransfer(root, null);
  }

  /**
   * Parses an ISO 20022 {@code camt.053.001.08} Bank-to-Customer Statement XML document.
   *
   * @param xml raw UTF-8 XML bytes
   * @return the extracted {@link Statement}
   * @throws Iso20022Exception if the document is not a valid {@code camt.053.001.08} message or
   *     misses required fields
   */
  public static Statement parseStatement(byte[] xml) {
    XmlNode root = parseTree(xml);
    String msgType = detectFromRoot(root);
    if (!CAMT_053.equals(msgType)) {
      throw new Iso20022Exception(
          "unsupported_message", "Expected " + CAMT_053 + " but got " + msgType);
    }
    return extractStatement(root, null);
  }

  /**
   * Validates an ISO 20022 {@code pacs.008.001.08} or {@code camt.053.001.08} document against all
   * business rules (IBAN check digits, BIC format, currency format, positive 18/5 decimal bounds,
   * transaction count, control sum, and statement balance reconciliation).
   *
   * @param xml raw UTF-8 XML bytes
   * @return an immutable list of {@link ValidationIssue}s (empty if valid)
   * @throws Iso20022Exception if the XML is hostile, malformed, unsupported, or missing mandatory
   *     elements
   */
  public static List<ValidationIssue> validate(byte[] xml) {
    XmlNode root = parseTree(xml);
    String msgType = detectFromRoot(root);
    List<ValidationIssue> issues = new ArrayList<>();
    if (PACS_008.equals(msgType)) {
      extractCreditTransfer(root, issues);
    } else {
      extractStatement(root, issues);
    }
    return List.copyOf(issues);
  }

  /**
   * Validates an ISO 20022 XML document against a caller-supplied XSD schema file after running the
   * hardened StAX pre-check.
   *
   * @param xml raw UTF-8 XML bytes
   * @param xsd path to the XSD schema file
   * @return an immutable list of schema {@link ValidationIssue}s with code {@code "schema"}
   * @throws Iso20022Exception if the XML is hostile, oversized, too deep, malformed, or if the
   *     schema cannot be loaded
   */
  public static List<ValidationIssue> validateSchema(byte[] xml, Path xsd) {
    Objects.requireNonNull(xml, "xml");
    Objects.requireNonNull(xsd, "xsd");
    if (!Files.isRegularFile(xsd)) {
      throw new Iso20022Exception("malformed", "XSD schema path does not exist");
    }
    // Run hardened StAX pre-parse to enforce size, DOCTYPE prohibition, depth <= 64, and
    // well-formedness before invoking the schema validator.
    parseTree(xml);

    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Schema schema = factory.newSchema(xsd.toFile());
      Validator validator = schema.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

      List<ValidationIssue> issues = new ArrayList<>();
      validator.setErrorHandler(
          new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
              // Warnings are not validation errors
            }

            @Override
            public void error(SAXParseException exception) {
              issues.add(
                  new ValidationIssue(
                      "schema", "", exception.getLineNumber(), sanitize(exception.getMessage())));
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
              throw exception;
            }
          });

      validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
      return List.copyOf(issues);
    } catch (SAXException | IOException e) {
      throw new Iso20022Exception("malformed", "Schema validation failed", e);
    }
  }

  private static String sanitize(String msg) {
    if (msg == null) {
      return "Schema validation error";
    }
    return msg.length() <= 200 ? msg : msg.substring(0, 197) + "...";
  }

  private static String detectFromRoot(XmlNode root) {
    if (!"Document".equals(root.name)) {
      throw new Iso20022Exception(
          "missing_element", "Root element must be Document, found: " + root.name);
    }
    String ns = root.namespace;
    if (ns == null || !ns.startsWith(NAMESPACE_PREFIX)) {
      throw new Iso20022Exception(
          "unsupported_message", "Unsupported or missing ISO 20022 Document namespace");
    }
    String id = ns.substring(NAMESPACE_PREFIX.length());
    if (!PACS_008.equals(id) && !CAMT_053.equals(id)) {
      throw new Iso20022Exception("unsupported_message", "Unsupported message type: " + id);
    }
    return id;
  }

  private static CreditTransfer extractCreditTransfer(
      XmlNode document, List<ValidationIssue> issues) {
    String basePath = "/Document/FIToFICstmrCdtTrf";
    XmlNode transfer = requireChild(document, "FIToFICstmrCdtTrf", basePath);
    String hdrPath = basePath + "/GrpHdr";
    XmlNode grpHdr = requireChild(transfer, "GrpHdr", hdrPath);

    String msgId = requireText(grpHdr, "MsgId", hdrPath + "/MsgId");
    XmlNode creDtTmNode = requireChild(grpHdr, "CreDtTm", hdrPath + "/CreDtTm");
    Instant creationDateTime = parseInstant(creDtTmNode.text(), hdrPath + "/CreDtTm");

    XmlNode nbOfTxsNode = requireChild(grpHdr, "NbOfTxs", hdrPath + "/NbOfTxs");
    int numberOfTransactions = parsePositiveOrZeroInt(nbOfTxsNode.text(), hdrPath + "/NbOfTxs");

    XmlNode ctrlSumNode = grpHdr.firstChild("CtrlSum");
    BigDecimal controlSum = null;
    if (ctrlSumNode != null) {
      String ctrlPath = hdrPath + "/CtrlSum";
      controlSum = parseDecimal(ctrlSumNode.text(), ctrlPath);
    }

    List<XmlNode> txNodes = transfer.children("CdtTrfTxInf");
    if (txNodes.isEmpty()) {
      throw new Iso20022Exception(
          "missing_element", "Missing required element: " + basePath + "/CdtTrfTxInf");
    }

    List<Transaction> transactions = new ArrayList<>(txNodes.size());
    BigDecimal computedSum = BigDecimal.ZERO;

    for (int i = 0; i < txNodes.size(); i++) {
      XmlNode txNode = txNodes.get(i);
      String txPath = basePath + "/CdtTrfTxInf[" + (i + 1) + "]";

      XmlNode pmtId = requireChild(txNode, "PmtId", txPath + "/PmtId");
      String endToEndId = requireText(pmtId, "EndToEndId", txPath + "/PmtId/EndToEndId");

      String amtPath = txPath + "/IntrBkSttlmAmt";
      XmlNode amtNode = requireChild(txNode, "IntrBkSttlmAmt", amtPath);
      BigDecimal amount = parseDecimal(amtNode.text(), amtPath);
      String currency = amtNode.attribute("Ccy");
      if (currency == null || currency.isEmpty()) {
        throw new Iso20022Exception("missing_element", "Missing Ccy attribute at " + amtPath);
      }

      if (issues != null) {
        validateAmount(amount, amtPath, amtNode.line, issues);
        validateCurrency(currency, amtPath + "/@Ccy", amtNode.line, issues);
      }
      computedSum = computedSum.add(amount);

      Party debtor = extractParty(txNode, "Dbtr", "DbtrAcct", txPath, issues);
      String dbtrBicPath = txPath + "/DbtrAgt/FinInstnId/BICFI";
      XmlNode dbtrAgt = requireChild(txNode, "DbtrAgt", txPath + "/DbtrAgt");
      XmlNode dbtrFin = requireChild(dbtrAgt, "FinInstnId", txPath + "/DbtrAgt/FinInstnId");
      XmlNode dbtrBicNode = requireChild(dbtrFin, "BICFI", dbtrBicPath);
      String debtorAgentBic = requireNonEmptyText(dbtrBicNode, dbtrBicPath);
      if (issues != null) {
        validateBic(debtorAgentBic, dbtrBicPath, dbtrBicNode.line, issues);
      }

      String cdtrBicPath = txPath + "/CdtrAgt/FinInstnId/BICFI";
      XmlNode cdtrAgt = requireChild(txNode, "CdtrAgt", txPath + "/CdtrAgt");
      XmlNode cdtrFin = requireChild(cdtrAgt, "FinInstnId", txPath + "/CdtrAgt/FinInstnId");
      XmlNode cdtrBicNode = requireChild(cdtrFin, "BICFI", cdtrBicPath);
      String creditorAgentBic = requireNonEmptyText(cdtrBicNode, cdtrBicPath);
      if (issues != null) {
        validateBic(creditorAgentBic, cdtrBicPath, cdtrBicNode.line, issues);
      }

      Party creditor = extractParty(txNode, "Cdtr", "CdtrAcct", txPath, issues);

      String remittance = "";
      XmlNode rmtInf = txNode.firstChild("RmtInf");
      if (rmtInf != null) {
        XmlNode ustrd = rmtInf.firstChild("Ustrd");
        if (ustrd != null) {
          remittance = ustrd.text();
        }
      }

      transactions.add(
          new Transaction(
              endToEndId,
              amount,
              currency,
              debtor,
              creditor,
              debtorAgentBic,
              creditorAgentBic,
              remittance));
    }

    if (issues != null) {
      if (numberOfTransactions != transactions.size()) {
        issues.add(
            new ValidationIssue(
                "count_mismatch",
                hdrPath + "/NbOfTxs",
                nbOfTxsNode.line,
                "NbOfTxs is "
                    + numberOfTransactions
                    + " but found "
                    + transactions.size()
                    + " transactions"));
      }
      if (controlSum != null && controlSum.compareTo(computedSum) != 0) {
        issues.add(
            new ValidationIssue(
                "control_sum_mismatch",
                hdrPath + "/CtrlSum",
                ctrlSumNode.line,
                "CtrlSum "
                    + controlSum.toPlainString()
                    + " does not match transaction sum "
                    + computedSum.toPlainString()));
      }
    }

    return new CreditTransfer(
        msgId, creationDateTime, numberOfTransactions, controlSum, transactions);
  }

  private static Party extractParty(
      XmlNode txNode,
      String partyTag,
      String acctTag,
      String txPath,
      List<ValidationIssue> issues) {
    String partyPath = txPath + "/" + partyTag;
    XmlNode partyNode = requireChild(txNode, partyTag, partyPath);
    String name = requireText(partyNode, "Nm", partyPath + "/Nm");

    String acctPath = txPath + "/" + acctTag;
    XmlNode acctNode = requireChild(txNode, acctTag, acctPath);
    XmlNode idNode = requireChild(acctNode, "Id", acctPath + "/Id");
    String ibanPath = acctPath + "/Id/IBAN";
    XmlNode ibanNode = requireChild(idNode, "IBAN", ibanPath);
    String iban = requireNonEmptyText(ibanNode, ibanPath);
    if (issues != null) {
      validateIban(iban, ibanPath, ibanNode.line, issues);
    }
    return new Party(name, iban);
  }

  private static Statement extractStatement(XmlNode document, List<ValidationIssue> issues) {
    String basePath = "/Document/BkToCstmrStmt";
    XmlNode bkToCstmr = requireChild(document, "BkToCstmrStmt", basePath);
    String stmtPath = basePath + "/Stmt";
    XmlNode stmt = requireChild(bkToCstmr, "Stmt", stmtPath);

    String id = requireText(stmt, "Id", stmtPath + "/Id");

    XmlNode acct = requireChild(stmt, "Acct", stmtPath + "/Acct");
    XmlNode acctId = requireChild(acct, "Id", stmtPath + "/Acct/Id");
    String ibanPath = stmtPath + "/Acct/Id/IBAN";
    XmlNode ibanNode = requireChild(acctId, "IBAN", ibanPath);
    String iban = requireNonEmptyText(ibanNode, ibanPath);
    if (issues != null) {
      validateIban(iban, ibanPath, ibanNode.line, issues);
    }

    List<XmlNode> balNodes = stmt.children("Bal");
    Balance opening = null;
    Balance closing = null;
    int closingLine = stmt.line;
    String closingPath = stmtPath + "/Bal";

    for (int i = 0; i < balNodes.size(); i++) {
      XmlNode balNode = balNodes.get(i);
      String balPath = stmtPath + "/Bal[" + (i + 1) + "]";
      XmlNode tp = requireChild(balNode, "Tp", balPath + "/Tp");
      XmlNode cdOrPrtry = requireChild(tp, "CdOrPrtry", balPath + "/Tp/CdOrPrtry");
      String code = requireText(cdOrPrtry, "Cd", balPath + "/Tp/CdOrPrtry/Cd");

      String amtPath = balPath + "/Amt";
      XmlNode amtNode = requireChild(balNode, "Amt", amtPath);
      BigDecimal amount = parseDecimal(amtNode.text(), amtPath);
      String currency = amtNode.attribute("Ccy");
      if (currency == null || currency.isEmpty()) {
        throw new Iso20022Exception("missing_element", "Missing Ccy attribute at " + amtPath);
      }
      if (issues != null) {
        validateAmount(amount, amtPath, amtNode.line, issues);
        validateCurrency(currency, amtPath + "/@Ccy", amtNode.line, issues);
      }

      boolean credit = parseCreditDebit(balNode, balPath);
      Balance balance = new Balance(code, amount, currency, credit);
      if ("OPBD".equals(code)) {
        opening = balance;
      } else if ("CLBD".equals(code)) {
        closing = balance;
        closingLine = balNode.line;
        closingPath = balPath;
      }
    }

    if (opening == null) {
      throw new Iso20022Exception(
          "missing_element", "Missing OPBD opening balance in " + stmtPath + "/Bal");
    }
    if (closing == null) {
      throw new Iso20022Exception(
          "missing_element", "Missing CLBD closing balance in " + stmtPath + "/Bal");
    }

    List<XmlNode> ntryNodes = stmt.children("Ntry");
    List<Entry> entries = new ArrayList<>(ntryNodes.size());
    BigDecimal netSigned = signedAmount(opening.amount(), opening.credit());

    for (int i = 0; i < ntryNodes.size(); i++) {
      XmlNode ntryNode = ntryNodes.get(i);
      String ntryPath = stmtPath + "/Ntry[" + (i + 1) + "]";
      String amtPath = ntryPath + "/Amt";
      XmlNode amtNode = requireChild(ntryNode, "Amt", amtPath);
      BigDecimal amount = parseDecimal(amtNode.text(), amtPath);
      String currency = amtNode.attribute("Ccy");
      if (currency == null || currency.isEmpty()) {
        throw new Iso20022Exception("missing_element", "Missing Ccy attribute at " + amtPath);
      }
      if (issues != null) {
        validateAmount(amount, amtPath, amtNode.line, issues);
        validateCurrency(currency, amtPath + "/@Ccy", amtNode.line, issues);
      }

      boolean credit = parseCreditDebit(ntryNode, ntryPath);
      netSigned = netSigned.add(signedAmount(amount, credit));

      XmlNode bookgDt = requireChild(ntryNode, "BookgDt", ntryPath + "/BookgDt");
      String dtText = requireText(bookgDt, "Dt", ntryPath + "/BookgDt/Dt");
      LocalDate bookingDate = parseLocalDate(dtText, ntryPath + "/BookgDt/Dt");

      String reference = requireText(ntryNode, "NtryRef", ntryPath + "/NtryRef");
      entries.add(new Entry(amount, currency, credit, bookingDate, reference));
    }

    if (issues != null) {
      BigDecimal expectedClosing = signedAmount(closing.amount(), closing.credit());
      if (netSigned.compareTo(expectedClosing) != 0) {
        issues.add(
            new ValidationIssue(
                "balance_mismatch",
                closingPath,
                closingLine,
                "Closing balance "
                    + expectedClosing.toPlainString()
                    + " does not equal opening plus entries "
                    + netSigned.toPlainString()));
      }
    }

    return new Statement(id, iban, opening, closing, entries);
  }

  private static BigDecimal signedAmount(BigDecimal amount, boolean credit) {
    return credit ? amount : amount.negate();
  }

  private static boolean parseCreditDebit(XmlNode parent, String parentPath) {
    String path = parentPath + "/CdtDbtInd";
    String ind = requireText(parent, "CdtDbtInd", path);
    if ("CRDT".equals(ind)) {
      return true;
    }
    if ("DBIT".equals(ind)) {
      return false;
    }
    throw new Iso20022Exception("malformed", "Invalid CdtDbtInd value at " + path);
  }

  private static void validateIban(
      String iban, String location, int line, List<ValidationIssue> issues) {
    if (!isValidIban(iban)) {
      issues.add(
          new ValidationIssue(
              "invalid_iban", location, line, "Invalid IBAN check digits or format"));
    }
  }

  static boolean isValidIban(String iban) {
    if (iban == null
        || iban.length() < 15
        || iban.length() > 34
        || !IBAN_PATTERN.matcher(iban).matches()) {
      return false;
    }
    String rearranged = iban.substring(4) + iban.substring(0, 4);
    StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
    for (int i = 0; i < rearranged.length(); i++) {
      char ch = rearranged.charAt(i);
      if (ch >= '0' && ch <= '9') {
        numeric.append(ch);
      } else {
        numeric.append(ch - 'A' + 10);
      }
    }
    BigDecimal value = new BigDecimal(numeric.toString());
    return value.remainder(MOD_97).intValue() == 1;
  }

  private static void validateBic(
      String bic, String location, int line, List<ValidationIssue> issues) {
    if (!BIC_PATTERN.matcher(bic).matches()) {
      issues.add(new ValidationIssue("invalid_bic", location, line, "Invalid ISO 9362 BIC format"));
    }
  }

  private static void validateCurrency(
      String currency, String location, int line, List<ValidationIssue> issues) {
    if (!CURRENCY_PATTERN.matcher(currency).matches()) {
      issues.add(
          new ValidationIssue("invalid_currency", location, line, "Currency must match [A-Z]{3}"));
    }
  }

  private static void validateAmount(
      BigDecimal amount, String location, int line, List<ValidationIssue> issues) {
    int scale = Math.max(0, amount.scale());
    int integerDigits = Math.max(1, amount.precision() - amount.scale());
    if (amount.signum() <= 0) {
      issues.add(new ValidationIssue("invalid_amount", location, line, "Amount must be positive"));
      return;
    }
    int totalDigits = integerDigits + scale;
    if (scale > 5 || totalDigits > 18) {
      issues.add(
          new ValidationIssue(
              "invalid_amount",
              location,
              line,
              "Amount exceeds maximum 18 total digits or 5 fraction digits"));
    }
  }

  private static Instant parseInstant(String text, String path) {
    try {
      return OffsetDateTime.parse(text).toInstant();
    } catch (DateTimeParseException e) {
      try {
        return Instant.parse(text);
      } catch (DateTimeParseException ex) {
        throw new Iso20022Exception("malformed", "Invalid timestamp at " + path, ex);
      }
    }
  }

  private static LocalDate parseLocalDate(String text, String path) {
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      throw new Iso20022Exception("malformed", "Invalid date at " + path, e);
    }
  }

  private static int parsePositiveOrZeroInt(String text, String path) {
    try {
      int value = Integer.parseInt(text);
      if (value < 0) {
        throw new Iso20022Exception("malformed", "Negative integer at " + path);
      }
      return value;
    } catch (NumberFormatException e) {
      throw new Iso20022Exception("malformed", "Invalid integer at " + path, e);
    }
  }

  private static BigDecimal parseDecimal(String text, String path) {
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw new Iso20022Exception("malformed", "Invalid decimal at " + path, e);
    }
  }

  private static XmlNode requireChild(XmlNode parent, String name, String path) {
    XmlNode child = parent.firstChild(name);
    if (child == null) {
      throw new Iso20022Exception("missing_element", "Missing required element: " + path);
    }
    return child;
  }

  private static String requireText(XmlNode parent, String name, String path) {
    XmlNode child = requireChild(parent, name, path);
    return requireNonEmptyText(child, path);
  }

  private static String requireNonEmptyText(XmlNode node, String path) {
    String value = node.text();
    if (value.isEmpty()) {
      throw new Iso20022Exception("missing_element", "Empty required element: " + path);
    }
    return value;
  }

  private static XmlNode parseTree(byte[] xml) {
    Objects.requireNonNull(xml, "xml");
    if (xml.length > MAX_INPUT_BYTES) {
      throw new Iso20022Exception(
          "input_too_large",
          "XML input size " + xml.length + " exceeds limit of " + MAX_INPUT_BYTES + " bytes");
    }
    if (xml.length == 0) {
      throw new Iso20022Exception("malformed", "XML input is empty");
    }

    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);

    Deque<XmlNode> stack = new ArrayDeque<>();
    XmlNode root = null;

    try {
      XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
      while (reader.hasNext()) {
        int event = reader.next();
        switch (event) {
          case XMLStreamConstants.DTD ->
              throw new Iso20022Exception(
                  "doctype_forbidden", "DOCTYPE declarations are forbidden");
          case XMLStreamConstants.ENTITY_REFERENCE ->
              throw new Iso20022Exception("doctype_forbidden", "Entity references are forbidden");
          case XMLStreamConstants.START_ELEMENT -> {
            int depth = stack.size() + 1;
            if (depth > MAX_DEPTH) {
              throw new Iso20022Exception(
                  "too_deep", "Element nesting exceeds maximum depth of " + MAX_DEPTH);
            }
            Location loc = reader.getLocation();
            int line = loc != null ? loc.getLineNumber() : 1;
            String ns = reader.getNamespaceURI();
            XmlNode node = new XmlNode(reader.getLocalName(), ns, line);
            int attrCount = reader.getAttributeCount();
            for (int i = 0; i < attrCount; i++) {
              node.putAttribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
            if (!stack.isEmpty()) {
              stack.peek().addChild(node);
            } else {
              root = node;
            }
            stack.push(node);
          }
          case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
            if (!stack.isEmpty()) {
              stack.peek().appendText(reader.getText());
            }
          }
          case XMLStreamConstants.END_ELEMENT -> stack.pop();
          default -> {
            // Ignore comments, processing instructions, start/end document
          }
        }
      }
      reader.close();
    } catch (Iso20022Exception e) {
      throw e;
    } catch (XMLStreamException e) {
      String msg = e.getMessage();
      if (msg != null
          && (msg.contains("DOCTYPE")
              || msg.contains("DTD")
              || msg.contains("entity")
              || msg.contains("Entity"))) {
        throw new Iso20022Exception(
            "doctype_forbidden", "DOCTYPE and entity declarations are forbidden", e);
      }
      throw new Iso20022Exception("malformed", "Malformed XML document", e);
    }

    if (root == null) {
      throw new Iso20022Exception("malformed", "No root element found");
    }
    return root;
  }

  private static final class XmlNode {
    final String name;
    final String namespace;
    final int line;
    private final StringBuilder text = new StringBuilder();
    private final List<XmlNode> children = new ArrayList<>();
    private final List<String> attrKeys = new ArrayList<>(2);
    private final List<String> attrValues = new ArrayList<>(2);

    XmlNode(String name, String namespace, int line) {
      this.name = name;
      this.namespace = namespace;
      this.line = line;
    }

    void putAttribute(String key, String value) {
      attrKeys.add(key);
      attrValues.add(value);
    }

    String attribute(String key) {
      for (int i = 0; i < attrKeys.size(); i++) {
        if (attrKeys.get(i).equals(key)) {
          return attrValues.get(i);
        }
      }
      return null;
    }

    void appendText(String chunk) {
      text.append(chunk);
    }

    String text() {
      return text.toString().strip();
    }

    void addChild(XmlNode child) {
      children.add(child);
    }

    XmlNode firstChild(String childName) {
      for (XmlNode child : children) {
        if (child.name.equals(childName)) {
          return child;
        }
      }
      return null;
    }

    List<XmlNode> children(String childName) {
      List<XmlNode> result = new ArrayList<>();
      for (XmlNode child : children) {
        if (child.name.equals(childName)) {
          result.add(child);
        }
      }
      return result;
    }
  }
}
