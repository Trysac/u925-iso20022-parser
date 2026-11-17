package io.github.trysac.iso20022_parser;

/**
 * Legacy exception retained for template compatibility.
 *
 * @see Iso20022Exception
 */
public final class BlankInputException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  /** Creates a blank input exception. */
  public BlankInputException() {
    super("input is blank");
  }
}
