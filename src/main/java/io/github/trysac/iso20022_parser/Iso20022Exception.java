package io.github.trysac.iso20022_parser;

import java.util.Objects;

/**
 * Exception thrown when an ISO 20022 XML document is oversized, hostile, malformed, unsupported, or
 * missing required structural elements.
 */
public final class Iso20022Exception extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * Creates a new exception with a stable machine-readable code and message.
   *
   * @param code the error code ({@code unsupported_message}, {@code doctype_forbidden}, {@code
   *     input_too_large}, {@code too_deep}, {@code malformed}, or {@code missing_element})
   * @param message human-readable description (bounded and free of sensitive payload data)
   */
  public Iso20022Exception(String code, String message) {
    super(sanitizeMessage(Objects.requireNonNull(message, "message")));
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Creates a new exception with a stable machine-readable code, message, and underlying cause.
   *
   * @param code the error code
   * @param message human-readable description
   * @param cause the underlying cause
   */
  public Iso20022Exception(String code, String message, Throwable cause) {
    super(sanitizeMessage(Objects.requireNonNull(message, "message")), cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Returns the machine-readable error code.
   *
   * @return the error code
   */
  public String code() {
    return code;
  }

  private static String sanitizeMessage(String message) {
    if (message.length() <= 200) {
      return message;
    }
    return message.substring(0, 197) + "...";
  }
}
